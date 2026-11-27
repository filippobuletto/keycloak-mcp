package io.github.filippobuletto.keycloakmcp.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.AuthenticationManagementResource;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.ConfigPropertyRepresentation;
import org.keycloak.representations.idm.RequiredActionConfigRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;

import io.github.filippobuletto.keycloakmcp.model.MoveDirection;
import io.github.filippobuletto.keycloakmcp.model.ProtocolMapperType;
import io.github.filippobuletto.keycloakmcp.model.RequiredActionDetail;
import io.github.filippobuletto.keycloakmcp.model.RequiredActionSummary;
import io.quarkiverse.mcp.server.ToolCallException;

@Singleton
public class RequiredActionService {

    @Inject
    @Caller
    Keycloak keycloak;

    @Inject
    AuthenticationFlowService authenticationFlows;

    private AuthenticationManagementResource flows(String realm) {
        return keycloak.realm(realm).flows();
    }

    /** Keycloak returns the actions in the order it runs them. */
    public List<RequiredActionSummary> list(String realm) {
        return all(realm).stream()
                .map(a -> new RequiredActionSummary(a.getAlias(), a.getName(), a.isEnabled(), a.isDefaultAction()))
                .toList();
    }

    public RequiredActionDetail get(String realm, String alias) {
        List<RequiredActionProviderRepresentation> actions = all(realm);
        int index = indexOf(actions, realm, alias);
        RequiredActionProviderRepresentation action = actions.get(index);
        List<ProtocolMapperType.Property> properties = properties(realm, alias).stream()
                .map(p -> new ProtocolMapperType.Property(p.getName(), p.getLabel(), p.getType(), p.isRequired(),
                        p.getDefaultValue(), p.getOptions(), p.getHelpText()))
                .toList();
        return new RequiredActionDetail(action.getAlias(), action.getName(), action.isEnabled(),
                action.isDefaultAction(), action.getProviderId(), index + 1,
                sorted(action.getConfig()), properties);
    }

    /**
     * Sets {@code enabled} and {@code defaultAction} where given. Keycloak 26.8.2 replaces every field on update (a
     * body without alias leaves a row no endpoint can reach), so the stored action is read and sent back whole.
     *
     * @param confirm runs with the consequences, before the write, only when the call disables an enabled action or
     *        turns on a default action that is off; it throws to refuse
     */
    public RequiredActionDetail update(String realm, String alias, Boolean enabled, Boolean defaultAction,
            Consumer<String> confirm) {
        if (enabled == null && defaultAction == null) {
            throw new ToolCallException("Give enabled or defaultAction. Nothing was changed.");
        }
        List<RequiredActionProviderRepresentation> actions = all(realm);
        RequiredActionProviderRepresentation action = actions.get(indexOf(actions, realm, alias));
        List<String> consequences = new ArrayList<>();
        if (Boolean.FALSE.equals(enabled) && action.isEnabled()) {
            consequences.add(disableConsequence(realm, action));
        }
        if (Boolean.TRUE.equals(defaultAction) && !action.isDefaultAction()) {
            consequences.add("Making required action " + alias + " a default action in realm " + realm
                    + " gives it to every new user.");
        }
        if (!consequences.isEmpty()) {
            confirm.accept(String.join(" ", consequences));
        }
        if (enabled != null) {
            action.setEnabled(enabled);
        }
        if (defaultAction != null) {
            action.setDefaultAction(defaultAction);
        }
        KeycloakErrors.run(() -> flows(realm).updateRequiredAction(alias, action));
        return get(realm, alias);
    }

    /** Keycloak 26.8.2 ignores a move past the first or last action, so the ends are refused here. */
    public List<RequiredActionSummary> move(String realm, String alias, MoveDirection direction) {
        List<RequiredActionProviderRepresentation> actions = all(realm);
        int index = indexOf(actions, realm, alias);
        if (direction == MoveDirection.UP ? index == 0 : index == actions.size() - 1) {
            throw new ToolCallException("Required action " + alias + " is already the "
                    + (direction == MoveDirection.UP ? "first" : "last") + ". Nothing was changed.");
        }
        KeycloakErrors.run(() -> {
            if (direction == MoveDirection.UP) {
                flows(realm).raiseRequiredActionPriority(alias);
            } else {
                flows(realm).lowerRequiredActionPriority(alias);
            }
        });
        return list(realm);
    }

