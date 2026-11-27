package io.github.filippobuletto.keycloakmcp.transport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;

/**
 * Builds the command that starts the packaged application for the integration tests: the native binary when the
 * {@code native} profile sets {@code native.image.path}, else the jar.
 */
final class AppLauncher {

    static final String JAR = "target/quarkus-app/quarkus-run.jar";

    private AppLauncher() {
    }

    /** The command for this JVM's {@code native.image.path}, OS and Java home, with the given {@code -D} options. */
    static List<String> command(String... properties) {
        return command(System.getProperty("native.image.path"), System.getProperty("os.name"),
                System.getProperty("java.home"), List.of(properties));
    }

    static List<String> command(String nativeImagePath, String osName, String javaHome, List<String> properties) {
        List<String> command = new ArrayList<>();
        if (nativeImagePath == null || nativeImagePath.isBlank()) {
            command.add(Path.of(javaHome, "bin", "java").toString());
            command.addAll(properties);
            command.addAll(List.of("-jar", JAR));
        } else {
            // The container build always produces a Linux binary
            Assumptions.assumeTrue(osName.startsWith("Linux"),
                    "native binary is Linux-only; run ./mvnw verify -Dnative on Linux or in CI");
            command.add(nativeImagePath);
            command.addAll(properties);
        }
        return command;
    }
}
