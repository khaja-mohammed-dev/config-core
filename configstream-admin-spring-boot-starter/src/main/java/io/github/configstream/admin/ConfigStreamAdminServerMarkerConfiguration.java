package io.github.configstream.admin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registered by {@link EnableConfigStreamAdminServer}; its marker bean is what switches
 * {@link ConfigStreamAdminServerAutoConfiguration} on.
 */
@Configuration(proxyBeanMethods = false)
public class ConfigStreamAdminServerMarkerConfiguration {

    @Bean
    Marker configStreamAdminServerMarker() {
        return new Marker();
    }

    static final class Marker {
    }
}
