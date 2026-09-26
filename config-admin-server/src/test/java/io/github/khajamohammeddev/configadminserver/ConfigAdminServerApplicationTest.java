package io.github.khajamohammeddev.configadminserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The packaged app starts as the admin server, with its dashboard and registration API. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigAdminServerApplicationTest {

    @Autowired
    TestRestTemplate http;

    @Test
    void servesTheDashboardAndAcceptsRegistrations() {
        ResponseEntity<String> page = http.getForEntity("/", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("config-admin", "No services registered yet");

        ResponseEntity<Void> response = http.postForEntity("/api/instances",
                Map.of("serviceName", "orders", "instanceId", "o-1", "host", "localhost", "port", 8080),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.getForObject("/", String.class)).contains("href=\"/services/orders\"");
    }
}
