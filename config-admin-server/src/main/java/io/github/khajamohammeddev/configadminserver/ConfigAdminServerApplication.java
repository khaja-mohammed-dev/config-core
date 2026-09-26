package io.github.khajamohammeddev.configadminserver;

import io.github.khajamohammeddev.configadmin.EnableConfigAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A ready-to-run config-admin. This class is the whole app: any Spring Boot app becomes the admin
 * server the same way, with the starter dependency and {@link EnableConfigAdminServer}.
 */
@SpringBootApplication
@EnableConfigAdminServer
public class ConfigAdminServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigAdminServerApplication.class, args);
    }
}
