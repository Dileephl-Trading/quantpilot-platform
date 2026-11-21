package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.integration.zerodha.KiteAuthService;
import com.dileephl.marketscanner.integration.zerodha.KiteIntegrationStatus;
import com.dileephl.marketscanner.integration.zerodha.KiteConnectProperties;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/integrations/zerodha")
public class KiteIntegrationController {
    private final KiteAuthService kiteAuthService;
    private final KiteConnectProperties properties;

    public KiteIntegrationController(KiteAuthService kiteAuthService, KiteConnectProperties properties) {
        this.kiteAuthService = kiteAuthService;
        this.properties = properties;
    }

    @GetMapping("/status")
    public ResponseEntity<KiteIntegrationStatus> status() {
        return ResponseEntity.ok(kiteAuthService.status());
    }

    @GetMapping("/login-url")
    public ResponseEntity<Map<String, String>> loginUrl() {
        return ResponseEntity.ok(Map.of("loginUrl", kiteAuthService.loginUrl()));
    }

    /**
     * Kite redirects here after login. The token exchange happens server-side and the browser is
     * redirected back into the existing SPA integration screen, without exposing any token.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(name = "request_token", required = false) String requestToken,
                                         @RequestParam(name = "status", required = false) String status) {
        boolean success = "success".equalsIgnoreCase(status) && requestToken != null && !requestToken.isBlank();
        String result = "error";
        if (success) {
            try {
                kiteAuthService.completeLogin(requestToken);
                result = "connected";
            } catch (IllegalStateException ignored) {
                // Do not expose provider details or token material in the redirect URL.
            }
        }
        String redirect = spaRedirect(result);
        return ResponseEntity.status(303).header(HttpHeaders.LOCATION, redirect).build();
    }

    private String spaRedirect(String result) {
        URI callback = URI.create(properties.getRedirectUrl());
        String origin = callback.getScheme() + "://" + callback.getRawAuthority();
        return origin + "/#integration?kite=" + result;
    }
}
