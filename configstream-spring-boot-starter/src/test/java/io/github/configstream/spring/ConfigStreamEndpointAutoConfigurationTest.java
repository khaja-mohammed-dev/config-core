package io.github.configstream.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.configstream.api.ConfigDeletion;
import io.github.configstream.api.ConfigHistory;
import io.github.configstream.api.ConfigHistoryEntry;
import io.github.configstream.api.ConfigUpdate;
import io.github.configstream.api.ConfigWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class ConfigStreamEndpointAutoConfigurationTest {

    private static final String SECRET = "0123456789abcdef-test-secret";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigStreamAutoConfiguration.class, ConfigStreamEndpointAutoConfiguration.class,
                    WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                    JacksonAutoConfiguration.class))
            .withUserConfiguration(ConfigStreamAutoConfigurationTest.FakeSourceConfig.class, FakeWriterConfig.class);

    @Test
    void offUnlessSecretIsSet() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(InternalConfigController.class));
    }

    @Test
    void offInNonWebApps() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigStreamAutoConfiguration.class, ConfigStreamEndpointAutoConfiguration.class))
                .withUserConfiguration(ConfigStreamAutoConfigurationTest.FakeSourceConfig.class, FakeWriterConfig.class)
                .withPropertyValues("configstream.internal.secret=" + SECRET)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(InternalConfigController.class));
    }

    @Test
    void offWithoutAWriter() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigStreamAutoConfiguration.class, ConfigStreamEndpointAutoConfiguration.class))
                .withUserConfiguration(ConfigStreamAutoConfigurationTest.FakeSourceConfig.class)
                .withPropertyValues("configstream.internal.secret=" + SECRET)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(InternalConfigController.class));
    }

    @Test
    void rejectsShortSecret() {
        runner.withPropertyValues("configstream.internal.secret=short")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("at least 16 characters"));
    }

    @Test
    void requiresTheSecretHeader() {
        runWithEndpoint((mvc, writer) -> {
            String body = "{\"key\":\"a\",\"value\":\"1\",\"changedBy\":\"alice\"}";
            expect(mvc, update(body), status().isUnauthorized());
            expect(mvc, update(body).header(InternalConfigController.SECRET_HEADER, SECRET + "x"),
                    status().isUnauthorized());
            expect(mvc, get("/internal/config/history").param("key", "a"), status().isUnauthorized());
            assertThat(writer.written).isEmpty();
        });
    }

    @Test
    void writesWithValidSecretAndReturnsTheHistoryEntry() {
        runWithEndpoint((mvc, writer) -> {
            mvc.perform(authorized(update(
                            "{\"key\":\"feature.x.enabled\",\"value\":\"true\",\"changedBy\":\"alice\",\"comment\":\"launch\"}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.key").value("feature.x.enabled"))
                    .andExpect(jsonPath("$.version").value(1))
                    .andExpect(jsonPath("$.oldValue").doesNotExist())
                    .andExpect(jsonPath("$.newValue").value("true"))
                    .andExpect(jsonPath("$.changedBy").value("alice"))
                    .andExpect(jsonPath("$.changedAt").value("2026-09-25T10:00:00Z"))
                    .andExpect(jsonPath("$.comment").value("launch"));
            assertThat(writer.written).isEqualTo(Map.of("feature.x.enabled", "true"));
        });
    }

    @Test
    void unchangedValueReturnsNoContent() {
        runWithEndpoint((mvc, writer) -> {
            String body = "{\"key\":\"a\",\"value\":\"1\",\"changedBy\":\"alice\"}";
            expect(mvc, authorized(update(body)), status().isOk());
            expect(mvc, authorized(update(body)), status().isNoContent());
        });
    }

    @Test
    void rejectsIncompleteRequests() {
        runWithEndpoint((mvc, writer) -> {
            expect(mvc, authorized(update("{\"key\":\"a\",\"changedBy\":\"alice\"}")), status().isBadRequest());
            expect(mvc, authorized(update("{\"key\":\" \",\"value\":\"1\",\"changedBy\":\"alice\"}")),
                    status().isBadRequest());
            expect(mvc, authorized(update("{\"key\":\"a\",\"value\":\"1\"}")), status().isBadRequest());
            expect(mvc, authorized(update("{\"key\":\"a\",\"value\":\"1\",\"changedBy\":\" \"}")),
                    status().isBadRequest());
            expect(mvc, authorized(post("/internal/config/update")), status().isBadRequest());
            assertThat(writer.written).isEmpty();
        });
    }

    @Test
    void deletesWithValidSecretAndReturnsTheHistoryEntry() {
        runWithEndpoint((mvc, writer) -> {
            writer.write(new ConfigUpdate("a", "1", "alice", null));
            String body = "{\"key\":\"a\",\"changedBy\":\"bob\",\"comment\":\"retired\"}";

            expect(mvc, delete(body), status().isUnauthorized());
            mvc.perform(authorized(delete(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.key").value("a"))
                    .andExpect(jsonPath("$.version").value(2))
                    .andExpect(jsonPath("$.oldValue").value("1"))
                    .andExpect(jsonPath("$.newValue").doesNotExist())
                    .andExpect(jsonPath("$.changedBy").value("bob"))
                    .andExpect(jsonPath("$.comment").value("retired"));
            assertThat(writer.written).isEmpty();

            expect(mvc, authorized(delete(body)), status().isNoContent()); // already deleted
        });
    }

    @Test
    void rejectsIncompleteDeletes() {
        runWithEndpoint((mvc, writer) -> {
            writer.write(new ConfigUpdate("a", "1", "alice", null));
            expect(mvc, authorized(delete("{\"key\":\"a\"}")), status().isBadRequest());
            expect(mvc, authorized(delete("{\"key\":\" \",\"changedBy\":\"alice\"}")), status().isBadRequest());
            expect(mvc, authorized(post("/internal/config/delete")), status().isBadRequest());
            assertThat(writer.written).containsKey("a");
        });
    }

    @Test
    void historyReturnsNewestFirstWithLimit() {
        runWithEndpoint((mvc, writer) -> {
            for (int i = 1; i <= 3; i++) {
                writer.write(new ConfigUpdate("a", "v" + i, "alice", null));
            }
            writer.write(new ConfigUpdate("b", "x", "bob", null));

            mvc.perform(authorized(get("/internal/config/history").param("key", "a")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].version").value(3))
                    .andExpect(jsonPath("$[0].oldValue").value("v2"));
            mvc.perform(authorized(get("/internal/config/history").param("key", "a").param("limit", "1")))
                    .andExpect(jsonPath("$.length()").value(1));
            mvc.perform(authorized(get("/internal/config/history").param("key", "missing")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        });
    }

    @Test
    void currentReturnsThisInstancesValuesSorted() {
        runWithEndpoint((mvc, writer) -> {
            expect(mvc, get("/internal/config"), status().isUnauthorized());
            mvc.perform(authorized(get("/internal/config")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.a").value("1")); // FakeSource's snapshot
        });
    }

    @Test
    void historyValidatesParameters() {
        runWithEndpoint((mvc, writer) -> {
            expect(mvc, authorized(get("/internal/config/history")), status().isBadRequest());
            expect(mvc, authorized(get("/internal/config/history").param("key", "a").param("limit", "0")),
                    status().isBadRequest());
        });
    }

    private void runWithEndpoint(EndpointTest test) {
        runner.withPropertyValues("configstream.internal.secret=" + SECRET).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(InternalConfigController.class);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context.getSourceApplicationContext()).build();
            test.run(mvc, context.getBean(FakeWriter.class));
        });
    }

    private static MockHttpServletRequestBuilder update(String json) {
        return post("/internal/config/update").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static MockHttpServletRequestBuilder delete(String json) {
        return post("/internal/config/delete").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header(InternalConfigController.SECRET_HEADER, SECRET);
    }

    private static void expect(MockMvc mvc, MockHttpServletRequestBuilder request, ResultMatcher matcher) throws Exception {
        mvc.perform(request).andExpect(matcher);
    }

    interface EndpointTest {
        void run(MockMvc mvc, FakeWriter writer) throws Exception;
    }

    @Configuration(proxyBeanMethods = false)
    static class FakeWriterConfig {
        @Bean
        FakeWriter fakeWriter() {
            return new FakeWriter();
        }
    }

    /** In-memory writer + history, versioning the same way the Mongo one does. */
    static class FakeWriter implements ConfigWriter, ConfigHistory {
        final Map<String, String> written = new ConcurrentHashMap<>();
        final List<ConfigHistoryEntry> entries = new CopyOnWriteArrayList<>();

        @Override
        public synchronized Optional<ConfigHistoryEntry> write(ConfigUpdate update) {
            String old = written.put(update.key(), update.value());
            if (update.value().equals(old)) {
                return Optional.empty();
            }
            return Optional.of(record(update.key(), old, update.value(), update.changedBy(), update.comment()));
        }

        @Override
        public synchronized Optional<ConfigHistoryEntry> delete(ConfigDeletion deletion) {
            String old = written.remove(deletion.key());
            if (old == null) {
                return Optional.empty();
            }
            return Optional.of(record(deletion.key(), old, null, deletion.changedBy(), deletion.comment()));
        }

        private ConfigHistoryEntry record(String key, String oldValue, String newValue, String changedBy, String comment) {
            long version = entries.stream().filter(e -> e.key().equals(key)).count() + 1;
            ConfigHistoryEntry entry = new ConfigHistoryEntry(key, version, oldValue, newValue, changedBy,
                    Instant.parse("2026-09-25T10:00:00Z"), comment);
            entries.add(0, entry);
            return entry;
        }

        @Override
        public List<ConfigHistoryEntry> history(String key, int limit) {
            return entries.stream().filter(e -> e.key().equals(key)).limit(limit).toList();
        }
    }
}
