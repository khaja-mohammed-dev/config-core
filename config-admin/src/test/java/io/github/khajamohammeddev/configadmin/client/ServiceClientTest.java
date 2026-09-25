package io.github.khajamohammeddev.configadmin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;
import io.github.khajamohammeddev.configadmin.ConfigAdminProperties;
import io.github.khajamohammeddev.configadmin.registry.InstanceRegistration;
import io.github.khajamohammeddev.configadmin.registry.InstanceRegistry;
import io.github.khajamohammeddev.configcore.api.ConfigHistoryEntry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

class ServiceClientTest {

    private static final String SECRET = "0123456789abcdef-secret";

    private final ConfigAdminProperties properties = new ConfigAdminProperties();
    private final InstanceRegistry registry = new InstanceRegistry(properties, Clock.systemUTC());
    private final List<HttpServer> servers = new CopyOnWriteArrayList<>();
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private ServiceClient client;

    ServiceClientTest() {
        properties.setRequestTimeout(Duration.ofSeconds(2));
        properties.getServiceSecrets().put("orders", SECRET);
        RestClient.Builder builder = RestClient.builder()
                .messageConverters(c -> c.add(0, new MappingJackson2HttpMessageConverter(json)));
        client = new ServiceClient(builder, registry, properties);
    }

    @AfterEach
    void stopServers() {
        servers.forEach(s -> s.stop(0));
    }

    @Test
    void readsCurrentConfigWithTheSecret() throws IOException {
        int port = fakeService(200, "{\"a\":\"1\",\"b\":\"2\"}");
        registry.register(new InstanceRegistration("orders", "o-1", "localhost", port, null));

        assertThat(client.currentConfig("orders")).isEqualTo(Map.of("a", "1", "b", "2"));
    }

    @Test
    void readsHistory() throws IOException {
        ConfigHistoryEntry entry = new ConfigHistoryEntry("a", 2, "1", "2", "alice",
                Instant.parse("2026-09-25T10:00:00Z"), "why");
        int port = fakeService(200, json.writeValueAsString(List.of(entry)));
        registry.register(new InstanceRegistration("orders", "o-1", "localhost", port, null));

        assertThat(client.history("orders", "a", 10)).containsExactly(entry);
    }

    @Test
    void failsOverToTheNextHealthyInstance() throws IOException {
        registry.register(new InstanceRegistration("orders", "o-1-dead", "localhost", unusedPort(), null));
        int port = fakeService(200, "{\"a\":\"1\"}");
        registry.register(new InstanceRegistration("orders", "o-2", "localhost", port, null));

        assertThat(client.currentConfig("orders")).containsEntry("a", "1");
    }

    @Test
    void reportsWhenNoInstanceAnswers() throws IOException {
        registry.register(new InstanceRegistration("orders", "o-1", "localhost", unusedPort(), null));

        assertThatThrownBy(() -> client.currentConfig("orders"))
                .isInstanceOf(ServiceCallException.class)
                .hasMessageContaining("Could not reach any instance of 'orders' (1 tried)");
    }

    @Test
    void reportsRejectedSecret() throws IOException {
        int port = fakeService(401, "");
        registry.register(new InstanceRegistration("orders", "o-1", "localhost", port, null));

        assertThatThrownBy(() -> client.currentConfig("orders"))
                .isInstanceOf(ServiceCallException.class)
                .hasMessageContaining("rejected the configured secret");
    }

    @Test
    void reportsMissingEndpoint() throws IOException {
        int port = fakeService(404, "");
        registry.register(new InstanceRegistration("orders", "o-1", "localhost", port, null));

        assertThatThrownBy(() -> client.currentConfig("orders"))
                .hasMessageContaining("does not expose the config-core internal endpoints");
    }

    @Test
    void reportsMissingSecret() {
        registry.register(new InstanceRegistration("billing", "b-1", "localhost", 8080, null));

        assertThatThrownBy(() -> client.currentConfig("billing"))
                .isInstanceOf(ServiceCallException.class)
                .hasMessageContaining("config-admin.service-secrets.billing");
    }

    @Test
    void defaultSecretCoversUnlistedServices() throws IOException {
        properties.setDefaultServiceSecret(SECRET);
        int port = fakeService(200, "{}");
        registry.register(new InstanceRegistration("billing", "b-1", "localhost", port, null));

        assertThat(client.currentConfig("billing")).isEmpty();
    }

    @Test
    void reportsUnknownServiceAndNoAddress() {
        assertThatThrownBy(() -> client.currentConfig("nope")).hasMessageContaining("No service named 'nope'");

        registry.register(new InstanceRegistration("orders", "o-1", "localhost", null, null));
        assertThatThrownBy(() -> client.currentConfig("orders"))
                .hasMessageContaining("No healthy instance of 'orders' with a reachable address");
    }

    /** Answers every request with the given status and body, but only if the secret header matches. */
    private int fakeService(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/config", exchange -> {
            boolean authorized = SECRET.equals(exchange.getRequestHeaders().getFirst(ServiceClient.SECRET_HEADER));
            int code = authorized ? status : 401;
            byte[] bytes = (authorized ? body : "").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        servers.add(server);
        return server.getAddress().getPort();
    }

    private static int unusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
