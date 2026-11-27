package io.github.filippobuletto.keycloakmcp.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.AuthenticationManagementResource;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

import io.github.filippobuletto.keycloakmcp.model.AuthenticationFlowDetail;
import io.github.filippobuletto.keycloakmcp.model.AuthenticationFlowSummary;
import io.github.filippobuletto.keycloakmcp.model.AuthenticatorType;
import io.github.filippobuletto.keycloakmcp.model.ClientFlowBinding;
import io.github.filippobuletto.keycloakmcp.model.FlowType;
import io.github.filippobuletto.keycloakmcp.model.MoveDirection;
import io.github.filippobuletto.keycloakmcp.model.ProtocolMapperType;
import io.github.filippobuletto.keycloakmcp.model.RealmFlowSlot;
import io.github.filippobuletto.keycloakmcp.model.Requirement;
import io.github.filippobuletto.keycloakmcp.model.SubFlowType;
import io.quarkiverse.mcp.server.ToolCallException;

@Singleton
public class AuthenticationFlowService {

    @Inject
    @Caller
    Keycloak keycloak;

    @Inject
    ClientService clients;

    private AuthenticationManagementResource flows(String realm) {
        return keycloak.realm(realm).flows();
    }

    public List<AuthenticationFlowSummary> list(String realm) {
        Map<String, List<String>> uses = uses(realm);
        return topLevel(realm).stream()
                .map(flow -> summary(flow, uses))
                .toList();
    }

    public AuthenticationFlowDetail get(String realm, String alias) {
        AuthenticationFlowSummary flow = summary(find(realm, alias), uses(realm));
        List<AuthenticationFlowDetail.Execution> executions = executions(realm, alias).stream()
                .map(execution -> execution(realm, execution))
                .toList();
        return new AuthenticationFlowDetail(flow.alias(), flow.description(), flow.type(), flow.builtIn(),
                flow.usedBy(), executions);
    }

    public AuthenticationFlowDetail create(String realm, String alias, String description, FlowType type) {
        AuthenticationFlowRepresentation flow = new AuthenticationFlowRepresentation();
        flow.setAlias(alias);
        flow.setDescription(description);
        flow.setProviderId(type.providerId());
        flow.setTopLevel(true);
        KeycloakErrors.checked(() -> flows(realm).createFlow(flow));
        return get(realm, alias);
    }

    public AuthenticationFlowDetail copy(String realm, String alias, String newAlias) {
        find(realm, alias);
        KeycloakErrors.checked(() -> flows(realm).copy(alias, Map.of("newName", newAlias)));
        return get(realm, newAlias);
    }

    /**
     * Keycloak 26.8.2 answers the delete of a flow in use with a 500 ({@code ModelException}), so the users are
     * checked here and named.
     */
    public void delete(String realm, String alias) {
        AuthenticationFlowRepresentation flow = find(realm, alias);
        if (flow.isBuiltIn()) {
            throw new ToolCallException("Authentication flow " + alias + " is built in and cannot be deleted."
                    + " Nothing was changed.");
        }
        List<String> users = uses(realm).getOrDefault(alias, List.of());
        if (!users.isEmpty()) {
            throw new ToolCallException("Authentication flow " + alias + " is in use by " + String.join(", ", users)
                    + ". Bind another flow there first. Nothing was changed.");
        }
        KeycloakErrors.run(() -> flows(realm).deleteFlow(flow.getId()));
    }

    /** Adds an authenticator step, disabled, at the end of the flow or of its sub-flow {@code parent}. */
    public AuthenticationFlowDetail addExecution(String realm, String flowAlias, String parent, String providerId) {
        String target = target(realm, flowAlias, parent);
        KeycloakErrors.run(() -> flows(realm).addExecution(target, Map.of("provider", providerId)));
        return get(realm, flowAlias);
    }

