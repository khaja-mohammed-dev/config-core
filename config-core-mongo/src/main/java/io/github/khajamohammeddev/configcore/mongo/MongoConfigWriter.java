package io.github.khajamohammeddev.configcore.mongo;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import io.github.khajamohammeddev.configcore.api.ConfigWriter;
import java.util.Objects;
import org.bson.Document;

/**
 * {@link ConfigWriter} for the document shape read by {@link MongoChangeStreamSource}. Only the
 * {@code value} field is set, so any other fields on the document are left alone.
 */
public class MongoConfigWriter implements ConfigWriter {

    private final MongoCollection<Document> collection;

    public MongoConfigWriter(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public void put(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        collection.updateOne(eq("_id", key), set(MongoChangeStreamSource.VALUE_FIELD, value),
                new UpdateOptions().upsert(true));
    }
}
