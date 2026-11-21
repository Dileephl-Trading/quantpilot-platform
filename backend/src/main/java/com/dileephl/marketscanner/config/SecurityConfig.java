package com.dileephl.marketscanner.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final QuantPilotSecurityProperties properties;
    private final ApiRateLimitFilter rateLimitFilter;

    public SecurityConfig(QuantPilotSecurityProperties properties, ApiRateLimitFilter rateLimitFilter) {
        this.properties = properties;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'")))
                .addFilterBefore(rateLimitFilter, BasicAuthenticationFilter.class);

        if (!properties.enabled()) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/integrations/zerodha/callback").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    InMemoryUserDetailsManager userDetailsService(PasswordEncoder passwordEncoder) {
        if (properties.enabled() && (properties.username().isBlank()
                || (properties.password().isBlank() && properties.passwordHash().isBlank()))) {
            throw new IllegalStateException(
                    "QUANTPILOT_SECURITY_ENABLED=true requires QUANTPILOT_SECURITY_USERNAME and " +
                    "QUANTPILOT_SECURITY_PASSWORD_HASH (or the development-only password)");
        }

        String username = properties.username().isBlank() ? "disabled-user" : properties.username();
        String credential;
        if (!properties.passwordHash().isBlank()) {
            credential = properties.passwordHash().startsWith("{")
                    ? properties.passwordHash()
                    : "{bcrypt}" + properties.passwordHash();
        } else {
            credential = "{noop}" + (properties.password().isBlank() ? "disabled-password" : properties.password());
        }

        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(credential)
                        .roles("USER")
                        .build());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(properties.allowedOrigin()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Retry-After"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
