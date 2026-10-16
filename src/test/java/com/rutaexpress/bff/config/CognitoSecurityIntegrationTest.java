package com.rutaexpress.bff.config;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/** Runs the actual gateway, decoder, signature verification, validators and filters. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CognitoSecurityIntegrationTest {
    private static final String CLIENT_ID = "test-app-client";
    private static final String ORIGIN = "http://localhost:4200";
    private static final String TENANT = "11111111-1111-4111-8111-111111111111";
    private static final String API = "22222222-2222-4222-8222-222222222222";
    private static final String SPA = "33333333-3333-4333-8333-333333333333";
    private static final String AZURE_ISSUER = "https://login.microsoftonline.com/" + TENANT + "/v2.0";
    private static final RSAKey KEY = generateKey();
    private static final RSAKey UNTRUSTED_KEY = generateKey();
    private static final DisposableServer STUB = HttpServer.create().host("127.0.0.1").port(0)
            .route(routes -> routes
                    .get("/.well-known/jwks.json", (req, res) -> res
                            .header("Content-Type", "application/json")
                            .sendString(Mono.just(new JWKSet(KEY.toPublicJWK()).toString())))
                    .get("/api/shipments", (req, res) -> res.sendString(Mono.just("shipments")))
                    .get("/api/shipments/track/RX-0001", (req, res) -> res.sendString(Mono.just("tracking")))
                    .get("/api/catalog/services", (req, res) -> res.sendString(Mono.just("catalog")))
                    .get("/api/report/kpis", (req, res) -> res.sendString(Mono.just("report")))
                    .get("/api/messaging/summary", (req, res) -> res.sendString(Mono.just(req.requestHeaders().get(HttpHeaders.AUTHORIZATION))))
                    .get("/api/rabbitmq/topology", (req, res) -> res.sendString(Mono.just("rabbit-admin")))
                    .get("/api/operator/shipments", (req, res) -> res.sendString(Mono.just(req.requestHeaders().get(HttpHeaders.AUTHORIZATION))))
                    .get("/api/operator/confirmations/RX-0001", (req, res) -> res.sendString(Mono.just("accepted-ticket")))
                    .post("/api/operator/shipments/1/accept", (req, res) -> res.sendString(Mono.just("accepted")))
                    .post("/api/shipments", (req, res) -> res.status(201).sendString(req.receive().asString())))
            .bindNow();
    private static final String ISSUER = "http://127.0.0.1:" + STUB.port();

    @Autowired WebTestClient web;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("COGNITO_ISSUER_URI", () -> ISSUER);
        registry.add("COGNITO_CLIENT_ID", () -> CLIENT_ID);
        registry.add("CATALOGO_URL", () -> ISSUER);
        registry.add("SHIPMENTS_URL", () -> ISSUER);
        registry.add("REPORT_URL", () -> ISSUER);
        registry.add("MESSAGING_URL", () -> ISSUER);
        registry.add("CORS_ALLOWED_ORIGIN", () -> ORIGIN);
        registry.add("app.security.azure.enabled", () -> "true");
        registry.add("app.security.azure.tenant-id", () -> TENANT);
        registry.add("app.security.azure.audience", () -> API);
        registry.add("app.security.azure.client-id", () -> SPA);
        registry.add("app.security.azure.scope", () -> "operator.access");
        registry.add("app.security.azure.jwk-set-uri", () -> ISSUER + "/.well-known/jwks.json");
    }

    @AfterAll static void stopStub() { STUB.disposeNow(); }

    @Test void healthIsPublic() {
        web.get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test void missingBearerIsUnauthorized() {
        web.get().uri("/api/shipments").exchange().expectStatus().isUnauthorized();
    }

    @Test void publicTrackingDoesNotRequireBearer() {
        web.get().uri("/api/shipments/track/RX-0001").exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo("tracking");
    }

    @Test void malformedBearerIsUnauthorized() {
        request("not-a-jwt").expectStatus().isUnauthorized();
    }

    @Test void missingGroupsIsForbidden() throws Exception {
        request(token(b -> b.claim("cognito:groups", null))).expectStatus().isForbidden();
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "ADMIN", "Cliente", "ROLE_ADMIN"})
    void otherGroupsCannotGrantAdmin(String group) throws Exception {
        request(token(b -> b.claim("cognito:groups", List.of(group)))).expectStatus().isForbidden();
    }

    @Test void malformedGroupClaimIsForbidden() throws Exception {
        request(token(b -> b.claim("cognito:groups", "Admin"))).expectStatus().isForbidden();
    }

    @Test void validAccessTokenWithoutAudienceReachesShipments() throws Exception {
        request(token(b -> {})).expectStatus().isOk().expectBody(String.class).isEqualTo("shipments");
    }

    @Test void validAccessTokenReachesCatalogWithUnchangedPath() throws Exception {
        web.get().uri("/api/catalog/services").headers(h -> h.setBearerAuth(token(b -> {})))
                .exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("catalog");
    }

    @Test void validAccessTokenReachesReportWithUnchangedPath() throws Exception {
        web.get().uri("/api/report/kpis").headers(h -> h.setBearerAuth(token(b -> {})))
                .exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("report");
    }

    @Test void messagingRouteForwardsTheValidatedBearerTokenUnchanged() {
        String signedToken = token(b -> {});
        web.get().uri("/api/messaging/summary").headers(h -> h.setBearerAuth(signedToken))
                .exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("Bearer " + signedToken);
    }

    @Test void rabbitAdminRouteKeepsItsPathAndRequiresAdmin() {
        web.get().uri("/api/rabbitmq/topology").headers(h -> h.setBearerAuth(token(b -> {})))
                .exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("rabbit-admin");
        web.get().uri("/api/rabbitmq/topology").headers(h -> h.setBearerAuth(token(b -> b.claim("cognito:groups", List.of("Cliente")))))
                .exchange().expectStatus().isForbidden();
    }

    @Test void authorizedPostForwardsBodyWithoutCsrfSession() throws Exception {
        web.post().uri("/api/shipments").headers(h -> h.setBearerAuth(token(b -> {})))
                .header("Content-Type", "application/json").bodyValue("{\"test\":true}")
                .exchange().expectStatus().isCreated().expectBody(String.class).isEqualTo("{\"test\":true}");
    }

    @Test void wrongIssuerIsUnauthorized() throws Exception {
        request(token(b -> b.issuer("https://untrusted.example"))).expectStatus().isUnauthorized();
    }

    @Test void wrongClientIsUnauthorizedEvenWithMatchingAudience() throws Exception {
        request(token(b -> b.claim("client_id", "other-client").audience(CLIENT_ID)))
                .expectStatus().isUnauthorized();
    }

    @Test void missingClientIsUnauthorized() throws Exception {
        request(token(b -> b.claim("client_id", null))).expectStatus().isUnauthorized();
    }

    @Test void idTokenIsUnauthorized() throws Exception {
        request(token(b -> b.claim("token_use", "id").audience(CLIENT_ID))).expectStatus().isUnauthorized();
    }

    @Test void missingTokenUseIsUnauthorized() throws Exception {
        request(token(b -> b.claim("token_use", null))).expectStatus().isUnauthorized();
    }

    @Test void expiredTokenIsUnauthorized() throws Exception {
        request(token(b -> b.issueTime(Date.from(Instant.now().minusSeconds(900)))
                .expirationTime(Date.from(Instant.now().minusSeconds(120)))))
                .expectStatus().isUnauthorized();
    }

    @Test void futureNotBeforeIsUnauthorized() throws Exception {
        request(token(b -> b.notBeforeTime(Date.from(Instant.now().plusSeconds(180)))))
                .expectStatus().isUnauthorized();
    }

    @Test void missingExpirationIsUnauthorized() throws Exception {
        request(token(b -> b.expirationTime(null))).expectStatus().isUnauthorized();
    }

    @Test void forgedSignatureIsUnauthorized() throws Exception {
        request(sign(claims().build(), UNTRUSTED_KEY)).expectStatus().isUnauthorized();
    }

    @Test void allowedPreflightDoesNotNeedToken() {
        web.options().uri("/api/shipments").header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGIN);
    }

    @Test void untrustedOriginPreflightIsForbidden() {
        web.options().uri("/api/shipments").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange().expectStatus().isForbidden();
    }

    @Test void unauthorizedResponseHasCorsHeader() {
        web.get().uri("/api/shipments").header("Origin", ORIGIN).exchange()
                .expectStatus().isUnauthorized().expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGIN);
    }

    @Test void protectedRequestsDoNotCreateSessionCookies() throws Exception {
        request(token(b -> {})).expectStatus().isOk().expectHeader().doesNotExist(HttpHeaders.SET_COOKIE);
        web.get().uri("/api/shipments").exchange().expectStatus().isUnauthorized();
    }

    @Test void otherActuatorEndpointsAreForbiddenEvenForAdmin() throws Exception {
        web.get().uri("/actuator/info").headers(h -> h.setBearerAuth(token(b -> {})))
                .exchange().expectStatus().isForbidden();
    }

    private WebTestClient.ResponseSpec request(String token) {
        return web.get().uri("/api/shipments").headers(h -> h.setBearerAuth(token)).exchange();
    }

    @Test void azureOperatorCanReadAcceptAndRetrieveConfirmationButCannotAdminister() {
        String access = azureToken(b -> {});
        web.get().uri("/api/operator/shipments").headers(h -> h.setBearerAuth(access))
                .exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("Bearer " + access);
        web.post().uri("/api/operator/shipments/1/accept").headers(h -> h.setBearerAuth(access))
                .exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("accepted");
        web.get().uri("/api/operator/confirmations/RX-0001").headers(h -> h.setBearerAuth(access))
                .exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("accepted-ticket");
        for (String route : List.of("/api/catalog/services", "/api/shipments", "/api/rabbitmq/topology",
                "/api/messaging/summary", "/api/report/kpis")) {
            web.get().uri(route).headers(h -> h.setBearerAuth(access)).exchange().expectStatus().isForbidden();
        }
    }
    @Test void azureCannotEscalateUsingCognitoGroupOrRoles() {
        web.get().uri("/api/rabbitmq/topology").headers(h -> h.setBearerAuth(azureToken(b ->
                        b.claim("cognito:groups", List.of("Admin")).claim("roles", List.of("Admin")))))
                .exchange().expectStatus().isForbidden();
    }
    @Test void cognitoAdminCanReviewOperatorFlowButUnassignedCognitoUserCannot() {
        web.get().uri("/api/operator/shipments").headers(h -> h.setBearerAuth(token(b -> {})))
                .exchange().expectStatus().isOk();
        web.get().uri("/api/operator/shipments").headers(h -> h.setBearerAuth(token(b ->
                        b.claim("cognito:groups", List.of("Cliente")))))
                .exchange().expectStatus().isForbidden();
    }
    @Test void rejectsWrongAzureTenantAudienceClientScopeAndIdToken() {
        for (Consumer<JWTClaimsSet.Builder> edit : List.<Consumer<JWTClaimsSet.Builder>>of(
                b -> b.claim("tid", "other-tenant"), b -> b.audience(SPA),
                b -> b.claim("azp", "other-client"), b -> b.claim("scp", "User.Read"),
                b -> b.claim("scp", null), b -> b.claim("oid", null), b -> b.claim("ver", "1.0"),
                b -> b.issuer("https://untrusted.example"),
                b -> b.expirationTime(Date.from(Instant.now().minusSeconds(900))))) {
            web.get().uri("/api/operator/shipments").headers(h -> h.setBearerAuth(azureToken(edit)))
                    .exchange().expectStatus().isUnauthorized();
        }
    }
    @Test void azureDoesNotAllowUnsupportedMethodsOrUnsignedForgery() {
        web.delete().uri("/api/operator/shipments/1").headers(h -> h.setBearerAuth(azureToken(b -> {})))
                .exchange().expectStatus().isForbidden();
        web.get().uri("/api/operator/shipments").exchange().expectStatus().isUnauthorized();
        JWTClaimsSet.Builder builder = azureClaims();
        web.get().uri("/api/operator/shipments").headers(h -> h.setBearerAuth(sign(builder.build(), UNTRUSTED_KEY)))
                .exchange().expectStatus().isUnauthorized();
    }
    private static JWTClaimsSet.Builder azureClaims() {
        return new JWTClaimsSet.Builder().issuer(AZURE_ISSUER).subject("operator-subject").audience(API)
                .issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("tid", TENANT).claim("azp", SPA).claim("ver", "2.0")
                .claim("oid", "44444444-4444-4444-8444-444444444444").claim("scp", "operator.access");
    }
    private static String azureToken(Consumer<JWTClaimsSet.Builder> edit) {
        JWTClaimsSet.Builder builder = azureClaims(); edit.accept(builder); return sign(builder.build(), KEY);
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer(ISSUER).subject("test-user")
                .issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("token_use", "access").claim("client_id", CLIENT_ID)
                .claim("cognito:groups", List.of("Admin"));
    }

    private static String token(Consumer<JWTClaimsSet.Builder> customize) {
        JWTClaimsSet.Builder builder = claims();
        customize.accept(builder);
        return sign(builder.build(), KEY);
    }

    private static String sign(JWTClaimsSet claims, RSAKey signingKey) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(KEY.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException e) { throw new IllegalStateException(e); }
    }

    private static RSAKey generateKey() {
        try { return new RSAKeyGenerator(2048).keyID("test-key").generate(); }
        catch (JOSEException e) { throw new IllegalStateException(e); }
    }
}
