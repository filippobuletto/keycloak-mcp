package io.github.filippobuletto.keycloakmcp.skills;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A skill served by this server.
 *
 * @param name the skill's name, equal to its directory under {@code skills/}
 * @param frontmatter the {@code SKILL.md} frontmatter, parsed from YAML, in file order
 * @param files the skill's files, {@code SKILL.md} first, then the index order
 */
public record Skill(String name, Map<String, Object> frontmatter, List<SkillFile> files) {

    public Skill {
        frontmatter = Collections.unmodifiableMap(new LinkedHashMap<>(frontmatter));
        files = List.copyOf(files);
    }
}
