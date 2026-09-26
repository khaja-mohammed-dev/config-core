package io.github.configstream.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigCacheTest {

    private final ConfigCache cache = new ConfigCache();

    @Test
    void snapshotPopulatesCache() {
        cache.onSnapshot(Map.of("a", "1", "b", "2"));

        assertThat(cache.get("a")).contains("1");
        assertThat(cache.getAll()).isEqualTo(Map.of("a", "1", "b", "2"));
    }

    @Test
    void laterSnapshotReplacesEverything() {
        cache.onSnapshot(Map.of("a", "1", "b", "2"));
        cache.onSnapshot(Map.of("b", "20", "c", "3"));

        assertThat(cache.getAll()).isEqualTo(Map.of("b", "20", "c", "3"));
    }

    @Test
    void upsertAndDelete() {
        cache.onChange(ConfigChange.upsert("a", "1"));
        cache.onChange(ConfigChange.upsert("a", "2"));
        assertThat(cache.get("a")).contains("2");

        cache.onChange(ConfigChange.delete("a"));
        assertThat(cache.get("a")).isEmpty();
    }

    @Test
    void deletingUnknownKeyIsNoOp() {
        cache.onChange(ConfigChange.delete("missing"));

        assertThat(cache.getAll()).isEmpty();
    }

    @Test
    void getAllReturnsImmutableCopy() {
        cache.onChange(ConfigChange.upsert("a", "1"));
        Map<String, String> copy = cache.getAll();
        cache.onChange(ConfigChange.upsert("b", "2"));

        assertThat(copy).containsOnlyKeys("a");
        assertThatThrownBy(() -> copy.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void changeValidatesValue() {
        assertThatThrownBy(() -> new ConfigChange(ConfigChange.Type.UPSERT, "a", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConfigChange(ConfigChange.Type.DELETE, "a", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
