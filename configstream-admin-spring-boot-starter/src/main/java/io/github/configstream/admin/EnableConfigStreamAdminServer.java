package io.github.configstream.admin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Turns this Spring Boot application into the configstream admin server, the way
 * {@code @EnableEurekaServer} turns one into a Eureka server:
 *
 * <pre>
 * &#64;SpringBootApplication
 * &#64;EnableConfigStreamAdminServer
 * public class ConfigStreamAdminApp { ... }
 * </pre>
 *
 * The app then accepts registrations and heartbeats from services using
 * {@code configstream-spring-boot-starter}, and serves the dashboard for viewing and changing their
 * config. Having the dependency on the classpath alone activates nothing.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ConfigStreamAdminServerMarkerConfiguration.class)
public @interface EnableConfigStreamAdminServer {
}
