package io.github.khajamohammeddev.configadmin;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khajamohammeddev.configadmin.client.ServiceCallException;
import io.github.khajamohammeddev.configadmin.client.ServiceClient;
import io.github.khajamohammeddev.configcore.api.ConfigHistoryEntry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The registration API and the read-only UI, with calls to services mocked out. */
// The config-core starter (and so the Mongo driver) is on the test classpath for the end-to-end test
@SpringBootTest(properties = {
        "config-core.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // fresh registry per test
class ConfigAdminApplicationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ServiceClient serviceClient;

    @Test
    void registrationLifecycle() throws Exception {
        register("orders", "o-1", 8080);
        mvc.perform(put("/api/instances/o-1/heartbeat")).andExpect(status().isNoContent());
        mvc.perform(put("/api/instances/unknown/heartbeat")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/instances/o-1")).andExpect(status().isNoContent());
        mvc.perform(put("/api/instances/o-1/heartbeat")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsIncompleteRegistration() throws Exception {
        mvc.perform(post("/api/instances").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceName\":\"orders\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dashboardListsServices() throws Exception {
        mvc.perform(get("/")).andExpect(content().string(containsString("No services registered yet")));

        register("orders", "o-1", 8080);
        register("orders", "o-2", 8081);
        register("billing", "b-1", 8082);

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("href=\"/services/orders\""),
                        containsString("href=\"/services/billing\""),
                        containsString("2 / 2"),
                        containsString("team-a"),
                        not(containsString("No services registered yet")))));
    }

    @Test
    void servicePageShowsInstancesAndConfig() throws Exception {
        register("orders", "o-1", 8080);
        when(serviceClient.currentConfig("orders"))
                .thenReturn(new TreeMap<>(Map.of("feature.x.enabled", "true", "limits.max", "50")));

        mvc.perform(get("/services/orders"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("o-1"),
                        containsString("http://localhost:8080"),
                        containsString(">UP<"),
                        containsString("feature.x.enabled"),
                        containsString("limits.max"),
                        containsString("/services/orders/history?key=limits.max"))));
    }

    @Test
    void servicePageShowsCallFailuresInsteadOfErroring() throws Exception {
        register("orders", "o-1", 8080);
        when(serviceClient.currentConfig("orders"))
                .thenThrow(new ServiceCallException("'orders' rejected the configured secret."));

        mvc.perform(get("/services/orders"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("o-1"),
                        containsString("rejected the configured secret"))));
    }

    @Test
    void unknownServiceIs404() throws Exception {
        mvc.perform(get("/services/nope")).andExpect(status().isNotFound());
    }

    @Test
    void historyPageShowsEntries() throws Exception {
        register("orders", "o-1", 8080);
        when(serviceClient.history("orders", "limits.max", 100)).thenReturn(List.of(
                new ConfigHistoryEntry("limits.max", 2, "10", "50", "alice", Instant.parse("2026-09-25T10:00:00Z"),
                        "Reverted to v1"),
                new ConfigHistoryEntry("limits.max", 1, null, "10", "bob", Instant.parse("2026-09-24T09:00:00Z"),
                        null)));

        mvc.perform(get("/services/orders/history").param("key", "limits.max"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("v2"),
                        containsString("alice"),
                        containsString("Reverted to v1"),
                        containsString("2026-09-25 10:00:00 UTC"),
                        containsString("(created)"))));
    }

    private void register(String service, String id, int port) throws Exception {
        mvc.perform(post("/api/instances").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"serviceName\":\"%s\",\"instanceId\":\"%s\",\"host\":\"localhost\",\"port\":%d,\"team\":\"team-a\"}"
                                .formatted(service, id, port)))
                .andExpect(status().isCreated());
    }
}
