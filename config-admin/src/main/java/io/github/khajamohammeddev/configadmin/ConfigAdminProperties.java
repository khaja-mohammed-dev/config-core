package io.github.khajamohammeddev.configadmin;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code config-admin.*}. */
@ConfigurationProperties(prefix = "config-admin")
public class ConfigAdminProperties {

    /**
     * How long an instance counts as up after its last heartbeat. Instances send one every 15s by
     * default, so the default tolerates two missed heartbeats.
     */
    private Duration leaseDuration = Duration.ofSeconds(45);

    /** How long after its last heartbeat a down instance is removed from the registry. */
    private Duration evictAfter = Duration.ofMinutes(10);

    /** Timeout for each call to a service's internal endpoints. */
    private Duration requestTimeout = Duration.ofSeconds(5);

    /**
     * Shared secret per service name, matching that service's {@code config-core.internal.secret}.
     * Needed to read (and later change) its config.
     */
    private Map<String, String> serviceSecrets = new HashMap<>();

    /** Secret used for services not listed in {@code service-secrets}. */
    private String defaultServiceSecret;

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getEvictAfter() {
        return evictAfter;
    }

    public void setEvictAfter(Duration evictAfter) {
        this.evictAfter = evictAfter;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Map<String, String> getServiceSecrets() {
        return serviceSecrets;
    }

    public void setServiceSecrets(Map<String, String> serviceSecrets) {
        this.serviceSecrets = serviceSecrets;
    }

    public String getDefaultServiceSecret() {
        return defaultServiceSecret;
    }

    public void setDefaultServiceSecret(String defaultServiceSecret) {
        this.defaultServiceSecret = defaultServiceSecret;
    }

    /** The secret for {@code serviceName}, or {@code null} if none is configured. */
    public String secretFor(String serviceName) {
        return serviceSecrets.getOrDefault(serviceName, defaultServiceSecret);
    }
}
