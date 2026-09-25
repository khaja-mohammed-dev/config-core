package io.github.khajamohammeddev.configcore.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khajamohammeddev.configcore.api.ConfigWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

class ConfigCoreEndpointAutoConfigurationTest {

    private static final String SECRET = "0123456789abcdef-test-secret";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigCoreAutoConfiguration.class, ConfigCoreEndpointAutoConfiguration.class,
                    WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                    JacksonAutoConfiguration.class))
            .withUserConfiguration(ConfigCoreAutoConfigurationTest.FakeSourceConfig.class, FakeWriterConfig.class);

    @Test
    void offUnlessSecretIsSet() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(InternalConfigController.class));
    }

    @Test
    void offInNonWebApps() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigCoreAutoConfiguration.class, ConfigCoreEndpointAutoConfiguration.class))
                .withUserConfiguration(ConfigCoreAutoConfigurationTest.FakeSourceConfig.class, FakeWriterConfig.class)
                .withPropertyValues("config-core.internal.secret=" + SECRET)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(InternalConfigController.class));
    }

    @Test
    void offWithoutAWriter() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigCoreAutoConfiguration.class, ConfigCoreEndpointAutoConfiguration.class))
                .withUserConfiguration(ConfigCoreAutoConfigurationTest.FakeSourceConfig.class)
                .withPropertyValues("config-core.internal.secret=" + SECRET)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(InternalConfigController.class));
    }

    @Test
    void rejectsShortSecret() {
        runner.withPropertyValues("config-core.internal.secret=short")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("at least 16 characters"));
    }

    @Test
    void requiresTheSecretHeader() {
        runWithEndpoint((mvc, writer) -> {
            expect(mvc, update("{\"key\":\"a\",\"value\":\"1\"}"), status().isUnauthorized());
            expect(mvc, update("{\"key\":\"a\",\"value\":\"1\"}")
                    .header(InternalConfigController.SECRET_HEADER, SECRET + "x"), status().isUnauthorized());
            assertThat(writer.written).isEmpty();
        });
    }

    @Test
    void writesWithValidSecret() {
        runWithEndpoint((mvc, writer) -> {
            expect(mvc, authorized(update("{\"key\":\"feature.x.enabled\",\"value\":\"true\"}")),
                    status().isNoContent());
            assertThat(writer.written).isEqualTo(Map.of("feature.x.enabled", "true"));
        });
    }

    @Test
    void rejectsIncompleteRequests() {
        runWithEndpoint((mvc, writer) -> {
            expect(mvc, authorized(update("{\"key\":\"a\"}")), status().isBadRequest());
            expect(mvc, authorized(update("{\"key\":\" \",\"value\":\"1\"}")), status().isBadRequest());
            expect(mvc, authorized(post("/internal/config/update")), status().isBadRequest());
            assertThat(writer.written).isEmpty();
        });
    }

    private void runWithEndpoint(EndpointTest test) {
        runner.withPropertyValues("config-core.internal.secret=" + SECRET).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(InternalConfigController.class);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context.getSourceApplicationContext()).build();
            test.run(mvc, context.getBean(FakeWriter.class));
        });
    }

    private static MockHttpServletRequestBuilder update(String json) {
        return post("/internal/config/update").contentType(MediaType.APPLICATION_JSON).content(json);
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

    static class FakeWriter implements ConfigWriter {
        final Map<String, String> written = new ConcurrentHashMap<>();

        @Override
        public void put(String key, String value) {
            written.put(key, value);
        }
    }
}
