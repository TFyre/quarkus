package io.quarkus.it.keycloak;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.keycloak.representations.AccessTokenResponse;

import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.Cookie;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
@QuarkusTest
@QuarkusTestResource(KeycloakRealmResourceManager.class)
public class BearerTokenAuthorizationTest {

    private static final String KEYCLOAK_SERVER_URL = System.getProperty("keycloak.url", "http://localhost:8180/auth");
    private static final String KEYCLOAK_REALM = "quarkus-";

    @Test
    public void testResolveTenantIdentifierWebApp() throws IOException {
        try (final WebClient webClient = createWebClient()) {
            HtmlPage page = webClient.getPage("http://localhost:8081/tenant/tenant-web-app/api/user/webapp");
            // State cookie is available but there must be no saved path parameter
            // as the tenant-web-app configuration does not set a redirect-path property
            assertNull(getStateCookieSavedPath(webClient, "tenant-web-app"));
            assertEquals("Log in to quarkus-webapp", page.getTitleText());
            HtmlForm loginForm = page.getForms().get(0);
            loginForm.getInputByName("username").setValueAttribute("alice");
            loginForm.getInputByName("password").setValueAttribute("alice");
            page = loginForm.getInputByName("login").click();
            assertEquals("tenant-web-app:alice", page.getBody().asText());
            webClient.getCookieManager().clearCookies();
        }
    }

    @Test
    public void testResolveTenantIdentifierWebApp2() throws IOException {
        try (final WebClient webClient = createWebClient()) {
            HtmlPage page = webClient.getPage("http://localhost:8081/tenant/tenant-web-app2/api/user/webapp2");
            // State cookie is available but there must be no saved path parameter
            // as the tenant-web-app configuration does not set a redirect-path property
            assertNull(getStateCookieSavedPath(webClient, "tenant-web-app2"));
            assertEquals("Log in to quarkus-webapp2", page.getTitleText());
            HtmlForm loginForm = page.getForms().get(0);
            loginForm.getInputByName("username").setValueAttribute("alice");
            loginForm.getInputByName("password").setValueAttribute("alice");
            page = loginForm.getInputByName("login").click();
            assertEquals("tenant-web-app2:alice", page.getBody().asText());
            webClient.getCookieManager().clearCookies();
        }
    }

    @Test
    public void testReAuthenticateWhenSwitchingTenants() throws IOException {
        try (final WebClient webClient = createWebClient()) {
            // tenant-web-app
            HtmlPage page = webClient.getPage("http://localhost:8081/tenant/tenant-web-app/api/user/webapp");
            assertNull(getStateCookieSavedPath(webClient, "tenant-web-app"));
            assertEquals("Log in to quarkus-webapp", page.getTitleText());
            HtmlForm loginForm = page.getForms().get(0);
            loginForm.getInputByName("username").setValueAttribute("alice");
            loginForm.getInputByName("password").setValueAttribute("alice");
            page = loginForm.getInputByName("login").click();
            assertEquals("tenant-web-app:alice", page.getBody().asText());
            // tenant-web-app2
            page = webClient.getPage("http://localhost:8081/tenant/tenant-web-app2/api/user/webapp2");
            assertNull(getStateCookieSavedPath(webClient, "tenant-web-app2"));
            assertEquals("Log in to quarkus-webapp2", page.getTitleText());
            loginForm = page.getForms().get(0);
            loginForm.getInputByName("username").setValueAttribute("alice");
            loginForm.getInputByName("password").setValueAttribute("alice");
            page = loginForm.getInputByName("login").click();
            assertEquals("tenant-web-app2:alice", page.getBody().asText());

            webClient.getCookieManager().clearCookies();
        }
    }

    @Test
    public void testResolveTenantIdentifier() {
        RestAssured.given().auth().oauth2(getAccessToken("alice", "b"))
                .when().get("/tenant/tenant-b/api/user")
                .then()
                .statusCode(200)
                .body(equalTo("tenant-b:alice"));

        // should give a 401 given that access token from issuer b can not access tenant c
        RestAssured.given().auth().oauth2(getAccessToken("alice", "b"))
                .when().get("/tenant/tenant-c/api/user")
                .then()
                .statusCode(401);
    }

    @Test
    public void testCustomHeader() {
        RestAssured.given().header("X-Forwarded-Authorization", getAccessToken("alice", "b"))
                .when().get("/tenant/tenant-customheader/api/user")
                .then()
                .statusCode(200)
                .body(equalTo("tenant-customheader:alice"));
    }

    @Test
    public void testCustomHeaderBearerScheme() {
        RestAssured.given().header("X-Forwarded-Authorization", "Bearer " + getAccessToken("alice", "b"))
                .when().get("/tenant/tenant-customheader/api/user")
                .then()
                .statusCode(200)
                .body(equalTo("tenant-customheader:alice"));
    }

    @Test
    public void testWrongCustomHeader() {
        RestAssured.given().header("X-Authorization", getAccessToken("alice", "b"))
                .when().get("/tenant/tenant-customheader/api/user")
                .then()
                .statusCode(401);
    }

    @Test
    public void testCustomHeaderCustomScheme() {
        RestAssured.given().header("X-Forwarded-Authorization", "DPoP " + getAccessToken("alice", "b"))
                .when().get("/tenant/tenant-customheader/api/user")
                .then()
                .statusCode(401);
    }

    @Test
    public void testResolveTenantConfig() {
        RestAssured.given().auth().oauth2(getAccessToken("alice", "d"))
                .when().get("/tenant/tenant-d/api/user")
                .then()
                .statusCode(200)
                .body(equalTo("tenant-d:alice.alice"));

        // should give a 401 given that access token from issuer b can not access tenant c
        RestAssured.given().auth().oauth2(getAccessToken("alice", "b"))
                .when().get("/tenant/tenant-d/api/user")
                .then()
                .statusCode(401);
    }