    /** Adds a sub-flow, disabled, at the end of the flow or of its sub-flow {@code parent}. */
    public AuthenticationFlowDetail addSubFlow(String realm, String flowAlias, String parent, String alias,
            String description, SubFlowType type, String formProviderId) {
        if (type == SubFlowType.FORM) {
            if (formProviderId == null) {
                throw new ToolCallException("A FORM sub-flow needs formProviderId, a type of kind form such as"
                        + " registration-page-form. Nothing was changed.");
            }
            // Keycloak 26.8.2 stores any provider id on a form sub-flow
            if (types(realm, null).stream()
                    .noneMatch(t -> t.id().equals(formProviderId) && t.kind().equals("form"))) {
                throw new ToolCallException(formProviderId + " is not a form type; listAuthenticatorTypes shows the"
                        + " types of kind form. Nothing was changed.");
            }
        }
        String target = target(realm, flowAlias, parent);
        Map<String, Object> data = new HashMap<>();
        data.put("alias", alias);
        data.put("type", type.providerId());
        data.put("description", description);
        if (type == SubFlowType.FORM) {
            data.put("provider", formProviderId);
        }
        KeycloakErrors.run(() -> flows(realm).addExecutionFlow(target, data));
        return get(realm, flowAlias);
    }

    /** Keycloak 26.8.2 stores a requirement the step does not allow, so it is checked here. */
    public AuthenticationFlowDetail updateExecution(String realm, String flowAlias, String executionId,
            Requirement requirement) {
        List<AuthenticationExecutionInfoRepresentation> executions = editableExecutions(realm, flowAlias);
        AuthenticationExecutionInfoRepresentation execution = executions
                .get(indexOf(executions, realm, flowAlias, executionId));
        List<String> allowed = execution.getRequirementChoices() == null ? List.of()
                : execution.getRequirementChoices();
        if (!allowed.contains(requirement.name())) {
            throw new ToolCallException("Execution " + execution.getDisplayName() + " allows only "
                    + String.join(", ", allowed) + "; " + requirement + " is refused. Nothing was changed.");
        }
        execution.setRequirement(requirement.name());
        KeycloakErrors.run(() -> flows(realm).updateExecutions(flowAlias, execution));
        return get(realm, flowAlias);
    }

    /** Keycloak 26.8.2 ignores a move past the first or last sibling, so the edges are refused here. */
    public AuthenticationFlowDetail moveExecution(String realm, String flowAlias, String executionId,
            MoveDirection direction) {
        List<AuthenticationExecutionInfoRepresentation> executions = editableExecutions(realm, flowAlias);
        int index = indexOf(executions, realm, flowAlias, executionId);
        AuthenticationExecutionInfoRepresentation execution = executions.get(index);
        if (!hasSibling(executions, index, direction == MoveDirection.UP ? -1 : 1)) {
            throw new ToolCallException("Execution " + execution.getDisplayName() + " is already the "
                    + (direction == MoveDirection.UP ? "first" : "last") + " of its level. Nothing was changed.");
        }
        KeycloakErrors.run(() -> {
            if (direction == MoveDirection.UP) {
                flows(realm).raisePriority(executionId);
            } else {
                flows(realm).lowerPriority(executionId);
            }
        });
        return get(realm, flowAlias);
    }

    /**
     * The execution's display name, after the checks a write runs: the flow exists, is not built in, and holds the
     * execution. The dialog of a delete names the step with it.
     */
    public String executionName(String realm, String flowAlias, String executionId) {
        List<AuthenticationExecutionInfoRepresentation> executions = editableExecutions(realm, flowAlias);
        return executions.get(indexOf(executions, realm, flowAlias, executionId)).getDisplayName();
    }

    /** Removes the execution; a sub-flow goes with its steps. */
    public void deleteExecution(String realm, String flowAlias, String executionId) {
        executionName(realm, flowAlias, executionId);
        KeycloakErrors.run(() -> flows(realm).removeExecution(executionId));
    }

