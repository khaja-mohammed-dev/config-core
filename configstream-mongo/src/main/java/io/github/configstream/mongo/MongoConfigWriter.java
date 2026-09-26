package io.github.configstream.mongo;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.unset;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import io.github.configstream.api.ConfigDeletion;
import io.github.configstream.api.ConfigHistoryEntry;
import io.github.configstream.api.ConfigUpdate;
import io.github.configstream.api.ConfigWriter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import org.bson.Document;

/**
 * {@link ConfigWriter} for the document shape read by {@link MongoChangeStreamSource}.
 *
 * <p>Each write runs in a transaction that reads the current value, sets the new value, bumps the
 * document's {@code version} field and appends a {@link MongoConfigHistory} entry, so the config and
 * its history can never disagree. Concurrent writes to the same key conflict inside MongoDB and are
 * retried, which keeps versions gap-free and in order. Other fields on the document are left alone.
 *
 * <p>Deleting removes the document's {@code value} field but keeps the document and its {@code version}:
 * the change stream reports a document without a value as a delete, and a later write to the same key
 * continues from the kept version instead of reusing version numbers already in the history.
 *
 * <p>Changes made directly in the database (not through a writer) update caches as usual but are not
 * recorded in the history.
 */
public class MongoConfigWriter implements ConfigWriter {

    static final String VERSION_FIELD = "version";

    private final MongoClient client;
    private final MongoCollection<Document> collection;
    private final MongoConfigHistory history;

    /** {@code collection} and {@code history}'s collection must both belong to {@code client}. */
    public MongoConfigWriter(MongoClient client, MongoCollection<Document> collection, MongoConfigHistory history) {
        this.client = client;
        this.collection = collection;
        this.history = history;
    }

    @Override
    public Optional<ConfigHistoryEntry> write(ConfigUpdate update) {
        Objects.requireNonNull(update, "update");
        try (ClientSession session = client.startSession()) {
            // withTransaction retries the whole body on transient errors such as write conflicts
            return session.withTransaction(() -> writeInTransaction(session, update));
        }
    }

    private Optional<ConfigHistoryEntry> writeInTransaction(ClientSession session, ConfigUpdate update) {
        Document current = collection.find(session, eq("_id", update.key())).first();
        String oldValue = current == null ? null : MongoChangeStreamSource.valueOf(current);
        if (update.value().equals(oldValue)) {
            return Optional.empty();
        }
        long version = versionOf(current) + 1;
        collection.updateOne(session, eq("_id", update.key()),
                combine(set(MongoChangeStreamSource.VALUE_FIELD, update.value()), set(VERSION_FIELD, version)),
                new UpdateOptions().upsert(true));
        return Optional.of(record(session, new ConfigHistoryEntry(update.key(), version, oldValue, update.value(),
                update.changedBy(), now(), update.comment())));
    }

    @Override
    public Optional<ConfigHistoryEntry> delete(ConfigDeletion deletion) {
        Objects.requireNonNull(deletion, "deletion");
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> deleteInTransaction(session, deletion));
        }
    }

    private Optional<ConfigHistoryEntry> deleteInTransaction(ClientSession session, ConfigDeletion deletion) {
        Document current = collection.find(session, eq("_id", deletion.key())).first();
        String oldValue = current == null ? null : MongoChangeStreamSource.valueOf(current);
        if (oldValue == null) {
            return Optional.empty();
        }
        long version = versionOf(current) + 1;
        collection.updateOne(session, eq("_id", deletion.key()),
                combine(unset(MongoChangeStreamSource.VALUE_FIELD), set(VERSION_FIELD, version)));
        return Optional.of(record(session, new ConfigHistoryEntry(deletion.key(), version, oldValue, null,
                deletion.changedBy(), now(), deletion.comment())));
    }

    private ConfigHistoryEntry record(ClientSession session, ConfigHistoryEntry entry) {
        history.insert(session, entry);
        return entry;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static long versionOf(Document doc) {
        // Documents created outside configstream have no version yet
        return doc != null && doc.get(VERSION_FIELD) instanceof Number n ? n.longValue() : 0;
    }
}
