package io.github.khajamohammeddev.configadmin;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.khajamohammeddev.configadmin.client.ServiceClient;
import io.github.khajamohammeddev.configadmin.registry.InstanceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ConfigAdminServerAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigAdminServerAutoConfiguration.class));

    @Test
    void theDependencyAloneActivatesNothing() {
        runner.withUserConfiguration(PlainApp.class).run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(InstanceRegistry.class)
                .doesNotHaveBean(ServiceClient.class)
                .doesNotHaveBean("configAdminDashboardController"));
    }

    @Test
    void theAnnotationTurnsTheAppIntoTheAdminServer() {
        runner.withUserConfiguration(AdminApp.class).run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(InstanceRegistry.class)
                .hasSingleBean(ServiceClient.class)
                .hasSingleBean(ConfigAdminProperties.class)
                .hasBean("configAdminRegistrationController")
                .hasBean("configAdminDashboardController")
                .hasBean("configAdminConfigEditController")
                .hasBean("configAdminTime"));
    }

    @Test
    void offInNonWebApps() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigAdminServerAutoConfiguration.class))
                .withUserConfiguration(AdminApp.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(InstanceRegistry.class));
    }

    @Test
    void dashboardPathIsNormalisedToALinkPrefix() {
        ConfigAdminProperties.Dashboard dashboard = new ConfigAdminProperties().getDashboard();
        assertThat(dashboard.basePath()).as("default").isEmpty();
        for (String path : new String[] {"/config-admin", "/config-admin/", "config-admin", " /config-admin// "}) {
            dashboard.setPath(path);
            assertThat(dashboard.basePath()).as(path).isEqualTo("/config-admin");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PlainApp {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigAdminServer
    static class AdminApp {
    }
}
