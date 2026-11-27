package io.github.filippobuletto.keycloakmcp.transport;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.vertx.ext.web.Router;

/**
 * Points the {@code resource_metadata} of every bearer challenge at the metadata for {@code MCP_PUBLIC_URL}. Quarkus
 * OIDC builds that URL from the request host with a forced https scheme, which is wrong for a plain-http server or a
 * proxy whose public host differs from the internal one.
 */
@ApplicationScoped
class ResourceMetadataChallenge {

    private static final Pattern RESOURCE_METADATA = Pattern.compile("resource_metadata=\"[^\"]*\"");
    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    private static final String WELL_KNOWN = "/.well-known/oauth-protected-resource";

    private final Optional<String> metadataUrl;

    ResourceMetadataChallenge(@ConfigProperty(name = "MCP_PUBLIC_URL") Optional<URI> publicUrl) {
        this.metadataUrl = publicUrl.map(ResourceMetadataChallenge::metadataUrl);
    }

    void register(@Observes Router router) {
        metadataUrl.ifPresent(url -> router.route().order(Integer.MIN_VALUE).handler(context -> {
            context.addHeadersEndHandler(ignored -> {
                String challenge = context.response().headers().get(WWW_AUTHENTICATE);
                if (challenge != null) {
                    context.response().headers().set(WWW_AUTHENTICATE, rewrite(challenge, url));
                }
            });
            context.next();
        }));
    }

    /** RFC 9728 metadata URL: the well-known segment goes between the authority and the resource path. */
    static String metadataUrl(URI resource) {
        String path = resource.getRawPath() == null || "/".equals(resource.getRawPath()) ? "" : resource.getRawPath();
        return resource.getScheme() + "://" + resource.getRawAuthority() + WELL_KNOWN + path;
    }

    static String rewrite(String challenge, String url) {
        return RESOURCE_METADATA.matcher(challenge)
                .replaceFirst(Matcher.quoteReplacement("resource_metadata=\"" + url + "\""));
    }
}
