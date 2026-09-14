package com.rutaexpress.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)

            .authorizeExchange(exchanges -> exchanges
            .pathMatchers("/actuator/health").permitAll()
            .pathMatchers("/api/catalog/**").hasAuthority("Admin")
            .pathMatchers("/api/shipments/**").hasAuthority("Admin")
            .anyExchange().authenticated()
        )

            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }


    private Converter<Jwt, Mono<AbstractAuthenticationToken>>
            jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                new CognitoGrantedAuthoritiesConverter()
        );

        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }


    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
            String issuer,

            @Value("${aws.cognito.client-id}")
            String clientId) {

        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder
                    .withJwkSetUri(issuer + "/.well-known/jwks.json")
                    .build();

        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(issuer);

        OAuth2TokenValidator<Jwt> clientIdValidator =
                new JwtClaimValidator<>(
                    "client_id",
                    clientId::equals
                );

        decoder.setJwtValidator(
            new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                clientIdValidator
            )
        );

        return decoder;
    }
}
