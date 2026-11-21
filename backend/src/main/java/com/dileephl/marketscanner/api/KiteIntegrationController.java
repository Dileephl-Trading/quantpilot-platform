package com.dileephl.marketscanner.api;

import com.dileephl.marketscanner.integration.zerodha.KiteAuthService;
import com.dileephl.marketscanner.integration.zerodha.KiteIntegrationStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/integrations/zerodha")
public class KiteIntegrationController {
    private final KiteAuthService kiteAuthService;

    public KiteIntegrationController(KiteAuthService kiteAuthService) {
        this.kiteAuthService = kiteAuthService;
    }

    @GetMapping("/status")
    public ResponseEntity<KiteIntegrationStatus> status() {
        return ResponseEntity.ok(kiteAuthService.status());
    }

    @GetMapping("/login-url")
    public ResponseEntity<Map<String, String>> loginUrl() {
        return ResponseEntity.ok(Map.of("loginUrl", kiteAuthService.loginUrl()));
    }

    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(@RequestParam(name = "request_token", required = false) String requestToken,
                                           @RequestParam(name = "status", required = false) String status) {
        if (!"success".equalsIgnoreCase(status) || requestToken == null || requestToken.isBlank()) {
            return ResponseEntity.badRequest().body(callbackPage(false,
                    "Kite login was not completed. Close this page and try again from Market data."));
        }
        try {
            kiteAuthService.completeLogin(requestToken);
            return ResponseEntity.ok(callbackPage(true,
                    "Kite is connected for today. You can close this page and return to the dashboard."));
        } catch (IllegalStateException error) {
            return ResponseEntity.internalServerError().body(callbackPage(false,
                    "The Kite session could not be created. Check your local configuration and try again."));
        }
    }

    private String callbackPage(boolean success, String message) {
        String color = success ? "#0f9d75" : "#c9412e";
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>Kite Connect</title></head>"
                + "<body style=\"font-family:system-ui;max-width:560px;margin:80px auto;padding:24px\">"
                + "<h1 style=\"color:" + color + "\">" + (success ? "Connected" : "Connection not completed") + "</h1>"
                + "<p>" + message + "</p></body></html>";
    }
}
