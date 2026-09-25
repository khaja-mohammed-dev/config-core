package io.github.khajamohammeddev.configadmin.client;

import io.github.khajamohammeddev.configadmin.ConfigAdminProperties;
import io.github.khajamohammeddev.configadmin.registry.InstanceRegistry;
import io.github.khajamohammeddev.configadmin.registry.RegisteredInstance;
import io.github.khajamohammeddev.configadmin.registry.ServiceSummary;
import io.github.khajamohammeddev.configcore.api.ConfigHistoryEntry;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls a service's config-core internal endpoints ({@code /internal/config/**}). Every instance
 * of a service reads the same store, so any healthy instance can answer: instances are tried in
 * turn until one responds.
 */
@Component
public class ServiceClient {

    static final String SECRET_HEADER = "X-Config-Core-Secret";

    private static final Logger log = LoggerFactory.getLogger(ServiceClient.class);

    private final RestClient http;
    private final InstanceRegistry registry;
    private final ConfigAdminProperties properties;

    public ServiceClient(RestClient.Builder builder, InstanceRegistry registry, ConfigAdminProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getRequestTimeout());
        requestFactory.setReadTimeout(properties.getRequestTimeout());
        this.http = builder.requestFactory(requestFactory).build();
        this.registry = registry;
        this.properties = properties;
    }

    /** Current values as one healthy instance sees them, sorted by key. */
    public Map<String, String> currentConfig(String serviceName) {
        return call(serviceName, (baseUrl, secret) -> http.get()
                .uri(baseUrl + "/internal/config")
                .header(SECRET_HEADER, secret)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, String>>() {}));
    }

    /** Changes to {@code key}, newest first. */
    public List<ConfigHistoryEntry> history(String serviceName, String key, int limit) {
        return call(serviceName, (baseUrl, secret) -> http.get()
                .uri(baseUrl + "/internal/config/history?key={key}&limit={limit}", key, limit)
                .header(SECRET_HEADER, secret)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ConfigHistoryEntry>>() {}));
    }

    private <T> T call(String serviceName, BiFunction<String, String, T> request) {
        ServiceSummary service = registry.service(serviceName)
                .orElseThrow(() -> new ServiceCallException("No service named '" + serviceName + "' is registered."));
        String secret = properties.secretFor(serviceName);
        if (secret == null || secret.isBlank()) {
            throw new ServiceCallException("No secret configured for '" + serviceName + "'. Set config-admin.service-secrets."
                    + serviceName + " (or config-admin.default-service-secret) to its config-core.internal.secret.");
        }
        List<String> baseUrls = service.instances().stream()
                .filter(RegisteredInstance::up)
                .map(RegisteredInstance::baseUrl)
                .filter(url -> url != null)
                .toList();
        if (baseUrls.isEmpty()) {
            throw new ServiceCallException("No healthy instance of '" + serviceName + "' with a reachable address.");
        }
        RestClientException lastFailure = null;
        for (String baseUrl : baseUrls) {
            try {
                return request.apply(baseUrl, secret);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    // Every instance shares the secret, so trying the others won't help
                    throw new ServiceCallException("'" + serviceName + "' rejected the configured secret.", e);
                }
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    throw new ServiceCallException("'" + serviceName + "' does not expose the config-core internal "
                            + "endpoints. Is config-core.internal.secret set on the service?", e);
                }
                lastFailure = e;
            } catch (RestClientException e) {
                log.debug("Call to {} at {} failed; trying next instance", serviceName, baseUrl, e);
                lastFailure = e;
            }
        }
        throw new ServiceCallException("Could not reach any instance of '" + serviceName + "' ("
                + baseUrls.size() + " tried): " + lastFailure.getMessage(), lastFailure);
    }
}
