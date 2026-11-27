package io.github.filippobuletto.keycloakmcp.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@QuarkusTest
class SkillsTest {

    static final List<String> SKILLS = List.of("safe-changes", "login-troubleshooting", "flow-change", "security-audit");
    static final String EXTENSION = "io.modelcontextprotocol/skills";
    static final String PREFIX = "skill://keycloak/";

    /** Client on the stateless revision 2026-07-28, where the Skills extension is defined. */
    static McpStreamableTestClient client() {
        return McpAssured.newStreamableClient()
                .setBearerToken(McpClients.accessToken(McpClients.ADMIN_USER))
                .setStateless()
                .build()
                .connect();
    }

    static JsonObject request(McpStreamableTestClient client, String method, JsonObject params) {
        JsonObject request = client.newRequest(method);
        if (params != null) {
            request.put("params", params.copy());
        }
        McpAssured.injectStatelessMeta(request);
        return request;
    }

    static JsonObject call(McpStreamableTestClient client, String method, JsonObject params) {
        AtomicReference<JsonObject> response = new AtomicReference<>();
        client.when().message(request(client, method, params)).withAssert(response::set).send().thenAssertResults();
        return response.get();
    }

    static void assertInvalidParams(McpStreamableTestClient client, String method, JsonObject params,
            String message) {
        client.when()
                .message(request(client, method, params))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.code(), error.toString());
                    assertEquals(message, error.message());
                })
                .send()
                .thenAssertResults();
    }

    static String classpathText(String path) {
        try (InputStream in = SkillsTest.class.getResourceAsStream("/skills/" + path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Index paths of one skill, SKILL.md first. */
    static List<String> indexFiles(String skill) {
        List<String> files = new ArrayList<>();
        files.add(skill + "/SKILL.md");
        classpathText("index.txt").lines().map(String::strip)
                .filter(p -> p.startsWith(skill + "/") && !p.equals(skill + "/SKILL.md"))
                .forEach(files::add);
        return files;
    }

    static Map<?, ?> frontmatter(String skill) throws IOException {
        String text = classpathText(skill + "/SKILL.md");
        String[] parts = text.split("(?m)^---$", 3);
        return new YAMLMapper().readValue(parts[1], Map.class);
    }

    static String sha256(String text) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        return "sha256:" + HexFormat.of().formatHex(hash);
    }

    static void assertCacheFields(JsonObject result) {
        assertEquals("complete", result.getString("resultType"), result.encode());
        assertEquals(3_600_000L, result.getLong("ttlMs"), result.encode());
        assertEquals("public", result.getString("cacheScope"), result.encode());
    }

    @Test
    void discoverAdvertisesTheExtensionWithoutSettings() {
        McpStreamableTestClient client = client();
        JsonObject response = call(client, "server/discover", null);
        JsonObject extensions = response.getJsonObject("result").getJsonObject("capabilities")
                .getJsonObject("extensions");
        assertNotNull(extensions, response.encode());
        assertEquals(new JsonObject(), extensions.getJsonObject(EXTENSION), response.encode());
        client.disconnect();
    }

    @Test
    void listReturnsEverySkillWithItsManifest() throws IOException {
        McpStreamableTestClient client = client();
        JsonObject result = call(client, "skills/list", null).getJsonObject("result");

        assertCacheFields(result);
        assertFalse(result.containsKey("nextCursor"), result.encode());
        JsonArray skills = result.getJsonArray("skills");
        assertEquals(SKILLS.size(), skills.size(), result.encode());
        for (int i = 0; i < skills.size(); i++) {
            JsonObject entry = skills.getJsonObject(i);
            String name = entry.getJsonObject("frontmatter").getString("name");
            assertTrue(SKILLS.contains(name), entry.encode());
            assertEquals(PREFIX + name + "/SKILL.md", entry.getString("uri"));
            assertEquals(new JsonObject(frontmatter(name).entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue))),
                    entry.getJsonObject("frontmatter"));
            List<String> manifest = entry.getJsonArray("resources").stream()
                    .map(r -> ((JsonObject) r).getString("uri")).toList();
            assertEquals(indexFiles(name).stream().map(p -> PREFIX + p).toList(), manifest, entry.encode());
        }
        client.disconnect();
    }

    @Test
    void manifestMatchesTheResourceContents() throws NoSuchAlgorithmException {
        McpStreamableTestClient client = client();
        JsonArray skills = call(client, "skills/list", null).getJsonObject("result").getJsonArray("skills");
        List<JsonObject> files = skills.stream()
                .flatMap(s -> ((JsonObject) s).getJsonArray("resources").stream())
                .map(JsonObject.class::cast)
                .toList();
        assertFalse(files.isEmpty());

        List<String> checked = new ArrayList<>();
        for (JsonObject file : files) {
            String uri = file.getString("uri");
            AtomicReference<TextResourceContents> contents = new AtomicReference<>();
            client.when()
                    .resourcesRead(uri, response -> contents.set(response.contents().get(0).asText()))
                    .thenAssertResults();
            String text = contents.get().text();
            assertEquals("text/markdown", contents.get().mimeType(), uri);
            assertEquals(text.getBytes(StandardCharsets.UTF_8).length, file.getLong("size"), uri);
            assertEquals(sha256(text), file.getString("digest"), uri);
            assertEquals(classpathText(uri.substring(PREFIX.length())), text, uri);
            checked.add(uri);
        }
        assertEquals(files.size(), checked.size());
        client.disconnect();
    }

    @Test
    void getReturnsTheSameEntryAsList() {
        McpStreamableTestClient client = client();
        JsonArray skills = call(client, "skills/list", null).getJsonObject("result").getJsonArray("skills");
        for (int i = 0; i < skills.size(); i++) {
            JsonObject entry = skills.getJsonObject(i);
            JsonObject result = call(client, "skills/get", new JsonObject().put("uri", entry.getString("uri")))
                    .getJsonObject("result");
            assertCacheFields(result);
            assertEquals(entry, result.getJsonObject("skill"));
        }
        client.disconnect();
    }

    @Test
    void getRefusesUnknownSkillsAndSupportingFiles() {
        McpStreamableTestClient client = client();
        String unknown = PREFIX + "nope/SKILL.md";
        assertInvalidParams(client, "skills/get", new JsonObject().put("uri", unknown),
                "No skill is served at " + unknown);
        String supporting = PREFIX + "safe-changes/references/confirmations.md";
        assertInvalidParams(client, "skills/get", new JsonObject().put("uri", supporting),
                "No skill is served at " + supporting);
        client.disconnect();
    }

    @Test
    void listRefusesAnyCursor() {
        McpStreamableTestClient client = client();
        assertInvalidParams(client, "skills/list", new JsonObject().put("cursor", "abc"), "Invalid cursor");
        client.disconnect();
    }
}
