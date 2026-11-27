package io.github.filippobuletto.keycloakmcp.transport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Starts the packaged jar, or the native binary under {@code -Dnative}, with the http profile against Dev Services
 * Keycloak and checks the startup refusals: CORS origins, and client secret tools combined with logging that would
 * record the secrets. Runs in the integration-test phase because it needs the packaged application.
 */
@QuarkusTest
class HttpStartupIT {

    static final String STARTED = "started in";
    static final Duration TIMEOUT = Duration.ofSeconds(60);

    @ConfigProperty(name = "keycloak.url")
    String keycloakUrl;

    @Test
    void defaultStartHasTheFilterOnAndTrustsNoOrigin() throws Exception {
        String log = runUntilStarted(Map.of());

        assertFalse(log.contains("filter must be enabled"), log);
        // The extension's same startup check logs the empty allowlist at DEBUG, so the check did run
        assertTrue(log.contains("single-origin requests only"), log);
    }

    @Test
    void prodProfileStartsWithoutAdminCredentials() throws Exception {
        // prod is what the native image runs; like http it serves /mcp and acts as the signed-in user
        runUntilStarted(Map.of(), "-Dquarkus.profile=prod");
    }

    @Test
    void bareWildcardStopsStartup() throws Exception {
        Path output = Files.createTempFile(Path.of("target"), "http-startup", ".log");
        Process process = start(Map.of("MCP_CORS_ORIGINS", "*"), output);
        boolean exited = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        String log = Files.readString(output);
        if (!exited) {
            stop(process);
        }

        assertTrue(exited, "expected the server to exit:\n" + log);
        assertNotEquals(0, process.exitValue(), log);
        assertTrue(log.contains("MCP_CORS_ORIGINS contains '*'"), log);
        assertFalse(log.contains(STARTED), log);
    }

    @Test
    void regexOriginStartsWithAWarning() throws Exception {
        // List values treat a backslash as an escape, so the variable doubles each one
        String log = runUntilStarted(Map.of("MCP_CORS_ORIGINS", "/https://[a-z]+\\\\.corp\\\\.example/"));

        assertTrue(log.contains("matching the regular expression /https://[a-z]+\\.corp\\.example/"), log);
    }

    @Test
    void clientSecretsWithTrafficLoggingStopStartup() throws Exception {
        String log = runUntilExited(Map.of("MCP_CLIENT_SECRETS_ENABLED", "true",
                "QUARKUS_MCP_SERVER_TRAFFIC_LOGGING_ENABLED", "true"));

        assertTrue(log.contains("quarkus.mcp.server.traffic-logging.enabled"), log);
    }

    @Test
    void clientSecretsWithRestClientBodyLoggingStopStartup() throws Exception {
        String log = runUntilExited(Map.of("MCP_CLIENT_SECRETS_ENABLED", "true",
                "QUARKUS_REST_CLIENT_LOGGING_SCOPE", "request-response"));

        assertTrue(log.contains("quarkus.rest-client.logging.scope"), log);
    }

    @Test
    void clientSecretsAloneStart() throws Exception {
        runUntilStarted(Map.of("MCP_CLIENT_SECRETS_ENABLED", "true"));
    }

    @Test
    void bodyLoggingWithoutClientSecretsStarts() throws Exception {
        runUntilStarted(Map.of("QUARKUS_MCP_SERVER_TRAFFIC_LOGGING_ENABLED", "true",
                "QUARKUS_REST_CLIENT_LOGGING_SCOPE", "request-response"));
    }

    /** Starts the jar, expects it to exit with an error before the started line, returns everything it logged. */
    String runUntilExited(Map<String, String> env) throws Exception {
        Path output = Files.createTempFile(Path.of("target"), "http-startup", ".log");
        Process process = start(env, output);
        boolean exited = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        String log = Files.readString(output);
        if (!exited) {
            stop(process);
        }

        assertTrue(exited, "expected the server to exit:\n" + log);
        assertNotEquals(0, process.exitValue(), log);
        assertFalse(log.contains(STARTED), log);
        return log;
    }

    /** Starts the jar, waits for the started line, stops it and returns everything it logged. */
    String runUntilStarted(Map<String, String> env) throws Exception {
        return runUntilStarted(env, "-Dquarkus.profile=http");
    }

    String runUntilStarted(Map<String, String> env, String profile) throws Exception {
        Path output = Files.createTempFile(Path.of("target"), "http-startup", ".log");
        Process process = start(env, output, profile);
        try {
            Instant deadline = Instant.now().plus(TIMEOUT);
            while (!Files.readString(output).contains(STARTED)) {
                assertTrue(process.isAlive(), "server exited before starting:\n" + Files.readString(output));
                assertTrue(Instant.now().isBefore(deadline), "server did not start:\n" + Files.readString(output));
                Thread.sleep(200);
            }
            return Files.readString(output);
        } finally {
            stop(process);
        }
    }

    Process start(Map<String, String> env, Path output) throws Exception {
        return start(env, output, "-Dquarkus.profile=http");
    }

    /** No admin credentials: over HTTP the server calls Keycloak only with exchanged user tokens. */
    Process start(Map<String, String> env, Path output, String profile) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(AppLauncher.command(profile,
                "-Dquarkus.log.category.\"io.quarkiverse.mcp.server.http\".level=DEBUG"))
                .redirectErrorStream(true)
                .redirectOutput(output.toFile());
        Map<String, String> all = new HashMap<>(Map.of(
                "KEYCLOAK_URL", keycloakUrl,
                "MCP_OIDC_CLIENT_SECRET", "keycloak-mcp-server-secret",
                "QUARKUS_HTTP_PORT", "0"));
        all.putAll(env);
        builder.environment().putAll(all);
        return builder.start();
    }

    static void stop(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
    }
}
