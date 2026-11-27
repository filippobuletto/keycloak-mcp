package io.github.filippobuletto.keycloakmcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Runs the packaged jar, or the native binary under {@code -Dnative}, with the stdio profile against Dev Services
 * Keycloak and compares it with the HTTP transport of the test application. Runs in the integration-test phase because
 * it needs the packaged application.
 */
@QuarkusTest
class StdioSmokeIT {

    static final ObjectMapper JSON = new ObjectMapper();

    @ConfigProperty(name = "keycloak.url")
    String keycloakUrl;

    @Test
    void stdioServesTheHttpToolsWithOnlyJsonRpcOnStdout() throws Exception {
        Set<String> httpTools = new TreeSet<>();
        McpStreamableTestClient http = McpClients.connected();
        http.when()
                .toolsList(page -> {
                    assertEquals(null, page.nextCursor(), "expected a single page of tools");
                    page.tools().forEach(tool -> httpTools.add(tool.name()));
                })
                .thenAssertResults();
        http.disconnect();

        Path stderr = Files.createTempFile(Path.of("target"), "stdio-smoke", ".err");
        ProcessBuilder builder = new ProcessBuilder(AppLauncher.command("-Dquarkus.profile=stdio"))
                .redirectError(stderr.toFile());
        builder.environment().putAll(Map.of(
                "KEYCLOAK_URL", keycloakUrl,
                "KEYCLOAK_USERNAME", "admin",
                "KEYCLOAK_PASSWORD", "admin"));
        Process process = builder.start();
        List<String> stdout = new ArrayList<>();
        try (Writer stdin = process.outputWriter(StandardCharsets.UTF_8);
                BufferedReader out = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            send(stdin, """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",\
                    "capabilities":{},"clientInfo":{"name":"stdio-smoke","version":"1"}}}""");
            readUntilId(out, 1, stdout);
            send(stdin, """
                    {"jsonrpc":"2.0","method":"notifications/initialized"}""");
            send(stdin, """
                    {"jsonrpc":"2.0","id":2,"method":"tools/list"}""");
            JsonNode list = readUntilId(out, 2, stdout);
            send(stdin, """
                    {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"listRealms","arguments":{}}}""");
            JsonNode call = readUntilId(out, 3, stdout);
            send(stdin, """
                    {"jsonrpc":"2.0","id":4,"method":"resources/read","params":{"uri":"keycloak://realms"}}""");
            JsonNode resource = readUntilId(out, 4, stdout);
            send(stdin, """
                    {"jsonrpc":"2.0","id":5,"method":"prompts/get","params":{"name":"audit-realm",\
                    "arguments":{"realm":"acme"}}}""");
            JsonNode prompt = readUntilId(out, 5, stdout);

            assertTrue(list.path("result").path("nextCursor").isMissingNode(), list.toString());
            Set<String> stdioTools = StreamSupport.stream(list.path("result").path("tools").spliterator(), false)
                    .map(tool -> tool.get("name").asText())
                    .collect(Collectors.toCollection(TreeSet::new));
            assertFalse(stdioTools.isEmpty(), list.toString());
            assertEquals(httpTools, stdioTools);

            assertFalse(call.path("result").path("isError").asBoolean(), call.toString());
            assertTrue(call.toString().contains("acme"), call.toString());

            String realms = resource.path("result").path("contents").path(0).path("text").asText();
            assertTrue(realms.contains("\"acme\""), resource.toString());
            String audit = prompt.path("result").path("messages").path(0).path("content").path("text").asText();
            assertTrue(audit.contains("realm acme"), prompt.toString());
        } finally {
            process.destroy();
            process.waitFor(10, TimeUnit.SECONDS);
        }

        for (String line : stdout) {
            JsonNode message = JSON.readTree(line);
            assertEquals("2.0", message.path("jsonrpc").asText(), "non JSON-RPC line on stdout: " + line);
        }
        String logs = Files.readString(stderr);
        assertTrue(logs.contains("Profile stdio activated"), "expected Quarkus logs on stderr:\n" + logs);
    }

    @Test
    void stdioListsTheSkillsOnTheStatelessRevision() throws Exception {
        Path stderr = Files.createTempFile(Path.of("target"), "stdio-skills", ".err");
        ProcessBuilder builder = new ProcessBuilder(AppLauncher.command("-Dquarkus.profile=stdio"))
                .redirectError(stderr.toFile());
        builder.environment().putAll(Map.of(
                "KEYCLOAK_URL", keycloakUrl,
                "KEYCLOAK_USERNAME", "admin",
                "KEYCLOAK_PASSWORD", "admin"));
        Process process = builder.start();
        List<String> stdout = new ArrayList<>();
        try (Writer stdin = process.outputWriter(StandardCharsets.UTF_8);
                BufferedReader out = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            send(stdin, """
                    {"jsonrpc":"2.0","id":1,"method":"skills/list","params":{"_meta":{\
                    "io.modelcontextprotocol/protocolVersion":"2026-07-28",\
                    "io.modelcontextprotocol/clientInfo":{"name":"stdio-smoke","version":"1"},\
                    "io.modelcontextprotocol/clientCapabilities":{}}}}""");
            JsonNode list = readUntilId(out, 1, stdout);

            Set<String> skills = StreamSupport.stream(list.path("result").path("skills").spliterator(), false)
                    .map(skill -> skill.path("frontmatter").path("name").asText())
                    .collect(Collectors.toCollection(TreeSet::new));
            assertEquals(Set.of("flow-change", "login-troubleshooting", "safe-changes", "security-audit"), skills,
                    list.toString());
            assertEquals(3_600_000, list.path("result").path("ttlMs").asLong(), list.toString());
        } finally {
            process.destroy();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }

    static void send(Writer stdin, String message) throws IOException {
        stdin.write(message + "\n");
        stdin.flush();
    }

    /** Reads stdout lines, keeping each in {@code seen}, until the response with the given id arrives. */
    static JsonNode readUntilId(BufferedReader out, int id, List<String> seen) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
            try {
                for (String line = out.readLine(); line != null; line = out.readLine()) {
                    seen.add(line);
                    JsonNode message = JSON.readTree(line);
                    if (message.path("id").asInt(-1) == id) {
                        return message;
                    }
                }
                throw new IllegalStateException("stdout closed before response " + id + ", saw " + seen);
            } catch (IOException e) {
                throw new IllegalStateException("non-JSON line on stdout after " + seen, e);
            }
        }).get(60, TimeUnit.SECONDS);
    }
}
