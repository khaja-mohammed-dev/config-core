package io.github.configstream.admin.registry;

import java.time.Duration;
import java.time.Instant;

/**
 * A point-in-time view of one registered instance.
 *
 * @param up whether a heartbeat arrived within the lease duration
 */
public record RegisteredInstance(
        InstanceRegistration registration, Instant registeredAt, Instant lastHeartbeat, boolean up) {

    public String instanceId() {
        return registration.instanceId();
    }

    /** {@code http://host:port}, or {@code null} if the instance didn't report a port. */
    public String baseUrl() {
        return registration.port() == null ? null : "http://" + registration.host() + ":" + registration.port();
    }

    public Duration sinceLastHeartbeat(Instant now) {
        return Duration.between(lastHeartbeat, now);
    }
}
