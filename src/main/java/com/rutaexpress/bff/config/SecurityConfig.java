package com.rutaexpress.bff.config;

import java.util.List;
import java.util.Collection;
import com.nimbusds.jwt.JWTParser;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.util.Assert;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(AzureTokenPolicy.class)
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http, CorsConfigurationSource corsConfigurationSource, AzureTokenPolicy azure) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> azure.isAzure(jwt)
                ? List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))
                : new CognitoGrantedAuthoritiesConverter().convert(jwt));
        return http
                // Bearer-only API: no cookie authentication or server-side login session.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/shipments/track/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/operator/shipments", "/api/operator/shipments/*",
                                "/api/operator/confirmations/*").hasAnyRole("ADMIN", "OPERATOR")
                        .pathMatchers(HttpMethod.POST, "/api/operator/shipments/*/accept").hasAnyRole("ADMIN", "OPERATOR")
                        .pathMatchers("/api/operator/**").denyAll()
                        .pathMatchers("/api/**").hasRole("ADMIN")
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(converter))))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${app.security.cognito.client-id}") String clientId, AzureTokenPolicy azure) {
        Assert.hasText(issuer, "COGNITO_ISSUER_URI must not be blank");
        // Keys are fetched lazily and cached; health/startup do not require AWS connectivity.
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(issuer + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), new CognitoTokenValidator(clientId)));
        NimbusReactiveJwtDecoder operatorDecoder;
        if (azure.enabled()) {
            operatorDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(azure.keys())
                    .jwsAlgorithm(SignatureAlgorithm.RS256).build();
            operatorDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(azure.issuer()), azure));
        } else {
            operatorDecoder = null;
        }
        return token -> {
            try {
                // Unverified iss selects only a pinned decoder, never a token-supplied URL.
                String tokenIssuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
                if (issuer.equals(tokenIssuer)) return decoder.decode(token);
                if (operatorDecoder != null && azure.issuer().equals(tokenIssuer)) return operatorDecoder.decode(token);
            } catch (java.text.ParseException ignored) { }
            return Mono.error(new BadJwtException("Untrusted token issuer"));
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin}") String origin) {
        Assert.hasText(origin, "CORS_ALLOWED_ORIGIN must not be blank");
        Assert.isTrue(!origin.contains("*"), "CORS_ALLOWED_ORIGIN must be an explicit origin");
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origin));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
