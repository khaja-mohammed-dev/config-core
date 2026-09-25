package io.github.khajamohammeddev.configcore.api;

/**
 * Writes config values to the backing store. Writers never touch caches directly: every running
 * instance, including this one, picks the change up through its {@link ConfigChangeSource}.
 */
public interface ConfigWriter {

    /** Creates the key, or replaces its value if it already exists. */
    void put(String key, String value);
}
