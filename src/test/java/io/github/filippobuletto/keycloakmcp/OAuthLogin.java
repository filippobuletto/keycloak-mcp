package io.github.filippobuletto.keycloakmcp;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Signs in to the MCP endpoint the way Claude Code does with a pre-registered public client: follows the 401
 * challenge to the protected resource metadata, then to the issuer's discovery document, and runs the authorization
 * code flow with PKCE S256. The browser part (Keycloak's login form) is driven over plain HTTP, and the callback is read
 * from the redirect, so nothing has to listen on the callback port.
 */
public final class OAuthLogin {

    public static final String CLIENT_ID = "keycloak-mcp";
    public static final String CALLBACK = "http://localhost:33418/callback";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern RESOURCE_METADATA = Pattern.compile("resource_metadata=\"([^\"]+)\"");
    private static final Pattern FORM_TAG = Pattern.compile("<form[^>]*+>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION = Pattern.compile("action=\"([^\"]+)\"");

    private OAuthLogin() {
    }

    /** The resource metadata document an unauthenticated request to {@code mcp} leads to. */
    public static JsonNode metadata(URI mcp) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(mcp)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json")
                .build(), HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == 401, "expected 401 from " + mcp, response);
        Matcher matcher = RESOURCE_METADATA.matcher(response.headers().firstValue("WWW-Authenticate").orElse(""));
        check(matcher.find(), "no resource_metadata in the challenge", response);
        return getJson(http, URI.create(matcher.group(1)));
    }

    /** Access token for {@code username} (password equals the username in the seeded realm). */
    public static String signIn(URI mcp, String username) throws Exception {
        JsonNode metadata = metadata(mcp);
        HttpClient browser = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        JsonNode issuer = getJson(browser, URI.create(
                metadata.path("authorization_servers").path(0).asText() + "/.well-known/openid-configuration"));

        // Claude Code adds offline_access to the advertised scopes, to keep a refresh token across sessions
        String scope = String.join(" ", JSON.convertValue(metadata.path("scopes_supported"), String[].class))
                + " offline_access";
        String verifier = base64Url(randomBytes(32));
        String challenge = base64Url(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        Map<String, String> authorize = new LinkedHashMap<>();
        authorize.put("response_type", "code");
        authorize.put("client_id", CLIENT_ID);
        authorize.put("redirect_uri", CALLBACK);
        authorize.put("scope", scope);
        authorize.put("state", base64Url(randomBytes(16)));
        authorize.put("code_challenge", challenge);
        authorize.put("code_challenge_method", "S256");
        authorize.put("resource", metadata.path("resource").asText());

        HttpResponse<String> loginPage = browser.send(HttpRequest.newBuilder(URI.create(
                issuer.path("authorization_endpoint").asText() + "?" + form(authorize))).build(),
                HttpResponse.BodyHandlers.ofString());
        check(loginPage.statusCode() == 200, "authorization request rejected", loginPage);
        Optional<String> formTag = FORM_TAG.matcher(loginPage.body()).results().map(MatchResult::group)
                .filter(tag -> tag.contains("id=\"kc-form-login\"")).findFirst();
        check(formTag.isPresent(), "no login form", loginPage);
        Matcher action = ACTION.matcher(formTag.get());
        check(action.find(), "no form action", loginPage);

        HttpResponse<String> redirect = browser.send(HttpRequest.newBuilder(
                URI.create(action.group(1).replace("&amp;", "&")))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookies(loginPage))
                .POST(HttpRequest.BodyPublishers.ofString(form(Map.of("username", username, "password", username))))
                .build(), HttpResponse.BodyHandlers.ofString());
        String location = redirect.headers().firstValue("Location").orElse("");
        check(redirect.statusCode() == 302 && location.startsWith(CALLBACK), "login did not redirect to the callback",
                redirect);
        Map<String, String> callback = query(URI.create(location).getRawQuery());
        check(authorize.get("state").equals(callback.get("state")), "state mismatch: " + location, redirect);
        check(callback.containsKey("code"), "no code in " + location, redirect);

        Map<String, String> exchange = new LinkedHashMap<>();
        exchange.put("grant_type", "authorization_code");
        exchange.put("client_id", CLIENT_ID);
        exchange.put("code", callback.get("code"));
        exchange.put("redirect_uri", CALLBACK);
        exchange.put("code_verifier", verifier);
        exchange.put("resource", metadata.path("resource").asText());
        HttpResponse<String> token = browser.send(HttpRequest.newBuilder(
                URI.create(issuer.path("token_endpoint").asText()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(exchange)))
                .build(), HttpResponse.BodyHandlers.ofString());
        check(token.statusCode() == 200, "token request failed", token);
        return JSON.readTree(token.body()).path("access_token").asText();
    }

    /**
     * Cookies to send back, as a browser on localhost does. Keycloak marks them Secure, which java.net.CookieManager
     * never sends over http, while browsers treat localhost as a secure context.
     */
    private static String cookies(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .map(cookie -> cookie.split(";", 2)[0])
                .collect(Collectors.joining("; "));
    }

    private static JsonNode getJson(HttpClient http, URI uri) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri).build(),
                HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == 200, "GET " + uri, response);
        return JSON.readTree(response.body());
    }

    private static void check(boolean condition, String message, HttpResponse<String> response) {
        if (!condition) {
            throw new AssertionError(message + ": " + response.statusCode() + " " + response.headers().map() + "\n"
                    + response.body());
        }
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(parts[0], parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return values;
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
