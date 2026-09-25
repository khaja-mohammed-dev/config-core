package io.github.khajamohammeddev.configcore.api;

import java.util.Optional;

/**
 * Writes config values to the backing store, recording each change in the {@link ConfigHistory}.
 * Writers never touch caches directly: every running instance, including this one, picks the change
 * up through its {@link ConfigChangeSource}.
 */
public interface ConfigWriter {

    /**
     * Creates the key, or replaces its value if it already exists. The new value and its history
     * entry are stored together or not at all.
     *
     * @return the recorded history entry, or empty if the key already had this value (nothing is
     *     written and no version is used up)
     */
    Optional<ConfigHistoryEntry> write(ConfigUpdate update);
}
