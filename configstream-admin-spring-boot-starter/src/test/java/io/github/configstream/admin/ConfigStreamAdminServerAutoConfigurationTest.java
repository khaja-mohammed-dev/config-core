package io.github.configstream.admin;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.configstream.admin.client.ServiceClient;
import io.github.configstream.admin.registry.InstanceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ConfigStreamAdminServerAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigStreamAdminServerAutoConfiguration.class));

    @Test
    void theDependencyAloneActivatesNothing() {
        runner.withUserConfiguration(PlainApp.class).run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(InstanceRegistry.class)
                .doesNotHaveBean(ServiceClient.class)
                .doesNotHaveBean("configStreamAdminDashboardController"));
    }

    @Test
    void theAnnotationTurnsTheAppIntoTheAdminServer() {
        runner.withUserConfiguration(AdminApp.class).run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(InstanceRegistry.class)
                .hasSingleBean(ServiceClient.class)
                .hasSingleBean(ConfigStreamAdminProperties.class)
                .hasBean("configStreamAdminRegistrationController")
                .hasBean("configStreamAdminDashboardController")
                .hasBean("configStreamAdminConfigEditController")
                .hasBean("configStreamAdminTime"));
    }

    @Test
    void offInNonWebApps() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigStreamAdminServerAutoConfiguration.class))
                .withUserConfiguration(AdminApp.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(InstanceRegistry.class));
    }

    @Test
    void dashboardPathIsNormalisedToALinkPrefix() {
        ConfigStreamAdminProperties.Dashboard dashboard = new ConfigStreamAdminProperties().getDashboard();
        assertThat(dashboard.basePath()).as("default").isEmpty();
        for (String path : new String[] {"/admin", "/admin/", "admin", " /admin// "}) {
            dashboard.setPath(path);
            assertThat(dashboard.basePath()).as(path).isEqualTo("/admin");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PlainApp {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigStreamAdminServer
    static class AdminApp {
    }
}
