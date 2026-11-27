package io.github.filippobuletto.keycloakmcp.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.github.filippobuletto.keycloakmcp.McpClients;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.PromptArgument;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class KeycloakPromptsTest {

    /** Prompt texts put tool names, and nothing else, in backticks. */
    static final Pattern BACKTICKED = Pattern.compile("`([^`]+)`");

    static final Map<String, List<String>> ARGUMENTS = Map.of(
            "audit-realm", List.of("realm"),
            "troubleshoot-login", List.of("realm", "username"),
            "setup-client", List.of("realm", "clientId"));

    static final Map<String, List<String>> TOOLS = Map.of(
            "audit-realm", List.of("getRealm", "listClients", "getClient", "listRequiredActions",
                    "listAuthenticationFlows", "listIdentityProviders", "getEventSettings"),
            "troubleshoot-login", List.of("searchUsers", "getUser", "listUserSessions", "listEvents", "getRealm",
                    "listAuthenticationFlows"),
            "setup-client", List.of("listClients", "createClient", "addClientScopeToClient", "addClientScopeMapper",
                    "getClient"));

    @Test
    void promptsListShowsRequiredArguments() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .promptsList(page -> {
                    ARGUMENTS.forEach((name, arguments) -> {
                        var prompt = page.findByName(name);
                        assertEquals(arguments, prompt.arguments().stream().map(PromptArgument::name).toList(), name);
                        assertTrue(prompt.arguments().stream().allMatch(PromptArgument::required), name);
                        assertFalse(prompt.description().isBlank(), name);
                    });
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void promptsFillArgumentsAndNameExistingTools() {
        McpStreamableTestClient client = McpClients.connected();
        Set<String> tools = new HashSet<>();
        client.when()
                .toolsList(page -> page.tools().forEach(tool -> tools.add(tool.name())))
                .thenAssertResults();

        client.when()
                .promptsGet("audit-realm", Map.of("realm", "acme"), response -> {
                    String text = singleUserText(response);
                    assertTrue(text.contains("acme"), text);
                    assertTrue(text.contains("password policy"), text);
                    assertTrue(text.contains("brute-force protection"), text);
                    assertToolsExist("audit-realm", text, tools);
                })
                .promptsGet("troubleshoot-login", Map.of("realm", "acme", "username", "jdoe"), response -> {
                    String text = singleUserText(response);
                    assertTrue(text.contains("acme"), text);
                    assertTrue(text.contains("jdoe"), text);
                    assertToolsExist("troubleshoot-login", text, tools);
                })
                .promptsGet("setup-client", Map.of("realm", "acme", "clientId", "portal"), response -> {
                    String text = singleUserText(response);
                    assertTrue(text.contains("acme"), text);
                    assertTrue(text.contains("portal"), text);
                    assertToolsExist("setup-client", text, tools);
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void promptsCompleteRealmNames() {
        McpStreamableTestClient client = McpClients.connected();

        client.when()
                .promptComplete("audit-realm", "realm", "ac", completion -> {
                    assertEquals(List.of("acme"), completion.values());
                })
                .promptComplete("troubleshoot-login", "realm", "ac", completion -> {
                    assertEquals(List.of("acme"), completion.values());
                })
                .promptComplete("setup-client", "realm", "ac", completion -> {
                    assertEquals(List.of("acme"), completion.values());
                })
                .thenAssertResults();

        client.disconnect();
    }

    static String singleUserText(PromptResponse response) {
        assertEquals(1, response.messages().size(), response.messages().toString());
        assertEquals(Role.USER, response.firstMessage().role());
        String text = response.firstMessage().content().asText().text();
        assertFalse(text.contains("confirm=true"), text);
        return text;
    }

    static void assertToolsExist(String prompt, String text, Set<String> tools) {
        Set<String> named = new HashSet<>();
        Matcher matcher = BACKTICKED.matcher(text);
        while (matcher.find()) {
            named.add(matcher.group(1));
        }
        assertTrue(named.containsAll(TOOLS.get(prompt)), prompt + " names " + named);
        assertTrue(tools.containsAll(named), prompt + " names tools that don't exist: " + named);
    }
}
