package io.github.khajamohammeddev.configcore.mongo;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import io.github.khajamohammeddev.configcore.api.ConfigCache;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.awaitility.core.ConditionFactory;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MongoChangeStreamSourceIT {

    /** The phase goal: a change made directly in Mongo shows up in the cache within ~1 second. */
    private static final Duration PROPAGATION = Duration.ofSeconds(1);

    // MongoDBContainer runs a single-node replica set, which change streams require.
    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    static MongoClient client;

    MongoCollection<Document> collection;
    MongoChangeStreamSource source;
    ConfigCache cache;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void setUp() {
        // Fresh collection per test so tests can't see each other's data
        collection = client.getDatabase("configcore").getCollection("config_" + UUID.randomUUID());
        source = new MongoChangeStreamSource(collection);
        cache = new ConfigCache();
    }

    @AfterEach
    void tearDown() {
        source.stop();
    }

    @Test
    void loadsExistingConfigBeforeStartReturns() {
        collection.insertMany(List.of(
                entry("feature.x.enabled", "true"),
                entry("limits.max", 10),
                new Document("_id", "no.value")));

        source.start(cache);

        assertThat(cache.getAll()).isEqualTo(Map.of("feature.x.enabled", "true", "limits.max", "10"));
    }

    @Test
    void reflectsInsertUpdateAndDeleteWithoutRestart() {
        source.start(cache);

        collection.insertOne(entry("feature.x.enabled", "false"));
        awaitCache().until(() -> cache.get("feature.x.enabled").orElse(""), "false"::equals);

        collection.updateOne(eq("_id", "feature.x.enabled"), set("value", "true"));
        awaitCache().until(() -> cache.get("feature.x.enabled").orElse(""), "true"::equals);

        collection.replaceOne(eq("_id", "feature.x.enabled"), entry("feature.x.enabled", "replaced"));
        awaitCache().until(() -> cache.get("feature.x.enabled").orElse(""), "replaced"::equals);

        collection.deleteOne(eq("_id", "feature.x.enabled"));
        awaitCache().until(() -> cache.get("feature.x.enabled").isEmpty());
    }

    @Test
    void doesNotLoseWritesMadeDuringStartup() {
        int writes = 500;
        collection.insertOne(entry("counter", 0));

        // Keep writing while start() opens the stream and loads the snapshot
        CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
            for (int i = 1; i <= writes; i++) {
                collection.updateOne(eq("_id", "counter"), set("value", i));
                collection.insertOne(entry("key." + i, i));
            }
        });
        source.start(cache);
        writer.join();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(cache.get("counter")).contains(String.valueOf(writes));
            assertThat(cache.getAll()).hasSize(writes + 1);
        });
    }

    @Test
    void reloadsAfterCollectionIsDropped() {
        collection.insertOne(entry("old", "1"));
        source.start(cache);

        collection.drop();
        collection.insertOne(entry("new", "2"));

        await().atMost(Duration.ofSeconds(10))
                .until(cache::getAll, Map.of("new", "2")::equals);
    }

    @Test
    void stopHaltsUpdatesAndIsIdempotent() {
        source.start(cache);
        source.stop();
        source.stop();

        collection.insertOne(entry("after.stop", "x"));

        await().during(PROPAGATION).atMost(PROPAGATION.multipliedBy(2))
                .until(() -> cache.get("after.stop").isEmpty());
    }

    @Test
    void writerChangesReachTheCacheAndKeepOtherFields() {
        collection.insertOne(entry("feature.x.enabled", "false").append("description", "kept"));
        source.start(cache);
        MongoConfigWriter writer = new MongoConfigWriter(collection);

        writer.put("feature.x.enabled", "true");
        writer.put("brand.new", "1");

        awaitCache().until(cache::getAll, Map.of("feature.x.enabled", "true", "brand.new", "1")::equals);
        assertThat(collection.find(eq("_id", "feature.x.enabled")).first())
                .containsEntry("description", "kept");
    }

    @Test
    void cannotStartTwice() {
        source.start(cache);

        assertThatThrownBy(() -> source.start(cache)).isInstanceOf(IllegalStateException.class);
    }

    private static Document entry(String key, Object value) {
        return new Document("_id", key).append(MongoChangeStreamSource.VALUE_FIELD, value);
    }

    private static ConditionFactory awaitCache() {
        return await().atMost(PROPAGATION).pollInterval(Duration.ofMillis(20));
    }
}
