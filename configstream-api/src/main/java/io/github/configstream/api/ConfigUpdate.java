package io.github.configstream.api;

import java.util.Objects;

/**
 * A request to set one config value, carrying who asked for it so the change is auditable.
 *
 * <p>Rolling back is just another update: write the historical value again, ideally with a comment
 * such as {@code "Reverted to v3"}.
 *
 * @param key       the config key
 * @param value     the new value
 * @param changedBy the authenticated identity making the change, e.g. a user name from the admin app
 * @param comment   optional free-text reason; may be {@code null}
 */
public record ConfigUpdate(String key, String value, String changedBy, String comment) {

    public ConfigUpdate {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(changedBy, "changedBy");
    }
}
