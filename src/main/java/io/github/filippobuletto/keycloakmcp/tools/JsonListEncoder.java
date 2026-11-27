package io.github.filippobuletto.keycloakmcp.tools;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.ToolResponseEncoder;

/**
 * Encodes a list returned by a tool as one JSON array, so an empty result reads as {@code []} instead of a response
 * with no content.
 */
@Singleton
@Priority(1)
public class JsonListEncoder implements ToolResponseEncoder<Object> {

    @Inject
    ObjectMapper mapper;

    @Override
    public boolean supports(Class<?> runtimeType) {
        return List.class.isAssignableFrom(runtimeType);
    }

    @Override
    public ToolResponse encode(Object value) {
        try {
            return ToolResponse.success(new TextContent(mapper.writeValueAsString(value)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot encode tool result as JSON", e);
        }
    }
}
