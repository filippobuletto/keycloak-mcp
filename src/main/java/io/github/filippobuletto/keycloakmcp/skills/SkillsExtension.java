package io.github.filippobuletto.keycloakmcp.skills;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.CacheControl;
import io.quarkiverse.mcp.server.CacheScope;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpExtension;
import io.quarkiverse.mcp.server.McpExtensionMethod;
import io.quarkiverse.mcp.server.McpExtensionMethodArg;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Serves the skills under {@code skills/} through the MCP Skills extension: each file as a {@code skill://keycloak/}
 * resource, plus the {@code skills/list} and {@code skills/get} methods. The content is fixed per build.
 */
@Singleton
@Startup
@McpExtension(id = "io.modelcontextprotocol/skills")
public class SkillsExtension {

    static final String PREFIX = "skill://keycloak/";
    static final String INDEX = "skills/index.txt";
    static final long TTL_MS = 3_600_000L;
    static final String CACHE_SCOPE = "public";
    static final String MIME_TYPE = "text/markdown";

    @Inject
    ResourceManager resources;

    /** Skill entries by {@code SKILL.md} URI, in index order. */
    private Map<String, SkillEntry> entries = Map.of();

    /**
     * Loads the catalog and registers every skill file as a resource; a bad catalog stops startup. Runs when the bean is
     * created, so a request that arrives during startup waits for it instead of seeing no skills.
     */
    @PostConstruct
    void register() {
        byte[] index = read(INDEX);
        if (index == null) {
            throw new IllegalStateException(INDEX + " is missing from the build");
        }
        List<Skill> skills = SkillCatalog.load(new String(index, StandardCharsets.UTF_8),
                path -> read("skills/" + path));
        CacheControl cache = new CacheControl(TTL_MS, CacheScope.PUBLIC);
        Map<String, SkillEntry> byUri = new LinkedHashMap<>();
        for (Skill skill : skills) {
            for (SkillFile file : skill.files()) {
                String uri = PREFIX + file.path();
                boolean skillMd = file == skill.files().getFirst();
                resources.newResource(skillMd ? skill.name() : file.path().substring(file.path().lastIndexOf('/') + 1))
                        .setUri(uri)
                        .setMimeType(MIME_TYPE)
                        .setDescription(skillMd ? (String) skill.frontmatter().get("description")
                                : "Supporting file of the " + skill.name() + " skill")
                        .setCacheControl(cache)
                        .setNotifyListChanged(false)
                        .setHandler(args -> new ResourceResponse(List.of(new TextResourceContents(uri, file.text(),
                                MIME_TYPE)), null, cache))
                        .register();
            }
            SkillEntry entry = new SkillEntry(PREFIX + skill.files().getFirst().path(), skill.frontmatter(),
                    skill.files().stream().map(f -> new ManifestEntry(PREFIX + f.path(), f.digest(), f.size()))
                            .toList());
            byUri.put(entry.uri(), entry);
        }
        entries = Collections.unmodifiableMap(byUri);
        // One notification for all files: a client that listed resources during startup refreshes once
        resources.notifyListChanged(connection -> true);
    }

    @McpExtensionMethod("skills/list")
    ListResult list(@McpExtensionMethodArg(name = "cursor") Optional<String> cursor) {
        if (cursor.isPresent()) {
            throw new McpException("Invalid cursor", JsonRpcErrorCodes.INVALID_PARAMS);
        }
        return new ListResult(List.copyOf(entries.values()), TTL_MS, CACHE_SCOPE);
    }

    @McpExtensionMethod("skills/get")
    GetResult get(@McpExtensionMethodArg(name = "uri") String uri) {
        SkillEntry entry = entries.get(uri);
        if (entry == null) {
            throw new McpException("No skill is served at " + uri, JsonRpcErrorCodes.INVALID_PARAMS);
        }
        return new GetResult(entry, TTL_MS, CACHE_SCOPE);
    }

    /** The bytes of a classpath resource, or {@code null} when the build does not contain it. */
    private static byte[] read(String path) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One file in a skill's manifest. */
    @RegisterForReflection
    public record ManifestEntry(String uri, String digest, long size) {
    }

    /** A skill as {@code skills/list} and {@code skills/get} return it. */
    @RegisterForReflection
    public record SkillEntry(String uri, Map<String, Object> frontmatter, List<ManifestEntry> resources) {
    }

    /** Result of {@code skills/list}: every skill on one page. */
    @RegisterForReflection
    public record ListResult(List<SkillEntry> skills, long ttlMs, String cacheScope) {
    }

    /** Result of {@code skills/get}. */
    @RegisterForReflection
    public record GetResult(SkillEntry skill, long ttlMs, String cacheScope) {
    }
}
