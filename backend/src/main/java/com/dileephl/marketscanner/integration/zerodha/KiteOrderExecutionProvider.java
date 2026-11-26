package com.dileephl.marketscanner.integration.zerodha;

import com.dileephl.marketscanner.execution.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Kite order adapter. It is feature-gated and never called while routing is disabled. */
@Component
public class KiteOrderExecutionProvider implements OrderExecutionProvider {
    private static final ZoneOffset INDIA = ZoneOffset.ofHoursMinutes(5, 30);
    private final KiteConnectProperties properties;
    private final KiteAuthService authService;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().build();

    public KiteOrderExecutionProvider(KiteConnectProperties properties, KiteAuthService authService, ObjectMapper mapper) {
        this.properties = properties; this.authService = authService; this.mapper = mapper;
    }

    @Override public BrokerOrderResponse place(BrokerOrderRequest request) {
        requireRoutingEnabled();
        validate(request);
        JsonNode data = send("POST", "/orders/regular", form(request, false));
        String orderId = data.path("order_id").asText();
        if (orderId.isBlank()) throw new IllegalStateException("Kite did not return an order id");
        return new BrokerOrderResponse(orderId, request.symbol(), request.side(), request.quantity(), 0,
                null, BrokerOrderStatus.PENDING, "Order submitted to Kite; confirmation is required", now());
    }

    @Override public BrokerOrderResponse status(String orderId) {
        requireRoutingEnabled();
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("Order id is required");
        JsonNode data = send("GET", "/orders/" + encode(orderId), "");
        JsonNode latest = data.isArray() && !data.isEmpty() ? data.get(data.size() - 1) : data;
        return fromKite(latest, orderId);
    }

    @Override public BrokerOrderResponse cancel(String orderId) {
        requireRoutingEnabled();
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("Order id is required");
        JsonNode data = send("DELETE", "/orders/regular/" + encode(orderId), "");
        return new BrokerOrderResponse(data.path("order_id").asText(orderId), null, null, 0, 0, null,
                BrokerOrderStatus.CANCELLED, "Cancellation request submitted to Kite", now());
    }

    @Override public BrokerOrderResponse modify(String orderId, BrokerOrderRequest request) {
        requireRoutingEnabled();
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("Order id is required");
        validate(request);
        JsonNode data = send("PUT", "/orders/regular/" + encode(orderId), form(request, true));
        return new BrokerOrderResponse(data.path("order_id").asText(orderId), request.symbol(), request.side(), request.quantity(), 0,
                null, BrokerOrderStatus.PENDING, "Modification request submitted to Kite", now());
    }

