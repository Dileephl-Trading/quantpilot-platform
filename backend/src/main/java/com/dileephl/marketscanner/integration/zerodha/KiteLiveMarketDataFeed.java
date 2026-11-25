package com.dileephl.marketscanner.integration.zerodha;

import com.dileephl.marketscanner.integration.marketdata.LiveFeedStatus;
import com.dileephl.marketscanner.integration.marketdata.LiveMarketDataFeed;
import com.dileephl.marketscanner.integration.marketdata.LiveMarketTick;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Read-only Kite WebSocket client with reconnect and stale-feed protection. */
@Component
public class KiteLiveMarketDataFeed implements LiveMarketDataFeed {
    private static final ZoneOffset INDIA_OFFSET = ZoneOffset.ofHoursMinutes(5, 30);
    private final KiteConnectProperties properties;
    private final KiteAuthService authService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final AtomicLong connectionGeneration = new AtomicLong();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "kite-feed-health"); thread.setDaemon(true); return thread;
    });
    private final Map<Long, BigDecimal> latestPrices = new ConcurrentHashMap<>();
    private volatile Set<Long> activeTokens = Set.of();
    private volatile Consumer<LiveMarketTick> activeConsumer;
    private volatile ScheduledFuture<?> reconnectFuture;
    private volatile String state = "STOPPED";
    private volatile int subscribedInstruments;
    private volatile OffsetDateTime connectedAt;
    private volatile OffsetDateTime lastTickAt;
    private volatile OffsetDateTime lastMessageAt;
    private volatile String lastError;

    public KiteLiveMarketDataFeed(KiteConnectProperties properties, KiteAuthService authService, ObjectMapper objectMapper) {
        this.properties = properties; this.authService = authService; this.objectMapper = objectMapper;
        scheduler.scheduleWithFixedDelay(this::checkStale, 5, 5, TimeUnit.SECONDS);
    }

    @Override public String providerName() { return "ZERODHA_KITE"; }

    @Override
    public synchronized void start(Set<Long> instrumentTokens, Consumer<LiveMarketTick> tickConsumer) {
        if (!properties.isMarketDataEnabled()) throw new IllegalStateException("Kite market data is disabled by configuration");
        if (instrumentTokens == null || instrumentTokens.isEmpty()) throw new IllegalArgumentException("Add at least one enabled symbol to the live watchlist");
        int capacity = properties.getMaxLiveInstruments();
        if (instrumentTokens.size() > capacity) {
            throw new IllegalArgumentException("The live watchlist is limited to " + capacity + " symbols");
        }
        stop(); activeTokens = Set.copyOf(instrumentTokens); activeConsumer = tickConsumer;
        subscribedInstruments = activeTokens.size(); latestPrices.clear(); lastError = null;
        openSocket(connectionGeneration.incrementAndGet());
    }

    @Override
    public synchronized void stop() {
        connectionGeneration.incrementAndGet();
        if (reconnectFuture != null) reconnectFuture.cancel(false);
        reconnectFuture = null; activeTokens = Set.of(); activeConsumer = null;
        WebSocket existing = socket.getAndSet(null); if (existing != null) existing.sendClose(WebSocket.NORMAL_CLOSURE, "local stop");
        state = "STOPPED"; subscribedInstruments = 0; connectedAt = null; lastTickAt = null; lastMessageAt = null; latestPrices.clear();
    }

    @Override
    public LiveFeedStatus status() {
        return new LiveFeedStatus(providerName(), state, subscribedInstruments, properties.getMaxLiveInstruments(), connectedAt, lastTickAt,
                Collections.unmodifiableMap(new HashMap<>(latestPrices)), lastError);
    }

    private synchronized void openSocket(long generation) {
        if (generation != connectionGeneration.get() || activeTokens.isEmpty()) return;
        state = "CONNECTING";
        URI endpoint = UriComponentsBuilder.fromUriString(properties.getWebsocketBaseUrl())
                .queryParam("api_key", properties.getApiKey()).queryParam("access_token", authService.currentAccessToken())
                .build().encode().toUri();
        httpClient.newWebSocketBuilder().buildAsync(endpoint, new Listener(generation, activeTokens, activeConsumer))
                .whenComplete((connected, error) -> { if (error != null && generation == connectionGeneration.get()) scheduleReconnect(generation, "Unable to connect to Kite live feed"); });
    }

    private synchronized void scheduleReconnect(long generation, String error) {
        if (generation != connectionGeneration.get() || activeTokens.isEmpty()) return;
        lastError = error; state = "RECONNECTING";
        WebSocket existing = socket.getAndSet(null);
        if (existing != null) existing.abort();
        if (reconnectFuture != null && !reconnectFuture.isDone()) return;
        reconnectFuture = scheduler.schedule(() -> openSocket(generation), Math.max(1, properties.getReconnectDelaySeconds()), TimeUnit.SECONDS);
    }

    private void checkStale() {
        if (!state.equals("CONNECTED")) return;
        OffsetDateTime reference = lastMessageAt == null ? connectedAt : lastMessageAt;
        if (reference != null && reference.plusSeconds(Math.max(5, properties.getStaleTickSeconds())).isBefore(OffsetDateTime.now(INDIA_OFFSET))) {
            scheduleReconnect(connectionGeneration.get(), "No tick received within the configured stale threshold"); state = "STALE";
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final long generation; private final Set<Long> tokens; private final Consumer<LiveMarketTick> consumer;
        private final ByteArrayOutputStream fragments = new ByteArrayOutputStream();
        private Listener(long generation, Set<Long> tokens, Consumer<LiveMarketTick> consumer) { this.generation = generation; this.tokens = tokens; this.consumer = consumer; }

        @Override public void onOpen(WebSocket webSocket) {
            if (generation != connectionGeneration.get()) { webSocket.abort(); return; }
            socket.set(webSocket); state = "CONNECTED"; connectedAt = OffsetDateTime.now(INDIA_OFFSET); lastMessageAt = connectedAt;
            try {
                List<Long> selected = tokens.stream().sorted().toList();
                webSocket.sendText(objectMapper.writeValueAsString(Map.of("a", "subscribe", "v", selected)), true);
                webSocket.sendText(objectMapper.writeValueAsString(Map.of("a", "mode", "v", List.of("full", selected))), true);
            } catch (JsonProcessingException error) { scheduleReconnect(generation, "Unable to prepare Kite subscription"); webSocket.abort(); }
            webSocket.request(1);
        }

        @Override public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            if (generation != connectionGeneration.get()) return CompletableFuture.completedFuture(null);
            lastMessageAt = OffsetDateTime.now(INDIA_OFFSET); byte[] bytes = new byte[data.remaining()]; data.get(bytes); fragments.writeBytes(bytes);
            if (last) { parseMessage(fragments.toByteArray(), consumer); fragments.reset(); } webSocket.request(1); return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence message, boolean last) {
            if (generation == connectionGeneration.get() && message.toString().contains("\"type\":\"error\"")) {
                String text = message.toString().toLowerCase();
                if (text.contains("token") || text.contains("expired") || text.contains("invalid")) {
                    authService.markInvalid();
                    KiteLiveMarketDataFeed.this.stop();
                    lastError = "Kite session expired or is invalid; connect Kite again for today's session";
                    webSocket.abort();
                } else {
                    scheduleReconnect(generation, "Kite live feed returned an error");
                }
            }
            webSocket.request(1); return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            socket.compareAndSet(webSocket, null); if (generation == connectionGeneration.get() && !activeTokens.isEmpty()) scheduleReconnect(generation, "Kite live feed closed: " + reason);
            return CompletableFuture.completedFuture(null);
        }

        @Override public void onError(WebSocket webSocket, Throwable error) { if (generation == connectionGeneration.get()) scheduleReconnect(generation, "Kite live feed connection failed"); }
    }

    private void parseMessage(byte[] message, Consumer<LiveMarketTick> consumer) {
        if (message.length <= 1) return; ByteBuffer frame = ByteBuffer.wrap(message).order(ByteOrder.BIG_ENDIAN); if (frame.remaining() < 2) return;
        int packetCount = Short.toUnsignedInt(frame.getShort());
        for (int index = 0; index < packetCount && frame.remaining() >= 2; index++) {
            int packetLength = Short.toUnsignedInt(frame.getShort()); if (packetLength > frame.remaining()) { lastError = "Malformed Kite live packet"; return; }
            ByteBuffer packet = frame.slice().order(ByteOrder.BIG_ENDIAN); packet.limit(packetLength); frame.position(frame.position() + packetLength); if (packetLength < 12) continue;
            long token = Integer.toUnsignedLong(packet.getInt(0)); BigDecimal price = BigDecimal.valueOf(packet.getInt(4), 2); long quantity = Integer.toUnsignedLong(packet.getInt(8));
            if (price.signum() <= 0 || quantity <= 0) continue;
            OffsetDateTime timestamp = packetLength >= 64 && packet.getInt(60) > 0 ? java.time.Instant.ofEpochSecond(Integer.toUnsignedLong(packet.getInt(60))).atOffset(INDIA_OFFSET) : OffsetDateTime.now(INDIA_OFFSET);
            lastTickAt = timestamp; latestPrices.put(token, price); consumer.accept(new LiveMarketTick(token, timestamp, price, quantity));
        }
    }
}
