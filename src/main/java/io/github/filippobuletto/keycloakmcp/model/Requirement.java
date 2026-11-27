package io.github.filippobuletto.keycloakmcp.model;

/** How a flow step counts; each step allows only some of these (requirementChoices). */
public enum Requirement {
    REQUIRED,
    ALTERNATIVE,
    DISABLED,
    CONDITIONAL
}