    @Test
    public void testDefaultTenant() {
        // any non-extent tenant should accept tokens from tenant a
        RestAssured.given().auth().oauth2(getAccessToken("alice", "a"))
                .when().get("/tenant/tenant-any/api/user")
                .then()
                .statusCode(200)
                .body(equalTo("tenant-any:alice"));
    }

    @Test
    public void testSimpleOidcJwtWithJwkRefresh() {
        RestAssured.when().post("/oidc/jwk-endpoint-call-count").then().body(equalTo("0"));
        RestAssured.when().get("/oidc/introspection-status").then().body(equalTo("false"));
        RestAssured.when().get("/oidc/rotate-status").then().body(equalTo("false"));
        // Quarkus OIDC is initialized with JWK set with kid '1' as part of the discovery process
        // Now enable the rotation
        RestAssured.when().post("/oidc/rotate").then().body(equalTo("true"));

        // OIDC server will have a refreshed JWK set with kid '2', 200 is expected even though the introspection fallback is disabled.
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        Response r = RestAssured.given().auth().oauth2(getAccessTokenFromSimpleOidc("2"))
                                .when().get("/tenant/tenant-oidc/api/user");
                        return r.getStatusCode() == 200;
                    }
                });

        // JWK is available now in Quarkus OIDC, confirm that no timeout is needed 
        RestAssured.given().auth().oauth2(getAccessTokenFromSimpleOidc("2"))
                .when().get("/tenant/tenant-oidc/api/user")
                .then()
                .statusCode(200)
                .body(equalTo("tenant-oidc:alice"));

        // Get a token with kid '3' - it can only be verified via the introspection fallback since OIDC returns JWK set with kid '2'
        // 401 since the introspection is not enabled
        RestAssured.given().auth().oauth2(getAccessTokenFromSimpleOidc("3"))
                .when().get("/tenant/tenant-oidc/api/user")
                .then()
                .statusCode(401);

        // Enable introspection
        RestAssured.when().post("/oidc/introspection").then().body(equalTo("true"));
        // No timeout is required
        RestAssured.given().auth().oauth2(getAccessTokenFromSimpleOidc("3"))
                .when().get("/tenant/tenant-oidc/api/user")
                .then()
                .statusCode(200)
                .body(equalTo("tenant-oidc:alice"));

        // Finally try the opaque token 
        RestAssured.given().auth().oauth2(getOpaqueAccessTokenFromSimpleOidc())
                .when().get("/tenant-opaque/tenant-oidc/api/user")
                .then()
                .statusCode(200)
                .body(equalTo("tenant-oidc-opaque:alice"));

        // OIDC JWK endpoint must've been called only twice, once as part of the Quarkus OIDC/Vertx Auth initialization
        // and once during the 1st request with a token kid '2', follow up requests must've been blocked due to the interval
        // restrictions
        RestAssured.when().get("/oidc/jwk-endpoint-call-count").then().body(equalTo("2"));
    }

    @Test
    public void testSimpleOidcNoDiscovery() {
        RestAssured.when().post("/oidc/jwk-endpoint-call-count").then().body(equalTo("0"));
        RestAssured.when().get("/oidc/introspection-status").then().body(equalTo("false"));
        RestAssured.when().get("/oidc/rotate-status").then().body(equalTo("false"));

        // Quarkus OIDC is initialized with JWK set with kid '1' as part of the initialization process
        RestAssured.given().auth().oauth2(getAccessTokenFromSimpleOidc("1"))
                .when().get("/tenant/tenant-oidc-no-discovery/api/user")
                .then()
                .statusCode(200)
                .body(equalTo("tenant-oidc-no-discovery:alice"));
        RestAssured.when().get("/oidc/jwk-endpoint-call-count").then().body(equalTo("1"));
    }

    private String getAccessToken(String userName, String clientId) {
        return RestAssured
                .given()
                .param("grant_type", "password")
                .param("username", userName)
                .param("password", userName)
                .param("client_id", "quarkus-app-" + clientId)
                .param("client_secret", "secret")
                .when()
                .post(KEYCLOAK_SERVER_URL + "/realms/" + KEYCLOAK_REALM + clientId + "/protocol/openid-connect/token")
                .as(AccessTokenResponse.class).getToken();
    }

    private String getAccessTokenFromSimpleOidc(String kid) {
        String json = RestAssured
                .given()
                .queryParam("kid", kid)
                .when()
                .post("/oidc/token")
                .body().asString();
        JsonObject object = new JsonObject(json);
        return object.getString("access_token");
    }

    private String getOpaqueAccessTokenFromSimpleOidc() {
        String json = RestAssured
                .when()
                .post("/oidc/opaque-token")
                .body().asString();
        JsonObject object = new JsonObject(json);
        return object.getString("access_token");
    }

    private WebClient createWebClient() {
        WebClient webClient = new WebClient();
        webClient.setCssErrorHandler(new SilentCssErrorHandler());
        return webClient;
    }

    private Cookie getStateCookie(WebClient webClient, String tenantId) {
        return webClient.getCookieManager().getCookie("q_auth" + (tenantId == null ? "" : "_" + tenantId));
    }

    private String getStateCookieSavedPath(WebClient webClient, String tenantId) {
        String[] parts = getStateCookie(webClient, tenantId).getValue().split("\\|");
        return parts.length == 2 ? parts[1] : null;
    }
}
