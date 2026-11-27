package io.github.filippobuletto.keycloakmcp.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Checks that the {@code security-audit} skill restates every kcwarden check named in
 * {@code src/test/resources/kcwarden-checks.txt}, each in the reference file its row names.
 */
class KcwardenCoverageTest {

    static final Path SKILL = SkillSourcesTest.SKILLS.resolve("security-audit");
    static final Path CHECKS = Path.of("src/test/resources/kcwarden-checks.txt");
    static final String NOT_VISIBLE = "**Not visible through this server**";
    static final Pattern HEADING = Pattern.compile("(?m)^## (\\S+)\\s*$");
    static final Pattern BACKTICKED_CAMEL_CASE = SkillSourcesTest.BACKTICKED_CAMEL_CASE;

    /** One row of the check list. */
    record Check(String file, String name, boolean fromMain, boolean notVisible) {
    }

    static List<Check> checks() throws IOException {
        List<Check> checks = new ArrayList<>();
        for (String line : Files.readAllLines(CHECKS)) {
            String row = line.strip();
            if (row.isEmpty() || row.startsWith("#")) {
                continue;
            }
            List<String> fields = List.of(row.split("\\s+"));
            Set<String> flags = new TreeSet<>(fields.subList(2, fields.size()));
            assertTrue(Set.of("main", "not-visible").containsAll(flags), row);
            checks.add(new Check(fields.get(0), fields.get(1), flags.contains("main"), flags.contains("not-visible")));
        }
        return checks;
    }

    /** Sections of each reference file by heading, in file order. */
    static Map<String, Map<String, String>> sectionsByFile() throws IOException {
        Map<String, Map<String, String>> byFile = new TreeMap<>();
        try (var files = Files.list(SKILL.resolve("references"))) {
            for (Path file : files.sorted().toList()) {
                String text = Files.readString(file);
                Matcher heading = HEADING.matcher(text);
                Map<String, String> sections = new LinkedHashMap<>();
                List<int[]> bounds = new ArrayList<>();
                List<String> names = new ArrayList<>();
                while (heading.find()) {
                    names.add(heading.group(1));
                    bounds.add(new int[] { heading.start(), heading.end() });
                }
                for (int i = 0; i < names.size(); i++) {
                    int end = i + 1 < bounds.size() ? bounds.get(i + 1)[0] : text.length();
                    String previous = sections.put(names.get(i), text.substring(bounds.get(i)[1], end));
                    assertEquals(null, previous, file + " repeats the heading " + names.get(i));
                }
                byFile.put(file.getFileName().toString(), sections);
            }
        }
        return byFile;
    }

    @Test
    void everyCheckIsAHeadingInItsReferenceFileAndNothingElseIs() throws IOException {
        Map<String, Map<String, String>> sections = sectionsByFile();
        Map<String, Set<String>> expected = new TreeMap<>();
        for (Check check : checks()) {
            assertTrue(expected.computeIfAbsent(check.file(), f -> new TreeSet<>()).add(check.name()),
                    "listed twice: " + check.name());
        }
        Map<String, Set<String>> actual = new TreeMap<>();
        sections.forEach((file, byHeading) -> actual.put(file, new TreeSet<>(byHeading.keySet())));
        assertEquals(expected, actual);

        Set<String> all = new TreeSet<>();
        expected.values().forEach(names -> names.forEach(name -> assertTrue(all.add(name), "in two files: " + name)));
    }

    @Test
    void listHoldsEveryKcwardenCheck() throws IOException {
        List<Check> checks = checks();
        assertEquals(61, checks.size(), "53 auditors and 8 monitors");
        assertEquals(8, checks.stream().filter(c -> c.file().equals("role-guardrails.md")).count(), "monitors");
        assertEquals(30, checks.stream().filter(c -> c.file().equals("clients.md")).count(), "client auditors");
        assertEquals(List.of("AdminEventsStoredWithoutExpiration", "UserEventsStoredWithoutExpiration"),
                checks.stream().filter(Check::fromMain).map(Check::name).sorted().toList());
    }

    @Test
    void notVisibleMarksMatchTheList() throws IOException {
        Map<String, Map<String, String>> sections = sectionsByFile();
        Set<String> expected = new TreeSet<>();
        Set<String> fromMainExpected = new TreeSet<>();
        for (Check check : checks()) {
            if (check.notVisible()) {
                expected.add(check.name());
            }
            if (check.fromMain()) {
                fromMainExpected.add(check.name());
            }
        }
        Set<String> marked = new TreeSet<>();
        Set<String> labelledFromMain = new TreeSet<>();
        sections.values().forEach(byHeading -> byHeading.forEach((name, body) -> {
            if (body.contains(NOT_VISIBLE)) {
                marked.add(name);
            }
            if (body.toLowerCase(Locale.ROOT).contains("from kcwarden main, after v0.19.0")) {
                labelledFromMain.add(name);
            }
        }));
        assertEquals(expected, marked);
        assertEquals(fromMainExpected, labelledFromMain);
    }

    @Test
    void securityAuditNamesOnlyReadTools() throws IOException {
        List<String> writes = new ArrayList<>();
        try (var files = Files.walk(SKILL)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Matcher word = BACKTICKED_CAMEL_CASE.matcher(Files.readString(file));
                while (word.find()) {
                    if (!word.group(1).matches("(get|list|search)[A-Z].*")) {
                        writes.add(SKILL.relativize(file) + ": " + word.group(1));
                    }
                }
            }
        }
        assertEquals(List.of(), writes);
    }
}
