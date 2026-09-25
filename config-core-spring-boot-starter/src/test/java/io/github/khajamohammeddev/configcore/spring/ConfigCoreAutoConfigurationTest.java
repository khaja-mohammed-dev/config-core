package io.github.khajamohammeddev.configcore.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.khajamohammeddev.configcore.api.ConfigChange;
import io.github.khajamohammeddev.configcore.api.ConfigChangeListener;
import io.github.khajamohammeddev.configcore.api.ConfigChangeSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

class ConfigCoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigCoreAutoConfiguration.class));

    @Test
    void failsFastWhenUriIsMissing() {
        runner.run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("config-core.mongo.uri is not set"));
    }

    @Test
    void failsFastWhenDatabaseIsMissing() {
        runner.withPropertyValues("config-core.mongo.uri=mongodb://localhost:27017/?replicaSet=rs0")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("No config database"));
    }

    @Test
    void canBeDisabled() {
        runner.withPropertyValues("config-core.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ConfigService.class));
    }

    @Test
    void customSourceReplacesMongo() {
        runner.withUserConfiguration(FakeSourceConfig.class).run(context -> {
            assertThat(context).hasNotFailed()
                    .doesNotHaveBean(ConfigCoreAutoConfiguration.ConfigCoreMongoClient.class);
            assertThat(context.getBean(ConfigService.class).getAll()).isEqualTo(Map.of("a", "1"));
        });
    }

    @Test
    void publishesEventsForLaterChangesOnly() {
        runner.withUserConfiguration(FakeSourceConfig.class).run(context -> {
            FakeSource source = context.getBean(FakeSource.class);
            ConfigService config = context.getBean(ConfigService.class);
            List<ConfigChangedEvent> events = context.getBean(EventCollector.class).events;
            assertThat(events).as("startup load publishes nothing").isEmpty();

            source.listener.onChange(ConfigChange.upsert("a", "2"));
            source.listener.onChange(ConfigChange.upsert("a", "2")); // duplicate: no event
            source.listener.onChange(ConfigChange.upsert("b", "x"));
            source.listener.onChange(ConfigChange.delete("b"));

            assertThat(config.get("a")).contains("2");
            assertThat(config.get("b")).isEmpty();
            assertThat(events).containsExactly(
                    new ConfigChangedEvent("a", "1", "2"),
                    new ConfigChangedEvent("b", null, "x"),
                    new ConfigChangedEvent("b", "x", null));
        });
    }

    @Test
    void resyncSnapshotPublishesOnlyTheDifferences() {
        runner.withUserConfiguration(FakeSourceConfig.class).run(context -> {
            FakeSource source = context.getBean(FakeSource.class);
            List<ConfigChangedEvent> events = context.getBean(EventCollector.class).events;
            source.listener.onChange(ConfigChange.upsert("keep", "same"));
            events.clear();

            source.listener.onSnapshot(Map.of("a", "changed", "keep", "same", "new", "n"));

            assertThat(events).containsExactlyInAnyOrder(
                    new ConfigChangedEvent("a", "1", "changed"),
                    new ConfigChangedEvent("new", null, "n"));
        });
    }

    @Test
    void stopsSourceOnShutdown() {
        FakeSource[] captured = new FakeSource[1];
        runner.withUserConfiguration(FakeSourceConfig.class)
                .run(context -> captured[0] = context.getBean(FakeSource.class));

        assertThat(captured[0].stopped).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    static class FakeSourceConfig {
        @Bean
        FakeSource fakeSource() {
            return new FakeSource(Map.of("a", "1"));
        }

        @Bean
        EventCollector eventCollector() {
            return new EventCollector();
        }
    }

    static class EventCollector {
        final List<ConfigChangedEvent> events = new CopyOnWriteArrayList<>(); // written by the change-stream thread

        @EventListener
        void on(ConfigChangedEvent event) {
            events.add(event);
        }
    }

    /** Delivers a fixed snapshot on start; tests then drive changes through {@link #listener}. */
    static class FakeSource implements ConfigChangeSource {
        private final Map<String, String> initial;
        ConfigChangeListener listener;
        boolean stopped;

        FakeSource(Map<String, String> initial) {
            this.initial = initial;
        }

        @Override
        public Map<String, String> loadInitial() {
            return initial;
        }

        @Override
        public void start(ConfigChangeListener listener) {
            this.listener = listener;
            listener.onSnapshot(initial);
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }
}
