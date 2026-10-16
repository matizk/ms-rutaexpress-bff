package com.rutaexpress.bff.config;

import java.util.Arrays;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

/** Trusted single-tenant delegated API tokens only. Never grants Cognito Admin. */
@ConfigurationProperties(prefix = "app.security.azure")
public record AzureTokenPolicy(boolean enabled, String tenantId, String audience,
                               String clientId, String scope, String jwkSetUri)
        implements OAuth2TokenValidator<Jwt> {
    public AzureTokenPolicy {
        if (enabled) {
            for (String value : new String[]{tenantId, audience, clientId}) {
                Assert.isTrue(value != null && value.matches("[0-9a-fA-F-]{36}"),
                        "Azure tenant, API audience and SPA client must be UUIDs");
                UUID.fromString(value);
            }
            Assert.isTrue(scope != null && scope.matches("[A-Za-z0-9_.-]+"),
                    "AZURE_OPERATOR_SCOPE must be a delegated scope name");
        }
    }
    public String issuer() { return "https://login.microsoftonline.com/" + tenantId + "/v2.0"; }
    public String keys() {
        return jwkSetUri == null || jwkSetUri.isBlank()
                ? "https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys" : jwkSetUri;
    }
    public boolean isAzure(Jwt jwt) { return enabled && issuer().equals(jwt.getClaimAsString("iss")); }
    @Override public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object scopes = jwt.getClaims().get("scp");
        Object oid = jwt.getClaims().get("oid");
        boolean valid = isAzure(jwt) && tenantId.equals(jwt.getClaims().get("tid"))
                && "2.0".equals(jwt.getClaims().get("ver"))
                && jwt.getAudience().equals(java.util.List.of(audience))
                && clientId.equals(jwt.getClaims().get("azp"))
                && oid instanceof String id && id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
                && scopes instanceof String text && Arrays.asList(text.split(" ")).contains(scope)
                && jwt.getExpiresAt() != null;
        return valid ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                        "A delegated access token for the configured Azure API, tenant and SPA is required", null));
    }
}