    /**
     * Creates the execution's config, or merges {@code config} into the one it has. Keycloak 26.8.2 stores a config
     * on a step that takes none, undeclared keys, and a second config that replaces the first, so each is checked here.
     */
    public AuthenticationFlowDetail setConfig(String realm, String flowAlias, String executionId, String alias,
            Map<String, String> config) {
        List<AuthenticationExecutionInfoRepresentation> executions = editableExecutions(realm, flowAlias);
        AuthenticationExecutionInfoRepresentation execution = executions
                .get(indexOf(executions, realm, flowAlias, executionId));
        if (!Boolean.TRUE.equals(execution.getConfigurable())) {
            throw new ToolCallException(
                    "Execution " + execution.getDisplayName() + " takes no config. Nothing was changed.");
        }
        MapperConfig.check("authenticator type", execution.getProviderId(), KeycloakErrors
                .call(() -> flows(realm).getAuthenticatorConfigDescription(execution.getProviderId())).getProperties(),
                config.keySet());
        if (execution.getAuthenticationConfig() == null) {
            if (alias == null) {
                throw new ToolCallException("Execution " + execution.getDisplayName()
                        + " has no config yet; give an alias to create one. Nothing was changed.");
            }
            AuthenticatorConfigRepresentation created = new AuthenticatorConfigRepresentation();
            created.setAlias(alias);
            created.setConfig(new HashMap<>(config));
            KeycloakErrors.checked(() -> flows(realm).newExecutionConfig(executionId, created));
        } else {
            AuthenticatorConfigRepresentation stored = KeycloakErrors
                    .call(() -> flows(realm).getAuthenticatorConfig(execution.getAuthenticationConfig()));
            if (alias != null && !alias.equals(stored.getAlias())) {
                throw new ToolCallException("Execution " + execution.getDisplayName() + " already has config "
                        + stored.getAlias() + "; its alias cannot change. Nothing was changed.");
            }
            Map<String, String> merged = stored.getConfig() == null ? new HashMap<>()
                    : new HashMap<>(stored.getConfig());
            merged.putAll(config);
            stored.setConfig(merged);
            KeycloakErrors.run(() -> flows(realm).updateAuthenticatorConfig(stored.getId(), stored));
        }
        return get(realm, flowAlias);
    }

    /** The display name of an execution that has a config, after the checks {@link #executionName} runs. */
    public String configuredExecutionName(String realm, String flowAlias, String executionId) {
        return configured(realm, flowAlias, executionId).getDisplayName();
    }

    public void deleteConfig(String realm, String flowAlias, String executionId) {
        String configId = configured(realm, flowAlias, executionId).getAuthenticationConfig();
        KeycloakErrors.run(() -> flows(realm).removeAuthenticatorConfig(configId));
    }

    private AuthenticationExecutionInfoRepresentation configured(String realm, String flowAlias, String executionId) {
        List<AuthenticationExecutionInfoRepresentation> executions = editableExecutions(realm, flowAlias);
        AuthenticationExecutionInfoRepresentation execution = executions
                .get(indexOf(executions, realm, flowAlias, executionId));
        if (execution.getAuthenticationConfig() == null) {
            throw new ToolCallException(
                    "Execution " + execution.getDisplayName() + " has no config. Nothing was changed.");
        }
        return execution;
    }

    /** Whether a step of the same level follows (step 1) or precedes (step -1) before the parent ends. */
    private static boolean hasSibling(List<AuthenticationExecutionInfoRepresentation> executions, int index,
            int step) {
        int level = executions.get(index).getLevel();
        for (int i = index + step; i >= 0 && i < executions.size(); i += step) {
            int other = executions.get(i).getLevel();
            if (other < level) {
                return false;
            }
            if (other == level) {
                return true;
            }
        }
        return false;
    }

    /** The alias to add into: the flow itself, or its sub-flow {@code parent}. */
    private String target(String realm, String flowAlias, String parent) {
        List<AuthenticationExecutionInfoRepresentation> executions = editableExecutions(realm, flowAlias);
        if (parent == null) {
            return flowAlias;
        }
        boolean found = executions.stream()
                .anyMatch(e -> Boolean.TRUE.equals(e.getAuthenticationFlow()) && parent.equals(e.getDisplayName()));
        if (!found) {
            throw new ToolCallException(
                    "Sub-flow " + parent + " not found in authentication flow " + flowAlias + " of realm " + realm);
        }
        return parent;
    }

    /** The executions of a top-level flow that is not built in. */
    private List<AuthenticationExecutionInfoRepresentation> editableExecutions(String realm, String flowAlias) {
        if (find(realm, flowAlias).isBuiltIn()) {
            throw new ToolCallException("Authentication flow " + flowAlias + " is built in; copy it with"
                    + " copyAuthenticationFlow, change the copy, then bind the copy with bindRealmFlow or"
                    + " setClientFlowOverride. Nothing was changed.");
        }
        return executions(realm, flowAlias);
    }

