package com.rutaexpress.bff.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class EntraGrantedAuthoritiesConverterTest {

    @Test
    void mapsEntraApplicationRolesToSpringAuthorities() {
        Jwt token = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("roles", List.of("Admin"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        var authorities = new EntraGrantedAuthoritiesConverter().convert(token);

        assertEquals(List.of("ROLE_Admin"),
                authorities.stream().map(authority -> authority.getAuthority()).toList());
    }
}
