package io.github.filippobuletto.keycloakmcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

class AppLauncherTest {

    static final List<String> PROPS = List.of("-Dquarkus.profile=stdio", "-Dfoo=bar");

    @Test
    void withoutNativeImagePathRunsTheJar() {
        List<String> command = AppLauncher.command(null, "Mac OS X", "/opt/jdk", PROPS);

        assertEquals(List.of(Path.of("/opt/jdk", "bin", "java").toString(), "-Dquarkus.profile=stdio", "-Dfoo=bar",
                "-jar", "target/quarkus-app/quarkus-run.jar"), command);
    }

    @Test
    void blankNativeImagePathRunsTheJar() {
        List<String> command = AppLauncher.command(" ", "Linux", "/opt/jdk", PROPS);

        assertEquals("target/quarkus-app/quarkus-run.jar", command.getLast());
    }

    @Test
    void withNativeImagePathOnLinuxRunsTheBinary() {
        List<String> command = AppLauncher.command("/work/target/keycloak-mcp-1.0.0-SNAPSHOT-runner", "Linux",
                "/opt/jdk", PROPS);

        assertEquals(List.of("/work/target/keycloak-mcp-1.0.0-SNAPSHOT-runner", "-Dquarkus.profile=stdio",
                "-Dfoo=bar"), command);
    }

    @Test
    void withNativeImagePathOffLinuxSkipsWithTheReason() {
        TestAbortedException skipped = assertThrows(TestAbortedException.class,
                () -> AppLauncher.command("/work/target/keycloak-mcp-1.0.0-SNAPSHOT-runner", "Mac OS X", "/opt/jdk",
                        PROPS));

        assertEquals("Assumption failed: native binary is Linux-only; run ./mvnw verify -Dnative on Linux or in CI",
                skipped.getMessage());
    }
}