    /**
     * Keycloak accepts any execution id of the realm under any flow alias, so the id is looked up in the named flow.
     */
    private static int indexOf(List<AuthenticationExecutionInfoRepresentation> executions, String realm,
            String flowAlias, String executionId) {
        for (int i = 0; i < executions.size(); i++) {
            if (executions.get(i).getId().equals(executionId)) {
                return i;
            }
        }
        throw new ToolCallException(
                "Execution " + executionId + " not found in authentication flow " + flowAlias + " of realm " + realm);
    }

    /**
     * Binds a top-level flow to a realm slot and returns the alias it replaces. Keycloak 26.8.2 answers an unknown alias
     * with a 500, so the flow and its type are checked first.
     *
     * @param confirm runs after the checks and before the write; it throws to refuse
     */
    public String bind(String realm, RealmFlowSlot slot, String flowAlias, Runnable confirm) {
        bindable(realm, flowAlias, slot.flowType(), slot.name());
        String previous = slot.alias(KeycloakErrors.call(() -> keycloak.realm(realm).toRepresentation()));
        confirm.run();
        RealmRepresentation update = new RealmRepresentation();
        slot.bind(update, flowAlias);
        KeycloakErrors.run(() -> keycloak.realm(realm).update(update));
        return previous;
    }

    /**
     * Sets a client's flow override to a top-level basic flow, or clears it when {@code flowAlias} is null. Keycloak
     * keeps an override the map leaves out, so clearing sends an empty value.
     *
     * @param confirm runs after the checks and before the write; it throws to refuse
     */
    public void override(String realm, String clientId, ClientFlowBinding binding, String flowAlias,
            Runnable confirm) {
        ClientRepresentation client = clients.find(realm, clientId);
        String flowId = flowAlias == null ? "" : bindable(realm, flowAlias, "basic-flow", binding.name()).getId();
        confirm.run();
        Map<String, String> overrides = client.getAuthenticationFlowBindingOverrides() == null ? new HashMap<>()
                : new HashMap<>(client.getAuthenticationFlowBindingOverrides());
        overrides.put(binding.key(), flowId);
        client.setAuthenticationFlowBindingOverrides(overrides);
        KeycloakErrors.run(() -> keycloak.realm(realm).clients().get(client.getId()).update(client));
    }

    private AuthenticationFlowRepresentation bindable(String realm, String flowAlias, String flowType, String target) {
        AuthenticationFlowRepresentation flow = find(realm, flowAlias);
        if (!flowType.equals(flow.getProviderId())) {
            throw new ToolCallException("Authentication flow " + flowAlias + " is a " + flow.getProviderId() + "; "
                    + target + " takes a " + flowType + ". Nothing was changed.");
        }
        return flow;
    }

    /** Every type of the server, or one type with its config properties when {@code providerId} is given. */
    public List<AuthenticatorType> types(String realm, String providerId) {
        List<AuthenticatorType> types = new ArrayList<>();
        AuthenticationManagementResource flows = flows(realm);
        addTypes(types, "authenticator", () -> flows.getAuthenticatorProviders());
        addTypes(types, "form", () -> flows.getFormProviders());
        addTypes(types, "form-action", () -> flows.getFormActionProviders());
        addTypes(types, "client-authenticator", () -> flows.getClientAuthenticatorProviders());
        types.sort(Comparator.comparing(AuthenticatorType::id));
        if (providerId == null) {
            return types;
        }
        AuthenticatorType type = types.stream()
                .filter(t -> t.id().equals(providerId))
                .findFirst()
                .orElseThrow(() -> new ToolCallException("Authenticator type " + providerId
                        + " not found; listAuthenticatorTypes without providerId lists the types."));
        List<ProtocolMapperType.Property> properties = KeycloakErrors
                .call(() -> flows.getAuthenticatorConfigDescription(providerId)).getProperties().stream()
                .map(p -> new ProtocolMapperType.Property(p.getName(), p.getLabel(), p.getType(), p.isRequired(),
                        p.getDefaultValue(), p.getOptions(), p.getHelpText()))
                .toList();
        return List.of(new AuthenticatorType(type.id(), type.name(), type.description(), type.kind(), properties));
    }

    private static void addTypes(List<AuthenticatorType> types, String kind,
            Supplier<List<Map<String, Object>>> providers) {
        for (Map<String, Object> provider : KeycloakErrors.call(providers)) {
            types.add(new AuthenticatorType((String) provider.get("id"), (String) provider.get("displayName"),
                    (String) provider.get("description"), kind, null));
        }
    }

