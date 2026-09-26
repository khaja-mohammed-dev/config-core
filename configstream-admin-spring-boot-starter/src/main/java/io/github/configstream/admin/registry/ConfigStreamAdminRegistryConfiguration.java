package io.github.configstream.admin.registry;

import io.github.configstream.admin.ConfigStreamAdminProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The instance registry and the API services use to register with it. */
@Configuration(proxyBeanMethods = false)
public class ConfigStreamAdminRegistryConfiguration {

    @Bean
    InstanceRegistry configStreamAdminInstanceRegistry(ConfigStreamAdminProperties properties) {
        return new InstanceRegistry(properties, Clock.systemUTC());
    }

    @Bean
    RegistrationController configStreamAdminRegistrationController(InstanceRegistry registry) {
        return new RegistrationController(registry);
    }
}
