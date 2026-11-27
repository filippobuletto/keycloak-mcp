package io.github.filippobuletto.keycloakmcp.model;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.keycloak.representations.idm.RealmRepresentation;

/** A realm's flow binding; {@code flowType} is the flow type Keycloak runs in it. */
public enum RealmFlowSlot {
    BROWSER(RealmRepresentation::getBrowserFlow, RealmRepresentation::setBrowserFlow,
            "basic-flow"),
    REGISTRATION(RealmRepresentation::getRegistrationFlow, RealmRepresentation::setRegistrationFlow,
            "basic-flow"),
    DIRECT_GRANT(RealmRepresentation::getDirectGrantFlow, RealmRepresentation::setDirectGrantFlow,
            "basic-flow"),
    RESET_CREDENTIALS(RealmRepresentation::getResetCredentialsFlow, RealmRepresentation::setResetCredentialsFlow,
            "basic-flow"),
    CLIENT_AUTHENTICATION(RealmRepresentation::getClientAuthenticationFlow, RealmRepresentation::setClientAuthenticationFlow,
            "client-flow"),
    DOCKER_AUTHENTICATION(RealmRepresentation::getDockerAuthenticationFlow, RealmRepresentation::setDockerAuthenticationFlow,
            "basic-flow"),
    FIRST_BROKER_LOGIN(RealmRepresentation::getFirstBrokerLoginFlow, RealmRepresentation::setFirstBrokerLoginFlow,
            "basic-flow");

    private final Function<RealmRepresentation, String> alias;
    private final BiConsumer<RealmRepresentation, String> bind;
    private final String flowType;

    RealmFlowSlot(Function<RealmRepresentation, String> alias, BiConsumer<RealmRepresentation, String> bind,
            String flowType) {
        this.alias = alias;
        this.bind = bind;
        this.flowType = flowType;
    }

    /** The alias of the flow bound to this slot. */
    public String alias(RealmRepresentation realm) {
        return alias.apply(realm);
    }

    /** Sets this slot's flow alias in {@code realm}. */
    public void bind(RealmRepresentation realm, String flowAlias) {
        bind.accept(realm, flowAlias);
    }

    public String flowType() {
        return flowType;
    }
}
