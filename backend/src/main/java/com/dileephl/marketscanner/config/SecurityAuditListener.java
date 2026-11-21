package com.dileephl.marketscanner.config;

import com.dileephl.marketscanner.audit.AuditTrailService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditListener {
    private final AuditTrailService auditTrail;

    public SecurityAuditListener(AuditTrailService auditTrail) {
        this.auditTrail = auditTrail;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        auditTrail.record(
                "AUTHENTICATION_SUCCESS",
                "SECURITY",
                safePrincipal(event.getAuthentication().getName()),
                "Authenticated API user");
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        auditTrail.record(
                "AUTHENTICATION_FAILURE",
                "SECURITY",
                safePrincipal(event.getAuthentication().getName()),
                "Rejected API authentication: " + event.getException().getClass().getSimpleName());
    }

    private String safePrincipal(String principal) {
        if (principal == null || principal.isBlank()) {
            return "unknown";
        }
        return principal.length() <= 64 ? principal : principal.substring(0, 64);
    }
}
