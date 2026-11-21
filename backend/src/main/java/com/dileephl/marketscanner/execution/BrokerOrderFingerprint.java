package com.dileephl.marketscanner.execution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class BrokerOrderFingerprint {
    private BrokerOrderFingerprint() { }
    static String of(BrokerOrderRequest request) {
        String value = String.join("|", String.valueOf(request.symbol()), String.valueOf(request.side()),
                String.valueOf(request.quantity()), String.valueOf(request.orderType()), String.valueOf(request.price()),
                String.valueOf(request.triggerPrice()), String.valueOf(request.product()), String.valueOf(request.exchange()),
                String.valueOf(request.validity()), String.valueOf(request.stopPrice()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
