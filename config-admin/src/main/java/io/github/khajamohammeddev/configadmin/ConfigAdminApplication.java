package io.github.khajamohammeddev.configadmin;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * The central config-admin app: services running config-core register here, and the UI shows each
 * service's live instances, current config and change history.
 */
@SpringBootApplication
@EnableConfigurationProperties(ConfigAdminProperties.class)
public class ConfigAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigAdminApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
