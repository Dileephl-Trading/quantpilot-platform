package com.dileephl.marketscanner.integration.zerodha;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class KiteAuthServiceTest {
    @Test
    void reportsSafeUnconfiguredStateWithoutLeakingCredentials() {
        KiteConnectProperties properties = new KiteConnectProperties();
        KiteIntegrationStatus status = new KiteAuthService(properties, mock(KiteSessionTokenRepository.class), new ObjectMapper()).status();

        assertEquals("NOT_CONFIGURED", status.state());
        assertTrue(!status.apiKeyConfigured());
        assertEquals("DISABLED", status.executionState());
    }

    @Test
    void constructsDocumentedLoginUrlAndSessionChecksum() {
        KiteConnectProperties properties = new KiteConnectProperties();
        properties.setApiKey("kite-api-key");
        properties.setApiSecret("kite-api-secret");
        KiteAuthService service = new KiteAuthService(properties, mock(KiteSessionTokenRepository.class), new ObjectMapper());

        assertTrue(service.loginUrl().contains("api_key=kite-api-key"));
        assertEquals("30266830215638c6b78ca2817382592dde67e9c1e68546046f97f32ca98f4d1d",
                service.sessionChecksum("request-token"));
    }
}