package io.github.filippobuletto.keycloakmcp.transport;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;

/** Checks the trusted CORS origins at startup: refuses a bare {@code *}, warns about each regex entry. */
@Startup
@ApplicationScoped
class CorsOriginsCheck {

    CorsOriginsCheck(@ConfigProperty(name = "quarkus.http.cors.origins") Optional<List<String>> origins) {
        for (String regex : CorsOrigins.regexEntries(origins.orElse(List.of()))) {
            Log.warnf("MCP_CORS_ORIGINS trusts every origin matching the regular expression %s; check that it is"
                    + " anchored and escaped (a single backslash in MCP_CORS_ORIGINS is dropped, write \\\\. for"
                    + " a literal dot), or list exact origins instead", regex);
        }
    }
}
