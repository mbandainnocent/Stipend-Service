package com.stipend.service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Stateless JWT security. Tokens are issued by the attendance service's auth module
 * (jjwt, HMAC, signed with the UTF-8 bytes of the shared {@code app.jwt.secret}).
 * Their {@code roles} claim already carries Spring authorities such as {@code ROLE_ADMIN}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final int MIN_SECRET_BYTES = 32;

    private static final String[] PUBLIC_URLS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health",
            "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${app.security.finance-roles:ADMIN,FINANCE}") String[] financeRoles
    ) throws Exception {
        String[] roles = Arrays.stream(financeRoles)
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .toArray(String[]::new);

        http
                .csrf(AbstractHttpConfigurer::disable)
                // Uses the corsConfigurationSource bean; preflights are answered before authorization.
                .cors(cors -> { })
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole(roles)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/stipends/*/approve").hasAnyRole(roles)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/stipends/*/mark-paid").hasAnyRole(roles)
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/v1/cohorts/*/stipends/approve",
                                "/api/v1/cohorts/*/stipends/mark-paid").hasAnyRole(roles)
                        .requestMatchers("/api/dev/**").hasAnyRole(roles)
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Browser origins allowed to call the API ({@code APP_CORS_ALLOWED_ORIGINS}, comma-separated).
     * With none configured no CORS mapping is registered, so cross-origin browser calls are refused.
     * Tokens travel in the Authorization header, so credentials (cookies) are not allowed.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins:}") String[] allowedOrigins
    ) {
        List<String> origins = Arrays.stream(allowedOrigins)
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!origins.isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(origins);
            config.setAllowedMethods(List.of("GET", "PATCH", "POST", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            config.setAllowCredentials(false);
            config.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", config);
        }
        return source;
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
        byte[] keyBytes = requireStrongSecret(secret).getBytes(StandardCharsets.UTF_8);
        MacAlgorithm algorithm = macAlgorithmFor(keyBytes.length);
        SecretKey key = new SecretKeySpec(keyBytes, algorithm.getName().replace("HS", "HmacSHA"));
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(algorithm).build();
    }

    /**
     * Mirrors jjwt's {@code signWith(key)} choice in the attendance service: the
     * strongest HMAC algorithm the key length allows.
     */
    static MacAlgorithm macAlgorithmFor(int keyBytes) {
        if (keyBytes >= 64) {
            return MacAlgorithm.HS512;
        }
        if (keyBytes >= 48) {
            return MacAlgorithm.HS384;
        }
        return MacAlgorithm.HS256;
    }

    private static String requireStrongSecret(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        return secret;
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var granted = authorities.convert(jwt);
            // Fall back to the single "role" claim (e.g. "ADMIN") if "roles" is absent.
            if ((granted == null || granted.isEmpty()) && jwt.hasClaim("role")) {
                return List.of(new org.springframework.security.core.authority
                        .SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role")));
            }
            return granted;
        });
        return converter;
    }
}
