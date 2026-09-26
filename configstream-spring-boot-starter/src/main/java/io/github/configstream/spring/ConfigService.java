package io.github.configstream.spring;

import io.github.configstream.api.ConfigCache;
import java.util.Map;
import java.util.Optional;

/**
 * Read access to live config values. Inject it anywhere in the application; values are served from
 * memory and reflect changes in the store within about a second, with no restart.
 */
public class ConfigService {

    private final ConfigCache cache;

    public ConfigService(ConfigCache cache) {
        this.cache = cache;
    }

    public Optional<String> get(String key) {
        return cache.get(key);
    }

    public String get(String key, String defaultValue) {
        return cache.get(key).orElse(defaultValue);
    }

    /**
     * Reads a flag. Returns {@code defaultValue} if the key is missing or its value is anything other
     * than {@code true} or {@code false} (case-insensitive), so a typo in the store can't flip a flag.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return cache.get(key)
                .map(String::trim)
                .map(v -> v.equalsIgnoreCase("true") ? Boolean.TRUE
                        : v.equalsIgnoreCase("false") ? Boolean.FALSE
                        : null)
                .orElse(defaultValue);
    }

    /** Immutable point-in-time copy of every config entry. */
    public Map<String, String> getAll() {
        return cache.getAll();
    }
}
