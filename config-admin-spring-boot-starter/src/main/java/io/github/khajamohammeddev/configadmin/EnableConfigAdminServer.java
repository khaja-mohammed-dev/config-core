package io.github.khajamohammeddev.configadmin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Turns this Spring Boot application into the config-core admin server, the way
 * {@code @EnableEurekaServer} turns one into a Eureka server:
 *
 * <pre>
 * &#64;SpringBootApplication
 * &#64;EnableConfigAdminServer
 * public class ConfigAdminApp { ... }
 * </pre>
 *
 * The app then accepts registrations and heartbeats from services using
 * {@code config-core-spring-boot-starter}, and serves the dashboard for viewing and changing their
 * config. Having the dependency on the classpath alone activates nothing.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ConfigAdminServerMarkerConfiguration.class)
public @interface EnableConfigAdminServer {
}