    @Override public List<BrokerOrderResponse> cancelOpenOrders() {
        requireRoutingEnabled();
        JsonNode data = send("GET", "/orders", "");
        List<BrokerOrderResponse> cancelled = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode row : data) {
                String status = row.path("status").asText("").toUpperCase();
                if ("OPEN".equals(status) || "TRIGGER PENDING".equals(status)) {
                    String orderId = row.path("order_id").asText();
                    if (!orderId.isBlank()) cancelled.add(cancel(orderId));
                }
            }
        }
        return List.copyOf(cancelled);
    }

    @Override public List<BrokerPosition> positions() {
        requireRoutingEnabled();
        JsonNode data = send("GET", "/portfolio/positions", "");
        JsonNode net = data.path("net");
        if (!net.isArray()) return List.of();
        List<BrokerPosition> positions = new ArrayList<>();
        for (JsonNode row : net) {
            String symbol = row.path("tradingsymbol").asText("");
            long quantity = row.path("quantity").asLong(0);
            if (symbol.isBlank() || quantity == 0) continue;
            positions.add(new BrokerPosition(symbol, row.path("exchange").asText("NSE"),
                    row.path("product").asText("MIS"), quantity, decimalOrNull(row.path("average_price"))));
        }
        return List.copyOf(positions);
    }

    @Override public List<BrokerTrade> trades(String orderId) {
        requireRoutingEnabled();
        if (orderId == null || orderId.isBlank()) return List.of();
        JsonNode data = send("GET", "/orders/" + encode(orderId) + "/trades", "");
        if (!data.isArray()) return List.of();
        List<BrokerTrade> trades = new ArrayList<>();
        for (JsonNode row : data) {
            String tradeId = row.path("trade_id").asText("");
            long quantity = row.path("quantity").asLong(0);
            BigDecimal price = decimalOrNull(row.path("average_price"));
            if (tradeId.isBlank() || quantity <= 0 || price == null || price.signum() <= 0) continue;
            OrderSide side = "BUY".equalsIgnoreCase(row.path("transaction_type").asText())
                    ? OrderSide.BUY : OrderSide.SELL;
            trades.add(new BrokerTrade(tradeId, row.path("order_id").asText(orderId),
                    row.path("tradingsymbol").asText(""), side, quantity, price,
                    parseTimestamp(row.path("fill_timestamp").asText(null))));
        }
        return List.copyOf(trades);
    }

    @Override public List<BrokerDayPosition> dayPositions() {
        requireRoutingEnabled();
        JsonNode data = send("GET", "/portfolio/positions", "");
        JsonNode day = data.path("day");
        if (!day.isArray()) return List.of();
        List<BrokerDayPosition> positions = new ArrayList<>();
        for (JsonNode row : day) {
            String symbol = row.path("tradingsymbol").asText("");
            if (symbol.isBlank()) continue;
            positions.add(new BrokerDayPosition(symbol, row.path("exchange").asText("NSE"),
                    row.path("product").asText("MIS"), row.path("quantity").asLong(0),
                    row.path("buy_quantity").asLong(0), decimalOrNull(row.path("buy_price")),
                    row.path("sell_quantity").asLong(0), decimalOrNull(row.path("sell_price")),
                    decimalOrNull(row.path("realised")), decimalOrNull(row.path("unrealised")),
                    decimalOrNull(row.path("pnl"))));
        }
        return List.copyOf(positions);
    }

    @Override public List<BrokerOrderResponse> squareOffPositions() {
        requireRoutingEnabled();
        JsonNode data = send("GET", "/portfolio/positions", "");
        List<BrokerOrderResponse> submitted = new ArrayList<>();
        JsonNode net = data.path("net");
        if (!net.isArray()) return List.of();
        for (JsonNode row : net) {
            long netQuantity = row.path("quantity").asLong(0);
            String product = row.path("product").asText("MIS");
            String symbol = row.path("tradingsymbol").asText("");
            if (netQuantity == 0 || symbol.isBlank() || !"MIS".equalsIgnoreCase(product)) continue;
            OrderSide side = netQuantity > 0 ? OrderSide.SELL : OrderSide.BUY;
            String exchange = row.path("exchange").asText("NSE");
            String body = "exchange=" + encode(exchange) + "&tradingsymbol=" + encode(symbol)
                    + "&transaction_type=" + side.name() + "&order_type=MARKET&quantity="
                    + Math.abs(netQuantity) + "&product=" + encode(product) + "&validity=DAY"
                    + "&market_protection=-1";
            JsonNode response = send("POST", "/orders/regular", body);
            String orderId = response.path("order_id").asText("");
            if (!orderId.isBlank()) {
                submitted.add(new BrokerOrderResponse(orderId, symbol, side, Math.abs(netQuantity), 0,
                        null, BrokerOrderStatus.PENDING, "Square-off order submitted to Kite", now()));
            }
        }
        return List.copyOf(submitted);
    }

    private BrokerOrderResponse fromKite(JsonNode row, String orderId) {
        String status = row.path("status").asText("").toUpperCase();
        long filledQuantity = row.path("filled_quantity").asLong(0);
        BrokerOrderStatus mapped = switch (status) {
            case "OPEN", "TRIGGER PENDING" -> filledQuantity > 0 ? BrokerOrderStatus.PARTIALLY_FILLED : BrokerOrderStatus.OPEN;
            case "COMPLETE" -> BrokerOrderStatus.COMPLETE;
            case "CANCELLED" -> BrokerOrderStatus.CANCELLED;
            case "REJECTED" -> BrokerOrderStatus.REJECTED;
            default -> BrokerOrderStatus.UNKNOWN;
        };
        OrderSide side = "BUY".equalsIgnoreCase(row.path("transaction_type").asText()) ? OrderSide.BUY : OrderSide.SELL;
        return new BrokerOrderResponse(orderId, row.path("tradingsymbol").asText(null), side,
                row.path("quantity").asLong(0), filledQuantity,
                decimalOrNull(row.path("average_price")), mapped, row.path("status_message").asText(null), now());
    }

    private JsonNode send(String method, String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.getApiBaseUrl() + path))
                .header("X-Kite-Version", "3")
                .header("Authorization", "token " + properties.getApiKey() + ":" + authService.currentAccessToken())
                .header("Accept", "application/json").timeout(java.time.Duration.ofSeconds(10));
        if (method.equals("POST")) builder.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body));
        else if (method.equals("PUT")) builder.header("Content-Type", "application/x-www-form-urlencoded").PUT(HttpRequest.BodyPublishers.ofString(body));
        else if (method.equals("DELETE")) builder.DELETE();
        else builder.GET();
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            String message = root.path("message").asText("");
            if (response.statusCode() == 403 && message.toLowerCase().matches(".*(token|session|authentication|authori[sz]).*")) {
                authService.markInvalid();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !"success".equalsIgnoreCase(root.path("status").asText())) {
                throw new IllegalStateException("Kite order API failed with HTTP " + response.statusCode() + ": " + root.path("message").asText("unknown error"));
            }
            return root.path("data");
        } catch (IOException error) { throw new IllegalStateException("Unable to call Kite order API", error); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException("Kite order API was interrupted", error); }
    }

    private String form(BrokerOrderRequest r, boolean modification) {
        List<String> fields = new ArrayList<>();
        add(fields, "exchange", defaultValue(r.exchange(), "NSE")); add(fields, "tradingsymbol", r.symbol());
        add(fields, "transaction_type", r.side().name()); add(fields, "order_type", defaultValue(r.orderType(), "LIMIT"));
        add(fields, "quantity", Long.toString(r.quantity())); add(fields, "product", defaultValue(r.product(), "MIS"));
        add(fields, "validity", defaultValue(r.validity(), "DAY"));
        String orderType = defaultValue(r.orderType(), "LIMIT").toUpperCase();
        if (("LIMIT".equals(orderType) || "SL".equals(orderType)) && r.price() != null) {
            add(fields, "price", r.price().toPlainString());
        }
        if (r.triggerPrice() != null) add(fields, "trigger_price", r.triggerPrice().toPlainString());
        if ("MARKET".equals(orderType) || "SL-M".equals(orderType)) {
            add(fields, "market_protection", "-1");
        }
        return String.join("&", fields);
    }
    private void add(List<String> f, String key, String value) { f.add(encode(key) + "=" + encode(value)); }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String defaultValue(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private BigDecimal decimalOrNull(JsonNode node) { return node == null || node.isMissingNode() || node.isNull() ? null : node.decimalValue(); }
    private OffsetDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) return now();
        try { return OffsetDateTime.parse(value).withOffsetSameInstant(INDIA); }
        catch (DateTimeParseException ignored) { }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))) {
            try { return LocalDateTime.parse(value, formatter).atOffset(INDIA); }
            catch (DateTimeParseException ignored) { }
        }
        return now();
    }
    private OffsetDateTime now() { return OffsetDateTime.now(INDIA); }
    private void requireRoutingEnabled() { if (!properties.isOrderRoutingEnabled()) throw new IllegalStateException("Kite order routing is disabled by configuration"); }
    private void validate(BrokerOrderRequest r) {
        if (r == null || r.symbol() == null || r.symbol().isBlank() || r.side() == null || r.quantity() <= 0) throw new IllegalArgumentException("Symbol, side and positive quantity are required");
        String orderType = r.orderType() == null ? "" : r.orderType().trim().toUpperCase();
        if (!List.of("MARKET", "LIMIT", "SL", "SL-M").contains(orderType)) throw new IllegalArgumentException("Unsupported order type");
        if (("LIMIT".equals(orderType) || "SL".equals(orderType)) && (r.price() == null || r.price().signum() <= 0)) {
            throw new IllegalArgumentException("A positive price is required for this order type");
        }
        if (("SL".equals(orderType) || "SL-M".equals(orderType)) && (r.triggerPrice() == null || r.triggerPrice().signum() <= 0)) {
            throw new IllegalArgumentException("A positive trigger price is required for this order type");
        }
    }
}
