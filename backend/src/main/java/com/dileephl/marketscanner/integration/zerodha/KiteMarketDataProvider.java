package com.dileephl.marketscanner.integration.zerodha;

import com.dileephl.marketscanner.integration.marketdata.CandleInterval;
import com.dileephl.marketscanner.integration.marketdata.MarketDataProvider;
import com.dileephl.marketscanner.service.SignalEngine.Candle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Read-only implementation of Kite's documented market-data contracts. It deliberately contains
 * no order placement, modification, cancellation, or position-changing method.
 */
@Component
public class KiteMarketDataProvider implements MarketDataProvider {
    private static final DateTimeFormatter KITE_CANDLE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    private final KiteConnectProperties properties;
    private final KiteAuthService kiteAuthService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public KiteMarketDataProvider(KiteConnectProperties properties, KiteAuthService kiteAuthService, ObjectMapper objectMapper) {
        this(properties, kiteAuthService, objectMapper, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    KiteMarketDataProvider(KiteConnectProperties properties, KiteAuthService kiteAuthService, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.kiteAuthService = kiteAuthService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String providerName() { return "ZERODHA_KITE"; }

    @Override
    public List<Candle> historicalCandles(long instrumentToken, CandleInterval interval,
                                         OffsetDateTime from, OffsetDateTime to) {
        if (instrumentToken <= 0 || from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("Instrument token and a valid time window are required");
        }
        requireEnabledCredentials();
        URI uri = UriComponentsBuilder.fromUriString(properties.getApiBaseUrl())
                .path("/instruments/historical/{token}/{interval}")
                .queryParam("from", from.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .queryParam("to", to.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .buildAndExpand(instrumentToken, interval.providerValue())
                .encode()
                .toUri();
        JsonNode root = executeJson(uri);
        JsonNode candles = root.path("data").path("candles");
        if (!candles.isArray()) throw new IllegalStateException("Kite historical response did not contain data.candles");
        List<Candle> result = new ArrayList<>();
        for (JsonNode row : candles) {
            if (!row.isArray() || row.size() < 6) throw new IllegalStateException("Invalid Kite candle row");
            result.add(new Candle(OffsetDateTime.parse(row.get(0).asText(), KITE_CANDLE_TIMESTAMP),
                    decimal(row, 1), decimal(row, 2), decimal(row, 3), decimal(row, 4), row.get(5).asLong()));
        }
        OffsetDateTime now = OffsetDateTime.now(from.getOffset());
        return result.stream()
                .filter(candle -> !candle.timestamp().plus(interval.candleDuration()).isAfter(now))
                .toList();
    }

    /** Fetches Kite's instrument CSV exactly as published. */
    @Override
    public String instrumentMasterCsv() {
        requireEnabledCredentials();
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getApiBaseUrl() + "/instruments"))
                .header("X-Kite-Version", "3")
                .header("Authorization", "token " + properties.getApiKey() + ":" + kiteAuthService.currentAccessToken())
                .header("Accept", "text/csv")
                .timeout(java.time.Duration.ofSeconds(20))
                .GET().build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            ensureSuccess(response.statusCode());
            return decodeCsv(response);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to call Kite instruments API", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kite instruments call was interrupted", error);
        }
    }

    private JsonNode executeJson(URI uri) {
        HttpRequest request = baseRequest(uri).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response.statusCode());
            return objectMapper.readTree(response.body());
        } catch (IOException error) {
            throw new IllegalStateException("Unable to call Kite historical API", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kite historical call was interrupted", error);
        }
    }

    private HttpRequest.Builder baseRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .header("X-Kite-Version", "3")
                .header("Authorization", "token " + properties.getApiKey() + ":" + kiteAuthService.currentAccessToken())
                .header("Accept", "application/json")
                .timeout(java.time.Duration.ofSeconds(10));
    }

    private BigDecimal decimal(JsonNode row, int index) { return row.get(index).decimalValue(); }

    private void requireEnabledCredentials() {
        if (!properties.isMarketDataEnabled()) throw new IllegalStateException("Kite market data is disabled by configuration");
        if (!properties.hasApiKey() || !kiteAuthService.hasCurrentAccessToken()) {
            throw new IllegalStateException("Kite API key and current access token are required");
        }
    }

    private String decodeCsv(HttpResponse<byte[]> response) {
        boolean gzip = response.headers().firstValue("Content-Encoding")
                .map(value -> value.toLowerCase().contains("gzip")).orElse(false);
        try (InputStream input = gzip ? new GZIPInputStream(new ByteArrayInputStream(response.body()))
                : new ByteArrayInputStream(response.body())) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Unable to decode Kite instrument-master response", error);
        }
    }

    private void ensureSuccess(int statusCode) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Kite market-data request failed with HTTP " + statusCode);
        }
    }
}