    /**
     * Merges {@code config} into the stored config. Keycloak 26.8.2 replaces the whole map and drops undeclared keys
     * without an error, so keys are checked and the stored ones kept here; Keycloak checks the values.
     */
    public RequiredActionDetail setConfig(String realm, String alias, Map<String, String> config) {
        MapperConfig.check("required action", alias, configurable(realm, alias), config.keySet());
        RequiredActionConfigRepresentation stored = KeycloakErrors
                .call(() -> flows(realm).getRequiredActionConfig(alias));
        Map<String, String> merged = stored.getConfig() == null ? new HashMap<>() : new HashMap<>(stored.getConfig());
        merged.putAll(config);
        stored.setConfig(merged);
        KeycloakErrors.run(() -> flows(realm).updateRequiredActionConfig(alias, stored));
        return get(realm, alias);
    }

    public RequiredActionDetail deleteConfig(String realm, String alias) {
        configurable(realm, alias);
        KeycloakErrors.run(() -> flows(realm).removeRequiredActionConfig(alias));
        return get(realm, alias);
    }

    /** The config keys of an action that takes config; refuses an unknown alias or an action that takes none. */
    private List<ConfigPropertyRepresentation> configurable(String realm, String alias) {
        indexOf(all(realm), realm, alias);
        List<ConfigPropertyRepresentation> properties = properties(realm, alias);
        if (properties.isEmpty()) {
            throw new ToolCallException("Required action " + alias + " takes no config. Nothing was changed.");
        }
        return properties;
    }

    private String disableConsequence(String realm, RequiredActionProviderRepresentation action) {
        String consequence = "Disabling required action " + action.getAlias() + " in realm " + realm
                + " stops asking users for it, also users who have it pending.";
        String authenticator = LockoutCheck.AUTHENTICATOR_BY_ACTION.get(action.getProviderId());
        if (authenticator == null) {
            return consequence;
        }
        Map<String, List<String>> uses = authenticationFlows.uses(realm);
        Map<String, List<AuthenticationExecutionInfoRepresentation>> executions = new HashMap<>();
        uses.keySet().forEach(flow -> executions.put(flow, authenticationFlows.executions(realm, flow)));
        List<String> flows = LockoutCheck.requiringFlows(authenticator, executions, uses);
        if (flows.isEmpty()) {
            return consequence;
        }
        return consequence + " These flows in use require its " + authenticator + " step: "
                + String.join("; ", flows) + ". Users without that credential cannot sign in through them.";
    }

    /** The config keys the action takes; empty when Keycloak says it is not configurable ({@code 404}). */
    List<ConfigPropertyRepresentation> properties(String realm, String alias) {
        return KeycloakErrors.call(() -> {
            try {
                List<ConfigPropertyRepresentation> properties = flows(realm)
                        .getRequiredActionConfigDescription(alias).getProperties();
                return properties == null ? List.<ConfigPropertyRepresentation> of() : properties;
            } catch (WebApplicationException e) {
                if (e.getResponse().getStatus() == 404) {
                    return List.<ConfigPropertyRepresentation> of();
                }
                throw e;
            }
        });
    }

    List<RequiredActionProviderRepresentation> all(String realm) {
        return KeycloakErrors.call(() -> flows(realm).getRequiredActions());
    }

    /** Keycloak says only "Failed to find required action", so the alias is looked up here and named. */
    static int indexOf(List<RequiredActionProviderRepresentation> actions, String realm, String alias) {
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).getAlias().equals(alias)) {
                return i;
            }
        }
        throw new ToolCallException("Required action " + alias + " not found in realm " + realm
                + "; listRequiredActions lists the aliases.");
    }

    static Map<String, String> sorted(Map<String, String> config) {
        return config == null ? new TreeMap<>() : new TreeMap<>(config);
    }
}
