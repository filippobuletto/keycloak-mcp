package io.github.filippobuletto.keycloakmcp.guard;

/** How a destructive tool gets its confirmation, set with {@code MCP_CONFIRM_MODE}. */
public enum ConfirmMode {
    /** The server asks the user through an MCP form elicitation. */
    ELICIT,
    /** The client passes {@code confirm=true}, for clients without elicitation. */
    FLAG
}
