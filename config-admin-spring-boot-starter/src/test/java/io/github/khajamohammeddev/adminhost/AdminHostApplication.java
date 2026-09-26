package io.github.khajamohammeddev.adminhost;

import io.github.khajamohammeddev.configadmin.EnableConfigAdminServer;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A user's own Spring Boot app turned into the admin server by the dependency plus one annotation,
 * the way {@code @EnableEurekaServer} works. Lives outside the starter's package so nothing is found
 * by component scanning: everything must come from the auto-configuration.
 */
@SpringBootApplication
@EnableConfigAdminServer
public class AdminHostApplication {
}
