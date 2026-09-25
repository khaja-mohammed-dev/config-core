package io.github.khajamohammeddev.configcore.spring;

import io.github.khajamohammeddev.configcore.api.ConfigHistory;
import io.github.khajamohammeddev.configcore.api.ConfigWriter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Registers {@code POST /internal/config/update} and {@code GET /internal/config/history}, but only
 * when {@code config-core.internal.secret} is set: the endpoints are off unless explicitly
 * configured, never open by default.
 */
@AutoConfiguration(after = ConfigCoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "config-core", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(prefix = "config-core.internal", name = "secret")
@ConditionalOnBean({ConfigWriter.class, ConfigHistory.class})
@EnableConfigurationProperties(ConfigCoreProperties.class)
public class ConfigCoreEndpointAutoConfiguration {

    static final int MIN_SECRET_LENGTH = 16;

    @Bean
    InternalConfigController configCoreInternalConfigController(
            ConfigWriter writer, ConfigHistory history, ConfigCoreProperties properties) {
        String secret = properties.getInternal().getSecret();
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("config-core.internal.secret must be at least "
                    + MIN_SECRET_LENGTH + " characters; generate one with e.g. `openssl rand -hex 32`.");
        }
        return new InternalConfigController(writer, history, secret);
    }
}
