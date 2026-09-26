package io.github.khajamohammeddev.configcore.api;

import java.util.Objects;

/**
 * A request to delete one config key, carrying who asked for it so the deletion is auditable.
 *
 * <p>Deleting is soft: the key disappears from every cache, but its history stays, and writing the key
 * again (for example, restoring the last value) continues its version numbering.
 *
 * @param key       the config key
 * @param changedBy the authenticated identity making the change
 * @param comment   optional free-text reason; may be {@code null}
 */
public record ConfigDeletion(String key, String changedBy, String comment) {

    public ConfigDeletion {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(changedBy, "changedBy");
    }
}
