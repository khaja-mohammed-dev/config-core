package io.github.khajamohammeddev.configadmin.registry;

import java.time.Instant;
import java.util.List;

/**
 * One service and all of its registered instances.
 *
 * @param team taken from the most recently registered instance; may be {@code null}
 */
public record ServiceSummary(String serviceName, String team, List<RegisteredInstance> instances) {

    public long upCount() {
        return instances.stream().filter(RegisteredInstance::up).count();
    }

    public Instant lastHeartbeat() {
        return instances.stream().map(RegisteredInstance::lastHeartbeat).max(Instant::compareTo).orElse(null);
    }
}
