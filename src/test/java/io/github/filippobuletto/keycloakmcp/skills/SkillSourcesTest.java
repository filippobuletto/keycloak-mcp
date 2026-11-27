package io.github.filippobuletto.keycloakmcp.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.Tool;

/** Checks the skill sources under {@code src/main/resources/skills/}, as committed. */
class SkillSourcesTest {

    static final Path SKILLS = Path.of("src/main/resources/skills");
    static final String TOOLS_PACKAGE = "io.github.filippobuletto.keycloakmcp.tools";
    static final Path TOOLS = Path.of("src/main/java", TOOLS_PACKAGE.split("\\."));
    static final Pattern BACKTICKED_CAMEL_CASE = Pattern.compile("`([a-z]+[A-Z][A-Za-z0-9]*)`");

    static List<String> indexPaths() throws IOException {
        return Files.readAllLines(SKILLS.resolve("index.txt")).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    @Test
    void indexNamesExactlyTheSkillFiles() throws IOException {
        List<String> onDisk;
        try (Stream<Path> files = Files.walk(SKILLS)) {
            onDisk = files.filter(Files::isRegularFile)
                    .map(p -> SKILLS.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> !p.equals("index.txt"))
                    .sorted()
                    .toList();
        }
        assertEquals(onDisk, indexPaths().stream().sorted().toList());
    }

    /** Every {@code @Tool} name in the tools package. */
    static Set<String> toolNames() throws Exception {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> sources = Files.list(TOOLS)) {
            for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String className = TOOLS_PACKAGE + "." + source.getFileName().toString().replace(".java", "");
                for (Method method : Class.forName(className).getDeclaredMethods()) {
                    Tool tool = method.getAnnotation(Tool.class);
                    if (tool != null) {
                        names.add(tool.name().equals(Tool.ELEMENT_NAME) ? method.getName() : tool.name());
                    }
                }
            }
        }
        return names;
    }

    @Test
    void skillsNameOnlyRealToolsAndNeverSkipAConfirmation() throws Exception {
        Set<String> tools = toolNames();
        assertTrue(tools.size() > 80, tools.toString());
        List<String> problems = new ArrayList<>();
        for (String path : indexPaths()) {
            String text = Files.readString(SKILLS.resolve(path));
            Matcher words = BACKTICKED_CAMEL_CASE.matcher(text);
            while (words.find()) {
                if (!tools.contains(words.group(1))) {
                    problems.add(path + ": `" + words.group(1) + "` is not a tool");
                }
            }
            for (String banned : List.of("confirm=true", "allowed-tools")) {
                if (text.contains(banned)) {
                    problems.add(path + ": contains " + banned);
                }
            }
        }
        assertEquals(List.of(), problems);
    }
}
