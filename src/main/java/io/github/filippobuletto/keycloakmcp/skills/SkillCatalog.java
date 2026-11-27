package io.github.filippobuletto.keycloakmcp.skills;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * Reads the skill files named in {@code skills/index.txt} and checks them against the Agent Skills rules. Any problem
 * fails with an {@link IllegalStateException} naming the file, so a bad build cannot start.
 */
public final class SkillCatalog {

    static final String SKILL_MD = "SKILL.md";
    private static final Pattern NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*+");
    private static final int MAX_NAME = 64;
    private static final int MAX_DESCRIPTION = 1024;
    private static final YAMLMapper YAML = new YAMLMapper();

    private SkillCatalog() {
    }

    /**
     * Loads every skill named in the index.
     *
     * @param index the index text: one path per line, relative to {@code skills/}; blank lines are skipped
     * @param reader returns the bytes of a path relative to {@code skills/}, or {@code null} when it does not exist
     * @return the skills in index order
     */
    public static List<Skill> load(String index, Function<String, byte[]> reader) {
        Map<String, List<SkillFile>> filesBySkill = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String line : index.split("\n")) {
            String path = line.strip();
            if (path.isEmpty()) {
                continue;
            }
            if (!seen.add(path)) {
                throw refused(path, "listed twice in the index");
            }
            String[] segments = path.split("/");
            if (segments.length < 2 || List.of(segments).stream().anyMatch(s -> s.isEmpty() || s.equals("..")
                    || s.equals("."))) {
                throw refused(path, "not a path inside a skill directory");
            }
            if (!path.endsWith(".md")) {
                throw refused(path, "only Markdown files are served");
            }
            byte[] bytes = reader.apply(path);
            if (bytes == null) {
                throw refused(path, "listed in the index but not found");
            }
            filesBySkill.computeIfAbsent(segments[0], k -> new ArrayList<>()).add(file(path, bytes));
        }
        if (filesBySkill.isEmpty()) {
            throw new IllegalStateException("skills/index.txt lists no skill files");
        }

        List<Skill> skills = new ArrayList<>();
        filesBySkill.forEach((directory, files) -> skills.add(skill(directory, files)));
        return List.copyOf(skills);
    }

    private static Skill skill(String directory, List<SkillFile> files) {
        String skillMdPath = directory + "/" + SKILL_MD;
        SkillFile skillMd = files.stream().filter(f -> f.path().equals(skillMdPath)).findFirst()
                .orElseThrow(() -> refused(skillMdPath, "missing: every skill needs a SKILL.md in the index"));
        Map<String, Object> frontmatter = frontmatter(skillMd);

        Object name = frontmatter.get("name");
        if (!directory.equals(name)) {
            throw refused(skillMdPath, "frontmatter name '" + name + "' must equal the directory name");
        }
        if (directory.length() > MAX_NAME || !NAME.matcher(directory).matches()) {
            throw refused(skillMdPath, "frontmatter name must be 1-64 characters of a-z, 0-9 and single hyphens");
        }
        if (!(frontmatter.get("description") instanceof String description) || description.isBlank()
                || description.length() > MAX_DESCRIPTION) {
            throw refused(skillMdPath, "frontmatter description must be text of 1-1024 characters");
        }

        List<SkillFile> ordered = new ArrayList<>(files);
        ordered.remove(skillMd);
        ordered.addFirst(skillMd);
        return new Skill(directory, frontmatter, ordered);
    }

    private static Map<String, Object> frontmatter(SkillFile skillMd) {
        List<String> lines = skillMd.text().lines().toList();
        int end = lines.isEmpty() || !lines.getFirst().equals("---") ? -1 : lines.subList(1, lines.size()).indexOf("---");
        if (end < 0) {
            throw refused(skillMd.path(), "no frontmatter block between two '---' lines at the top");
        }
        String yaml = String.join("\n", lines.subList(1, end + 1));
        try {
            Object parsed = YAML.readValue(yaml.isBlank() ? "{}" : yaml, Object.class);
            if (!(parsed instanceof Map<?, ?>)) {
                throw refused(skillMd.path(), "frontmatter must be a YAML mapping");
            }
            return YAML.convertValue(parsed, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw refused(skillMd.path(), "frontmatter is not valid YAML: " + e.getOriginalMessage());
        }
    }

    private static SkillFile file(String path, byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            return new SkillFile(path, new String(bytes, StandardCharsets.UTF_8), bytes.length,
                    "sha256:" + HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static IllegalStateException refused(String path, String reason) {
        return new IllegalStateException("skills/" + path + ": " + reason);
    }
}
