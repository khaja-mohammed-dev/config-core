package io.github.khajamohammeddev.configcore.spring;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import java.time.Duration;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** A Spring app that only sets properties: loads config from Mongo and follows live changes. */
@Testcontainers
class ConfigCoreAutoConfigurationIT {

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
                .withConfiguration(AutoConfigurations.of(ConfigCoreAutoConfiguration.class))
                .withUserConfiguration(ConfigCoreAutoConfigurationTest.EventCollector.class)
                .withPropertyValues(
                        "config-core.mongo.uri=" + MONGO.getReplicaSetUrl("app"),
                        "config-core.mongo.collection=flags")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ConfigService config = context.getBean(ConfigService.class);
                    var events = context.getBean(ConfigCoreAutoConfigurationTest.EventCollector.class).events;
                    assertThat(config.getBoolean("feature.x.enabled", true)).isFalse();

                    collection.updateOne(eq("_id", "feature.x.enabled"), set("value", true));

                    await().atMost(PROPAGATION).untilAsserted(() -> {
                        assertThat(config.getBoolean("feature.x.enabled", false)).isTrue();
                        assertThat(events).containsExactly(
                                new ConfigChangedEvent("feature.x.enabled", "false", "true"));
                    });
                });
    }
}
