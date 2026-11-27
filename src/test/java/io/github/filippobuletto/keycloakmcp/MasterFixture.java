package io.github.filippobuletto.keycloakmcp;

import java.io.IOException;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.PartialImportRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.runtime.StartupEvent;

/**
 * Adds the objects of {@code docker/master.json} to {@code master} of Dev Services Keycloak when a test app starts, as
 * {@code docker/seed-master.sh} does for compose: Dev Services skip {@code master} because it already exists. Objects
 * that exist are kept, so every restart can run it.
 */
@ApplicationScoped
public class MasterFixture {

    static final Path FILE = Path.of("docker/master.json");

    void seed(@Observes StartupEvent event, Keycloak keycloak) throws IOException {
        RealmRepresentation fixture = new ObjectMapper().readValue(FILE.toFile(), RealmRepresentation.class);
        RealmResource master = keycloak.realm("master");
        // Partial import skips client scopes, and clients can only reference scopes that exist
        for (ClientScopeRepresentation scope : fixture.getClientScopes()) {
            try (Response response = master.clientScopes().create(scope)) {
                check(response, 201, 409);
            }
        }
        PartialImportRepresentation rest = new PartialImportRepresentation();
        rest.setIfResourceExists("SKIP");
        rest.setRoles(fixture.getRoles());
        rest.setClients(fixture.getClients());
        rest.setUsers(fixture.getUsers());
        try (Response response = master.partialImport(rest)) {
            check(response, 200);
        }
    }

    private static void check(Response response, int... expected) {
        for (int status : expected) {
            if (response.getStatus() == status) {
                return;
            }
        }
        throw new IllegalStateException(
                "Seeding master from " + FILE + " failed: " + response.getStatus() + " " + response.readEntity(String.class));
    }
}
