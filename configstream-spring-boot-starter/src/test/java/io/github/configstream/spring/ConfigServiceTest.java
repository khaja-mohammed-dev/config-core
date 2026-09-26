package io.github.configstream.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.configstream.api.ConfigCache;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigServiceTest {

    private final ConfigCache cache = new ConfigCache();
    private final ConfigService service = new ConfigService(cache);

    @Test
    void getWithDefault() {
        cache.onSnapshot(Map.of("a", "1"));

        assertThat(service.get("a", "d")).isEqualTo("1");
        assertThat(service.get("missing", "d")).isEqualTo("d");
    }

    @Test
    void getBooleanParsesStrictly() {
        cache.onSnapshot(Map.of("on", " TRUE ", "off", "false", "typo", "ture"));

        assertThat(service.getBoolean("on", false)).isTrue();
        assertThat(service.getBoolean("off", true)).isFalse();
        assertThat(service.getBoolean("typo", false)).isFalse();
        assertThat(service.getBoolean("typo", true)).isTrue();
        assertThat(service.getBoolean("missing", true)).isTrue();
    }
}
