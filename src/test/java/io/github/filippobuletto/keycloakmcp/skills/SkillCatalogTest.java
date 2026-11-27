package io.github.filippobuletto.keycloakmcp.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SkillCatalogTest {

    static final String SKILL = """
            ---
            name: demo
            description: Does the demo.
            license: EUPL-1.2
            ---

            # Demo
            """;

    static final String REFERENCE = "# Notes\n\nSee `getRealm`.\n";

    static List<Skill> load(String index, Map<String, String> files) {
        return SkillCatalog.load(index, path -> {
            String text = files.get(path);
            return text == null ? null : text.getBytes(StandardCharsets.UTF_8);
        });
    }

    static Map<String, String> files(String... pathsAndTexts) {
        Map<String, String> files = new LinkedHashMap<>();
        for (int i = 0; i < pathsAndTexts.length; i += 2) {
            files.put(pathsAndTexts[i], pathsAndTexts[i + 1]);
        }
        return files;
    }

    static String sha256(String text) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        return "sha256:" + HexFormat.of().formatHex(hash);
    }

    static IllegalStateException refused(String index, Map<String, String> files) {
        return assertThrows(IllegalStateException.class, () -> load(index, files));
    }

    @Test
    void loadsSkillWithSkillMdFirst() throws Exception {
        List<Skill> skills = load("demo/references/notes.md\ndemo/SKILL.md\n",
                files("demo/SKILL.md", SKILL, "demo/references/notes.md", REFERENCE));

        assertEquals(1, skills.size());
        Skill skill = skills.get(0);
        assertEquals("demo", skill.name());
        assertEquals(Map.of("name", "demo", "description", "Does the demo.", "license", "EUPL-1.2"),
                skill.frontmatter());
        assertEquals(List.of("demo/SKILL.md", "demo/references/notes.md"),
                skill.files().stream().map(SkillFile::path).toList());

        SkillFile reference = skill.files().get(1);
        assertEquals(REFERENCE, reference.text());
        assertEquals(REFERENCE.getBytes(StandardCharsets.UTF_8).length, reference.size());
        assertEquals(sha256(REFERENCE), reference.digest());
        assertTrue(reference.digest().matches("sha256:[0-9a-f]{64}"), reference.digest());
    }

    @Test
    void sizeCountsBytesNotCharacters() {
        String text = SKILL + "Café — ✓\n";
        Skill skill = load("demo/SKILL.md", files("demo/SKILL.md", text)).get(0);
        assertEquals(text.getBytes(StandardCharsets.UTF_8).length, skill.files().get(0).size());
    }

    @Test
    void keepsIndexOrderAcrossSkillsAndSkipsBlankLines() {
        String other = SKILL.replace("name: demo", "name: other");
        List<Skill> skills = load("\nother/SKILL.md\n\ndemo/SKILL.md\n",
                files("demo/SKILL.md", SKILL, "other/SKILL.md", other));
        assertEquals(List.of("other", "demo"), skills.stream().map(Skill::name).toList());
    }

    @Test
    void refusesMissingFile() {
        IllegalStateException e = refused("demo/SKILL.md\ndemo/references/gone.md",
                files("demo/SKILL.md", SKILL));
        assertTrue(e.getMessage().contains("demo/references/gone.md"), e.getMessage());
    }

    @Test
    void refusesNonMarkdownFile() {
        IllegalStateException e = refused("demo/SKILL.md\ndemo/scripts/run.sh",
                files("demo/SKILL.md", SKILL, "demo/scripts/run.sh", "echo hi"));
        assertTrue(e.getMessage().contains("demo/scripts/run.sh"), e.getMessage());
    }

    @Test
    void refusesSkillWithoutSkillMd() {
        IllegalStateException e = refused("demo/references/notes.md",
                files("demo/references/notes.md", REFERENCE));
        assertTrue(e.getMessage().contains("demo/SKILL.md"), e.getMessage());
    }

    @Test
    void refusesDuplicateAndEscapingPaths() {
        IllegalStateException duplicate = refused("demo/SKILL.md\ndemo/SKILL.md", files("demo/SKILL.md", SKILL));
        assertTrue(duplicate.getMessage().contains("demo/SKILL.md"), duplicate.getMessage());
        IllegalStateException escaping = refused("demo/SKILL.md\ndemo/../x.md",
                files("demo/SKILL.md", SKILL, "demo/../x.md", REFERENCE));
        assertTrue(escaping.getMessage().contains("demo/../x.md"), escaping.getMessage());
    }

    @Test
    void refusesNameThatDiffersFromDirectory() {
        IllegalStateException e = refused("demo/SKILL.md",
                files("demo/SKILL.md", SKILL.replace("name: demo", "name: other")));
        assertTrue(e.getMessage().contains("demo/SKILL.md"), e.getMessage());
        assertTrue(e.getMessage().contains("other"), e.getMessage());
    }

    @Test
    void refusesBadNames() {
        for (String name : List.of("Demo", "de--mo", "-demo", "demo-", "a".repeat(65))) {
            IllegalStateException e = refused(name + "/SKILL.md",
                    files(name + "/SKILL.md", SKILL.replace("name: demo", "name: " + name)));
            assertTrue(e.getMessage().contains(name + "/SKILL.md"), e.getMessage());
        }
        String longest = "a".repeat(64);
        assertEquals(longest, load(longest + "/SKILL.md",
                files(longest + "/SKILL.md", SKILL.replace("name: demo", "name: " + longest))).get(0).name());
    }

    @Test
    void refusesMissingOrEmptyDescription() {
        for (String replacement : List.of("", "description: \"\"", "description: 42")) {
            IllegalStateException e = refused("demo/SKILL.md",
                    files("demo/SKILL.md", SKILL.replace("description: Does the demo.", replacement)));
            assertTrue(e.getMessage().contains("demo/SKILL.md"), e.getMessage());
            assertTrue(e.getMessage().contains("description"), e.getMessage());
        }
    }

    @Test
    void refusesDescriptionOver1024Characters() {
        String ok = "d".repeat(1024);
        load("demo/SKILL.md", files("demo/SKILL.md", SKILL.replace("Does the demo.", ok)));
        IllegalStateException e = refused("demo/SKILL.md",
                files("demo/SKILL.md", SKILL.replace("Does the demo.", ok + "d")));
        assertTrue(e.getMessage().contains("description"), e.getMessage());
    }

    @Test
    void refusesSkillMdWithoutFrontmatter() {
        for (String text : List.of("# Demo\n", "---\nname: demo\n", "---\n- a\n- b\n---\n")) {
            IllegalStateException e = refused("demo/SKILL.md", files("demo/SKILL.md", text));
            assertTrue(e.getMessage().contains("demo/SKILL.md"), e.getMessage());
            assertTrue(e.getMessage().contains("frontmatter"), e.getMessage());
        }
    }

    @Test
    void refusesEmptyIndex() {
        refused("\n", files());
    }
}
