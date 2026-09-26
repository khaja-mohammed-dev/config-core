package io.github.khajamohammeddev.demoservice;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * An empty application that uses config-core purely through its Spring Boot starter, standing in
 * for a real service in the end-to-end test. Lives outside the config-admin package so the admin
 * app's component scan doesn't pick it up.
 */
@SpringBootApplication
public class DemoServiceApplication {
}