    /**
     * Who runs each flow, by flow alias, as {@code KeycloakModelUtils.isFlowUsed} checks it: realm slots, client
     * browser and direct grant overrides, identity provider first and post broker login flows.
     */
    Map<String, List<String>> uses(String realm) {
        Map<String, List<String>> uses = new HashMap<>();
        RealmRepresentation realmRep = KeycloakErrors.call(() -> keycloak.realm(realm).toRepresentation());
        for (RealmFlowSlot slot : RealmFlowSlot.values()) {
            use(uses, slot.alias(realmRep), "realm: " + slot);
        }
        Map<String, String> aliasById = KeycloakErrors.call(() -> flows(realm).getFlows()).stream()
                .collect(Collectors.toMap(AuthenticationFlowRepresentation::getId,
                        AuthenticationFlowRepresentation::getAlias));
        List<ClientRepresentation> clients = KeycloakErrors.call(() -> keycloak.realm(realm).clients().findAll());
        for (ClientRepresentation client : clients) {
            Map<String, String> overrides = client.getAuthenticationFlowBindingOverrides();
            if (overrides != null) {
                for (ClientFlowBinding binding : ClientFlowBinding.values()) {
                    use(uses, aliasById.get(overrides.get(binding.key())),
                            "client " + client.getClientId() + ": " + binding);
                }
            }
        }
        List<IdentityProviderRepresentation> providers = KeycloakErrors
                .call(() -> keycloak.realm(realm).identityProviders().findAll());
        for (IdentityProviderRepresentation provider : providers) {
            use(uses, provider.getFirstBrokerLoginFlowAlias(),
                    "identity provider " + provider.getAlias() + ": FIRST_BROKER_LOGIN");
            use(uses, provider.getPostBrokerLoginFlowAlias(),
                    "identity provider " + provider.getAlias() + ": POST_BROKER_LOGIN");
        }
        return uses;
    }

    private static void use(Map<String, List<String>> uses, String alias, String user) {
        if (alias != null && !alias.isBlank()) {
            uses.computeIfAbsent(alias, a -> new ArrayList<>()).add(user);
        }
    }

    /** Keycloak lists only top-level flows here. */
    List<AuthenticationFlowRepresentation> topLevel(String realm) {
        return KeycloakErrors.call(() -> flows(realm).getFlows());
    }

    AuthenticationFlowRepresentation find(String realm, String alias) {
        return topLevel(realm).stream()
                .filter(flow -> flow.getAlias().equals(alias))
                .findFirst()
                .orElseThrow(() -> new ToolCallException(
                        "Authentication flow " + alias + " not found in realm " + realm));
    }

    List<AuthenticationExecutionInfoRepresentation> executions(String realm, String alias) {
        return KeycloakErrors.call(() -> flows(realm).getExecutions(alias));
    }

    private static AuthenticationFlowSummary summary(AuthenticationFlowRepresentation flow,
            Map<String, List<String>> uses) {
        return new AuthenticationFlowSummary(flow.getAlias(), flow.getDescription(), flow.getProviderId(),
                flow.isBuiltIn(), List.copyOf(uses.getOrDefault(flow.getAlias(), List.of())));
    }

    private AuthenticationFlowDetail.Execution execution(String realm, AuthenticationExecutionInfoRepresentation e) {
        boolean subFlow = Boolean.TRUE.equals(e.getAuthenticationFlow());
        AuthenticatorConfigRepresentation config = e.getAuthenticationConfig() == null ? null
                : KeycloakErrors.call(() -> flows(realm).getAuthenticatorConfig(e.getAuthenticationConfig()));
        // Keycloak shows a sub-flow under its alias as the display name
        return new AuthenticationFlowDetail.Execution(e.getId(), e.getLevel(), e.getDisplayName(),
                subFlow ? null : e.getProviderId(), subFlow ? e.getDisplayName() : null, e.getRequirement(),
                e.getRequirementChoices(), Boolean.TRUE.equals(e.getConfigurable()),
                config == null ? null : config.getAlias(),
                config == null ? null : sorted(config.getConfig()));
    }

    private static Map<String, String> sorted(Map<String, String> config) {
        return config == null ? new TreeMap<>() : new TreeMap<>(config);
    }
}
