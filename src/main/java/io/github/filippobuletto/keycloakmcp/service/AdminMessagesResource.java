package io.github.filippobuletto.keycloakmcp.service;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * The admin console's message bundle, a public endpoint the admin client has no resource for. Each entry has
 * {@code key} and {@code value}; it resolves the message keys Keycloak sends as mapper property labels.
 */
@Path("/resources/{realm}/admin/{locale}")
public interface AdminMessagesResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<Map<String, String>> messages(@PathParam("realm") String realm, @PathParam("locale") String locale);
}
