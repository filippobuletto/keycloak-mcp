package io.github.filippobuletto.keycloakmcp.model;

import java.util.List;

/** Event settings to change; a null field is left unchanged. */
public record EventSettingsInput(
        Boolean eventsEnabled,
        Long eventsExpiration,
        List<String> eventsListeners,
        List<String> enabledEventTypes,
        Boolean adminEventsEnabled,
        Boolean adminEventsDetailsEnabled) {
}
