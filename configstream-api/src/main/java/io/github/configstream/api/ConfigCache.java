package io.github.configstream.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory view of the config store, kept current by a {@link ConfigChangeSource}.
 * Reads never touch the database.
 */
public class ConfigCache implements ConfigChangeListener {

    private final Map<String, String> entries = new ConcurrentHashMap<>();

    public Optional<String> get(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    /** Immutable point-in-time copy of all entries. */
    public Map<String, String> getAll() {
        return Map.copyOf(entries);
    }

    @Override
    public void onSnapshot(Map<String, String> snapshot) {
        // Update in place rather than swapping maps, so readers never see an empty cache mid-reload.
        entries.putAll(snapshot);
        entries.keySet().retainAll(snapshot.keySet());
    }

    @Override
    public void onChange(ConfigChange change) {
        switch (change.type()) {
            case UPSERT -> entries.put(change.key(), change.value());
            case DELETE -> entries.remove(change.key());
        }
    }
}
