package io.github.filippobuletto.keycloakmcp.transport;

import java.util.List;

/** Rules for the trusted CORS origins configured through {@code MCP_CORS_ORIGINS}. */
final class CorsOrigins {

    private CorsOrigins() {
    }

    /**
     * Returns the regular-expression entries (values enclosed in {@code /}) so the caller can warn about them.
     *
     * @throws IllegalStateException when an entry is a bare {@code *}, which would trust every origin
     */
    static List<String> regexEntries(List<String> origins) {
        List<String> trimmed = origins.stream().map(String::trim).toList();
        if (trimmed.contains("*")) {
            throw new IllegalStateException("MCP_CORS_ORIGINS contains '*', which would let any website call /mcp."
                    + " List the exact origins to trust, for example https://console.example.com");
        }
        return trimmed.stream().filter(origin -> origin.length() > 1 && origin.startsWith("/") && origin.endsWith("/"))
                .toList();
    }
}
