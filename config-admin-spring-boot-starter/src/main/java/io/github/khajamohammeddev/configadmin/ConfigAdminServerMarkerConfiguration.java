package io.github.khajamohammeddev.configadmin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registered by {@link EnableConfigAdminServer}; its marker bean is what switches
 * {@link ConfigAdminServerAutoConfiguration} on.
 */
@Configuration(proxyBeanMethods = false)
public class ConfigAdminServerMarkerConfiguration {

    @Bean
    Marker configAdminServerMarker() {
        return new Marker();
    }

    static final class Marker {
    }
}
