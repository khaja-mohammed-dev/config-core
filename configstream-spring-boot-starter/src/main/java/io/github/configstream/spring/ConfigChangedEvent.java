package io.github.configstream.spring;

import java.util.Objects;

/**
 * Published as a Spring application event whenever a config value changes after startup.
 * Listen with {@code @EventListener}:
 *
 * <pre>{@code
 * @EventListener
 * void onConfigChanged(ConfigChangedEvent event) {
 *     if (event.key().equals("feature.x.enabled")) { ... }
 * }
 * }</pre>
 *
 * <p>Listeners run on configstream's change-stream thread, so a slow listener delays later updates.
 * Hand long-running work off to another thread (for example with {@code @Async}).
 *
 * @param key      the config key that changed
 * @param oldValue the previous value, or {@code null} if the key was just added
 * @param newValue the new value, or {@code null} if the key was deleted
 */
public record ConfigChangedEvent(String key, String oldValue, String newValue) {

    public ConfigChangedEvent {
        Objects.requireNonNull(key, "key");
        if (Objects.equals(oldValue, newValue)) {
            throw new IllegalArgumentException("oldValue and newValue are equal; nothing changed");
        }
    }

    public boolean isAdded() {
        return oldValue == null;
    }

    public boolean isDeleted() {
        return newValue == null;
    }
}
