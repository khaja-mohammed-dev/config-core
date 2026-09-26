package io.github.khajamohammeddev.configadmin.registry;

/**
 * What a config-core instance sends when it registers ({@code POST /api/instances}). Matches the
 * payload built by config-core's {@code AdminRegistration}.
 *
 * @param port may be {@code null} if the instance runs no web server; the admin app then cannot
 *     call it
 * @param team may be {@code null}
 */
public record InstanceRegistration(String serviceName, String instanceId, String host, Integer port, String team) {

    boolean isValid() {
        return notBlank(serviceName) && notBlank(instanceId) && notBlank(host);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
