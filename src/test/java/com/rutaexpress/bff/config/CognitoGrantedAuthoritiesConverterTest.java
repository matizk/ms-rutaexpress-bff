package com.rutaexpress.bff.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitoGrantedAuthoritiesConverterTest {
    private final CognitoGrantedAuthoritiesConverter converter = new CognitoGrantedAuthoritiesConverter();

    @Test void mapsAdminOnce() {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256")
                .claim("cognito:groups", List.of("Admin", "Admin", "Cliente")).build();
        assertEquals(List.of("ROLE_ADMIN"), converter.convert(jwt).stream()
                .map(GrantedAuthority::getAuthority).toList());
    }

    @Test void ignoresLegacyEntraRoles() {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256")
                .claim("roles", List.of("Admin")).build();
        assertTrue(converter.convert(jwt).isEmpty());
    }
}
