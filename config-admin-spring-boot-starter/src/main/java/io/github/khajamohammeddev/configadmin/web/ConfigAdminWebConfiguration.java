package io.github.khajamohammeddev.configadmin.web;

import io.github.khajamohammeddev.configadmin.ConfigAdminProperties;
import io.github.khajamohammeddev.configadmin.client.ServiceClient;
import io.github.khajamohammeddev.configadmin.registry.InstanceRegistry;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The dashboard, served under {@code config-admin.dashboard.path} (default {@code /}) so it can sit
 * beside the host app's own pages. Templates live under {@code templates/config-admin/} and static
 * files under {@code /config-admin/}, clear of the host app's.
 */
@Configuration(proxyBeanMethods = false)
public class ConfigAdminWebConfiguration implements WebMvcConfigurer {

    private final String basePath;

    ConfigAdminWebConfiguration(ConfigAdminProperties properties) {
        this.basePath = properties.getDashboard().basePath();
    }

    /** Used by the templates as {@code ${@configAdminTime.ago(instant)}}. */
    @Bean
    TimeFormat configAdminTime() {
        return new TimeFormat(Clock.systemUTC());
    }

    @Bean
    DashboardController configAdminDashboardController(InstanceRegistry registry, ServiceClient client) {
        return new DashboardController(registry, client);
    }

    @Bean
    ConfigEditController configAdminConfigEditController(InstanceRegistry registry, ServiceClient client) {
        return new ConfigEditController(registry, client, basePath);
    }

    @Bean
    DashboardModel configAdminDashboardModel() {
        return new DashboardModel(basePath);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        if (!basePath.isEmpty()) {
            configurer.addPathPrefix(basePath,
                    HandlerTypePredicate.forAssignableType(DashboardController.class, ConfigEditController.class));
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (!basePath.isEmpty()) {
            registry.addRedirectViewController(basePath, basePath + "/");
        }
    }
}
