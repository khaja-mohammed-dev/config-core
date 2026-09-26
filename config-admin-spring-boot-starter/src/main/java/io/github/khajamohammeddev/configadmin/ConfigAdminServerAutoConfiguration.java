package io.github.khajamohammeddev.configadmin;

import io.github.khajamohammeddev.configadmin.client.ServiceClient;
import io.github.khajamohammeddev.configadmin.registry.ConfigAdminRegistryConfiguration;
import io.github.khajamohammeddev.configadmin.registry.InstanceRegistry;
import io.github.khajamohammeddev.configadmin.web.ConfigAdminWebConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

/**
 * The admin server: registration API ({@code /api/instances}), the dashboard, and the client it uses to
 * call services. Active only in servlet web apps annotated with {@link EnableConfigAdminServer}.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(ConfigAdminServerMarkerConfiguration.Marker.class)
@EnableConfigurationProperties(ConfigAdminProperties.class)
@Import({ConfigAdminRegistryConfiguration.class, ConfigAdminWebConfiguration.class})
public class ConfigAdminServerAutoConfiguration {

    @Bean
    ServiceClient configAdminServiceClient(ObjectProvider<RestClient.Builder> builder, InstanceRegistry registry,
            ConfigAdminProperties properties) {
        // Boot's builder carries the app's message converters (e.g. Jackson with java.time support)
        return new ServiceClient(builder.getIfAvailable(RestClient::builder), registry, properties);
    }
}
