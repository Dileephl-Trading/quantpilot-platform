package com.dileephl.marketscanner.integration.zerodha;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local-only Kite Connect configuration. Secrets are environment variables and are never
 * returned by an API endpoint or committed to source control.
 */
@ConfigurationProperties(prefix = "scanner.zerodha")
public class KiteConnectProperties {
    private boolean marketDataEnabled;
    private boolean orderRoutingEnabled;
    private String apiKey;
    private String apiSecret;
    private String redirectUrl = "http://127.0.0.1:8080/api/v1/integrations/zerodha/callback";
    private String apiBaseUrl = "https://api.kite.trade";
    private String loginBaseUrl = "https://kite.zerodha.com";
    private String websocketBaseUrl = "wss://ws.kite.trade";
    private String sessionEncryptionKey;
    private int staleTickSeconds = 20;
    private int reconnectDelaySeconds = 3;
    private int maxLiveInstruments = 50;

    public boolean isMarketDataEnabled() { return marketDataEnabled; }
    public void setMarketDataEnabled(boolean marketDataEnabled) { this.marketDataEnabled = marketDataEnabled; }
    public boolean isOrderRoutingEnabled() { return orderRoutingEnabled; }
    public void setOrderRoutingEnabled(boolean orderRoutingEnabled) { this.orderRoutingEnabled = orderRoutingEnabled; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getApiSecret() { return apiSecret; }
    public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public String getLoginBaseUrl() { return loginBaseUrl; }
    public void setLoginBaseUrl(String loginBaseUrl) { this.loginBaseUrl = loginBaseUrl; }
    public String getWebsocketBaseUrl() { return websocketBaseUrl; }
    public void setWebsocketBaseUrl(String websocketBaseUrl) { this.websocketBaseUrl = websocketBaseUrl; }
    public String getSessionEncryptionKey() { return sessionEncryptionKey; }
    public void setSessionEncryptionKey(String value) { this.sessionEncryptionKey = value; }
    public int getStaleTickSeconds() { return staleTickSeconds; }
    public void setStaleTickSeconds(int value) { staleTickSeconds = value; }
    public int getReconnectDelaySeconds() { return reconnectDelaySeconds; }
    public void setReconnectDelaySeconds(int value) { reconnectDelaySeconds = value; }
    public int getMaxLiveInstruments() { return maxLiveInstruments; }
    public void setMaxLiveInstruments(int value) { maxLiveInstruments = value > 0 ? value : 50; }

    public boolean hasApiKey() { return populated(apiKey); }
    public boolean hasApiSecret() { return populated(apiSecret); }
    private boolean populated(String value) { return value != null && !value.isBlank(); }
}
