package io.github.khajamohammeddev.configadmin.registry;

import io.github.khajamohammeddev.configadmin.ConfigAdminProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The instance registry and the API services use to register with it. */
@Configuration(proxyBeanMethods = false)
public class ConfigAdminRegistryConfiguration {

    @Bean
    InstanceRegistry configAdminInstanceRegistry(ConfigAdminProperties properties) {
        return new InstanceRegistry(properties, Clock.systemUTC());
    }

    @Bean
    RegistrationController configAdminRegistrationController(InstanceRegistry registry) {
        return new RegistrationController(registry);
    }
}
