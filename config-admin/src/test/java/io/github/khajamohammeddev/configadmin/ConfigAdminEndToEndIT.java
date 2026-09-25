package io.github.khajamohammeddev.configadmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.github.khajamohammeddev.configadmin.registry.InstanceRegistry;
import io.github.khajamohammeddev.demoservice.DemoServiceApplication;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The Phase 5A goal end to end: a real config-core service registers with a real config-admin, and
 * the admin UI shows its live instance, current config and change history.
 */
@Testcontainers
class ConfigAdminEndToEndIT {

    private static final String SECRET = "0123456789abcdef-e2e-secret";
    private static final String NO_MONGO_AUTOCONFIG =
            "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration";

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    static final HttpClient http = HttpClient.newHttpClient();
    static ConfigurableApplicationContext admin;
    static String adminUrl;

    @BeforeAll
    static void startAdmin() {
        admin = new SpringApplicationBuilder(ConfigAdminApplication.class).run(
                "--server.port=0",
                "--config-core.enabled=false",
                NO_MONGO_AUTOCONFIG,
                "--config-admin.service-secrets.orders=" + SECRET);
        adminUrl = "http://localhost:" + admin.getEnvironment().getProperty("local.server.port");
    }

    @AfterAll
    static void stopAdmin() {
        admin.close();
    }

    @Test
    void serviceRegistersAndItsConfigAndHistoryShowUpInTheAdminUi() throws Exception {
        try (MongoClient mongo = MongoClients.create(MONGO.getReplicaSetUrl())) {
            mongo.getDatabase("orders").getCollection("config")
                    .insertOne(new Document("_id", "feature.x.enabled").append("value", "true"));
        }
        InstanceRegistry registry = admin.getBean(InstanceRegistry.class);

        try (ConfigurableApplicationContext service = startService()) {
            await().atMost(Duration.ofSeconds(10))
                    .until(() -> registry.service("orders").map(s -> s.upCount() == 1).orElse(false));
            String serviceUrl = "http://localhost:" + service.getEnvironment().getProperty("local.server.port");
            updateThroughService(serviceUrl, "limits.max", "50");

            assertThat(getHtml(adminUrl + "/")).contains("href=\"/services/orders\"", "team-a", "1 / 1");
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(getHtml(adminUrl + "/services/orders"))
                    .contains(">UP<", "feature.x.enabled", "limits.max", "50")
                    .doesNotContain("class=\"error\""));
            assertThat(getHtml(adminUrl + "/services/orders/history?key=limits.max"))
                    .contains("v1", "alice", "(created)", "launch");
        }

        // A clean shutdown deregisters the instance
        assertThat(registry.service("orders")).isEmpty();
        assertThat(getHtml(adminUrl + "/")).contains("No services registered yet");
    }

    private static ConfigurableApplicationContext startService() {
        return new SpringApplicationBuilder(DemoServiceApplication.class).run(
                "--server.port=0",
                "--spring.application.name=orders",
                NO_MONGO_AUTOCONFIG,
                "--config-core.team=team-a",
                "--config-core.mongo.uri=" + MONGO.getReplicaSetUrl("orders"),
                "--config-core.internal.secret=" + SECRET,
                "--config-core.admin.url=" + adminUrl,
                "--config-core.admin.heartbeat-interval=200ms",
                "--config-core.instance.host=localhost");
    }

    private static void updateThroughService(String serviceUrl, String key, String value) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(serviceUrl + "/internal/config/update"))
                .header("Content-Type", "application/json")
                .header("X-Config-Core-Secret", SECRET)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"key\":\"%s\",\"value\":\"%s\",\"changedBy\":\"alice\",\"comment\":\"launch\"}"
                                .formatted(key, value)))
                .build();
        assertThat(http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
    }

    private static String getHtml(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(url).isEqualTo(200);
        return response.body();
    }
}
