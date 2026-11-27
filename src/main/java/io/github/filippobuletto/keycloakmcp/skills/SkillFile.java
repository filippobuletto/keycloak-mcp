package io.github.filippobuletto.keycloakmcp.skills;

/**
 * One file of a skill.
 *
 * @param path path relative to {@code skills/}, for example {@code flow-change/SKILL.md}
 * @param text the file's content as UTF-8 text
 * @param size the file's size in bytes
 * @param digest {@code sha256:} and the lowercase hex SHA-256 of the file's bytes
 */
public record SkillFile(String path, String text, long size, String digest) {
}
