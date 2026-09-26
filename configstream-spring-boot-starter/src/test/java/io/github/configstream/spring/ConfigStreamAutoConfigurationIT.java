package io.github.configstream.spring;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import java.time.Duration;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** A Spring app that only sets properties: loads config from Mongo and follows live changes. */
@Testcontainers
class ConfigStreamAutoConfigurationIT {

    private static final Duration PROPAGATION = Duration.ofSeconds(1);

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    static MongoClient client;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @Test
    void loadsConfigAndReflectsLiveUpdatesWithoutRestart() {
        MongoCollection<Document> collection = client.getDatabase("app").getCollection("flags");
        collection.insertOne(new Document("_id", "feature.x.enabled").append("value", false));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigStreamAutoConfiguration.class))
                .withUserConfiguration(ConfigStreamAutoConfigurationTest.EventCollector.class)
                .withPropertyValues(
                        "configstream.mongo.uri=" + MONGO.getReplicaSetUrl("app"),
                        "configstream.mongo.collection=flags")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ConfigService config = context.getBean(ConfigService.class);
                    var events = context.getBean(ConfigStreamAutoConfigurationTest.EventCollector.class).events;
                    assertThat(config.getBoolean("feature.x.enabled", true)).isFalse();

                    collection.updateOne(eq("_id", "feature.x.enabled"), set("value", true));

                    await().atMost(PROPAGATION).untilAsserted(() -> {
                        assertThat(config.getBoolean("feature.x.enabled", false)).isTrue();
                        assertThat(events).containsExactly(
                                new ConfigChangedEvent("feature.x.enabled", "false", "true"));
                    });
                });
    }

    @Test
    void internalEndpointWritesThroughToMongoAndBackIntoTheCache() {
        String secret = "0123456789abcdef-it-secret";
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigStreamAutoConfiguration.class, ConfigStreamEndpointAutoConfiguration.class,
                        WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
                        JacksonAutoConfiguration.class))
                .withPropertyValues(
                        "configstream.mongo.uri=" + MONGO.getReplicaSetUrl("endpoint"),
                        "configstream.internal.secret=" + secret)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MockMvc mvc = MockMvcBuilders
                            .webAppContextSetup((WebApplicationContext) context.getSourceApplicationContext())
                            .build();

                    for (String value : new String[] {"50", "80"}) {
                        mvc.perform(post("/internal/config/update")
                                        .header(InternalConfigController.SECRET_HEADER, secret)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"key\":\"limits.max\",\"value\":\"" + value + "\",\"changedBy\":\"alice\"}"))
                                .andExpect(status().isOk());
                    }

                    assertThat(client.getDatabase("endpoint").getCollection("config")
                            .find(eq("_id", "limits.max")).first())
                            .containsEntry("value", "80");
                    ConfigService config = context.getBean(ConfigService.class);
                    await().atMost(PROPAGATION).until(() -> config.get("limits.max", ""), "80"::equals);

                    // History lands in <collection>_history and is served newest first
                    mvc.perform(get("/internal/config/history")
                                    .header(InternalConfigController.SECRET_HEADER, secret)
                                    .param("key", "limits.max"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.length()").value(2))
                            .andExpect(jsonPath("$[0].version").value(2))
                            .andExpect(jsonPath("$[0].oldValue").value("50"))
                            .andExpect(jsonPath("$[0].newValue").value("80"))
                            .andExpect(jsonPath("$[0].changedBy").value("alice"));
                    assertThat(client.getDatabase("endpoint").getCollection("config_history").countDocuments())
                            .isEqualTo(2);
                });
    }
}
