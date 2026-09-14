package com.rutaexpress.bff.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

/** Cognito access tokens identify the app client with client_id, not ID-token aud. */
public class CognitoTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final String clientId;

    public CognitoTokenValidator(String clientId) {
        Assert.hasText(clientId, "COGNITO_CLIENT_ID must not be blank");
        this.clientId = clientId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (!"access".equals(jwt.getClaims().get("token_use"))
                || !clientId.equals(jwt.getClaims().get("client_id"))
                || jwt.getExpiresAt() == null) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "A Cognito access token for the configured client with exp is required", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
