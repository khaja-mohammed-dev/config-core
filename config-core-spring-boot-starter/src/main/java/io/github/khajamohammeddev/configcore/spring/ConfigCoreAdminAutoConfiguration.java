package io.github.khajamohammeddev.configcore.spring;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Registers this instance with the config-admin app and keeps it alive with heartbeats. Only active
 * when {@code config-core.admin.url} is set; without it the service runs standalone.
 */
@AutoConfiguration(after = ConfigCoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "config-core", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "config-core.admin", name = "url")
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(ConfigCoreProperties.class)
public class ConfigCoreAdminAutoConfiguration {

    // Short timeouts: the admin app being slow must never hold up this service, including shutdown.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    AdminRegistration configCoreAdminRegistration(ConfigCoreProperties properties, Environment environment) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        RestClient http = RestClient.builder()
                .baseUrl(properties.getAdmin().getUrl())
                .requestFactory(requestFactory)
                .build();
        return new AdminRegistration(http, () -> instanceInfo(properties, environment),
                properties.getAdmin().getHeartbeatInterval());
    }

    private static AdminRegistration.InstanceInfo instanceInfo(ConfigCoreProperties properties, Environment env) {
        ConfigCoreProperties.Instance instance = properties.getInstance();
        String id = instance.getId() != null ? instance.getId() : UUID.randomUUID().toString();
        String host = instance.getHost() != null ? instance.getHost() : localAddress();
        // Set by Spring Boot once the embedded web server has started
        Integer port = instance.getPort() != null ? instance.getPort() : env.getProperty("local.server.port", Integer.class);
        String serviceName = env.getProperty("spring.application.name", "application");
        return new AdminRegistration.InstanceInfo(serviceName, id, host, port, properties.getTeam());
    }

    private static String localAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }
}
