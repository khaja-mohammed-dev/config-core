package io.github.khajamohammeddev.configcore.mongo;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import io.github.khajamohammeddev.configcore.api.ConfigHistoryEntry;
import io.github.khajamohammeddev.configcore.api.ConfigUpdate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MongoConfigWriterIT {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    static MongoClient client;

    MongoCollection<Document> config;
    MongoCollection<Document> historyCollection;
    MongoConfigHistory history;
    MongoConfigWriter writer;

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
        String suffix = UUID.randomUUID().toString();
        config = client.getDatabase("configcore").getCollection("config_" + suffix);
        historyCollection = client.getDatabase("configcore").getCollection("history_" + suffix);
        history = new MongoConfigHistory(historyCollection);
        history.ensureIndexes();
        writer = new MongoConfigWriter(client, config, history);
    }

    @Test
    void recordsEveryChangeWithIncreasingVersions() {
        Instant before = Instant.now().minusSeconds(1);

        writer.write(new ConfigUpdate("limits.max", "10", "alice", null));
        writer.write(new ConfigUpdate("limits.max", "20", "bob", "traffic spike"));

        List<ConfigHistoryEntry> entries = history.history("limits.max", 10);
        assertThat(entries).extracting(ConfigHistoryEntry::version).containsExactly(2L, 1L);
        assertThat(entries.get(0)).satisfies(e -> {
            assertThat(e.oldValue()).isEqualTo("10");
            assertThat(e.newValue()).isEqualTo("20");
            assertThat(e.changedBy()).isEqualTo("bob");
            assertThat(e.comment()).isEqualTo("traffic spike");
            assertThat(e.changedAt()).isAfter(before);
        });
        assertThat(entries.get(1).oldValue()).as("created").isNull();
        assertThat(config.find(eq("_id", "limits.max")).first())
                .containsEntry("value", "20")
                .containsEntry("version", 2L);
    }

    @Test
    void writeReturnsTheRecordedEntry() {
        Optional<ConfigHistoryEntry> entry = writer.write(new ConfigUpdate("a", "1", "alice", null));

        assertThat(entry).isPresent();
        assertThat(history.history("a", 1)).containsExactly(entry.get());
    }

    @Test
    void writingTheSameValueRecordsNothing() {
        writer.write(new ConfigUpdate("a", "1", "alice", null));

        assertThat(writer.write(new ConfigUpdate("a", "1", "bob", null))).isEmpty();
        assertThat(history.history("a", 10)).hasSize(1);
    }

    @Test
    void rollbackIsJustAnotherWrite() {
        writer.write(new ConfigUpdate("a", "good", "alice", null));
        writer.write(new ConfigUpdate("a", "bad", "bob", null));
        ConfigHistoryEntry v1 = history.history("a", 10).get(1);

        writer.write(new ConfigUpdate("a", v1.newValue(), "alice", "Reverted to v" + v1.version()));

        assertThat(history.history("a", 10))
                .extracting(ConfigHistoryEntry::version, ConfigHistoryEntry::oldValue, ConfigHistoryEntry::newValue,
                        ConfigHistoryEntry::comment)
                .first().isEqualTo(tuple(3L, "bad", "good", "Reverted to v1"));
    }

    @Test
    void continuesVersioningDocumentsCreatedOutsideConfigCore() {
        config.insertOne(new Document("_id", "legacy").append("value", true));

        writer.write(new ConfigUpdate("legacy", "false", "alice", null));

        ConfigHistoryEntry entry = history.history("legacy", 1).get(0);
        assertThat(entry.version()).isEqualTo(1);
        assertThat(entry.oldValue()).isEqualTo("true");
    }

    @Test
    void concurrentWritesGetDistinctSequentialVersions() {
        int writes = 20;
        CompletableFuture<?>[] futures = IntStream.rangeClosed(1, writes)
                .mapToObj(i -> CompletableFuture.runAsync(
                        () -> writer.write(new ConfigUpdate("hot", "v" + i, "user" + i, null))))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();

        List<ConfigHistoryEntry> entries = history.history("hot", 100);
        assertThat(entries).extracting(ConfigHistoryEntry::version)
                .containsExactlyElementsOf(IntStream.iterate(writes, v -> v - 1).limit(writes)
                        .mapToObj(Long::valueOf).toList());
        // Each entry's old value is the previous entry's new value: no lost updates
        for (int i = 0; i < entries.size() - 1; i++) {
            assertThat(entries.get(i).oldValue()).isEqualTo(entries.get(i + 1).newValue());
        }
        assertThat(config.find(eq("_id", "hot")).first().getString("value")).isEqualTo(entries.get(0).newValue());
    }

    @Test
    void historyIsLimitedAndPerKey() {
        for (int i = 1; i <= 5; i++) {
            writer.write(new ConfigUpdate("a", "v" + i, "alice", null));
        }
        writer.write(new ConfigUpdate("b", "x", "alice", null));

        assertThat(history.history("a", 2)).extracting(ConfigHistoryEntry::newValue).containsExactly("v5", "v4");
        assertThat(history.history("missing", 10)).isEmpty();
    }

    @Test
    void indexRejectsDuplicateVersions() {
        writer.write(new ConfigUpdate("a", "1", "alice", null));

        assertThatThrownBy(() -> historyCollection.insertOne(new Document("key", "a").append("version", 1L)))
                .isInstanceOf(MongoWriteException.class);
    }
}
