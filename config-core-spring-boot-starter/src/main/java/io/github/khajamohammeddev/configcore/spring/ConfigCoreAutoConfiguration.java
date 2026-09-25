package io.github.khajamohammeddev.configcore.spring;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import io.github.khajamohammeddev.configcore.api.ConfigCache;
import io.github.khajamohammeddev.configcore.api.ConfigChangeSource;
import io.github.khajamohammeddev.configcore.api.ConfigWriter;
import io.github.khajamohammeddev.configcore.mongo.MongoChangeStreamSource;
import io.github.khajamohammeddev.configcore.mongo.MongoConfigWriter;
import org.bson.Document;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires config-core into a Spring Boot application: connects to the config store, loads it into
 * memory and exposes it as a {@link ConfigService} bean.
 *
 * <p>Applications can replace the backing store by defining their own {@link ConfigChangeSource}
 * bean (plus a {@link ConfigWriter} if they want the internal update endpoint); the Mongo
 * connection is then not created at all.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "config-core", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ConfigCoreProperties.class)
public class ConfigCoreAutoConfiguration {

    /**
     * Starts watching the store before returning, so the config is fully loaded by the time any
     * other bean gets the service injected.
     */
    @Bean
    public ConfigService configService(ConfigChangeSource configCoreChangeSource, ApplicationEventPublisher publisher) {
        ConfigCache cache = new ConfigCache();
        configCoreChangeSource.start(new EventPublishingListener(cache, publisher));
        return new ConfigService(cache);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(ConfigChangeSource.class)
    static class MongoSourceConfiguration {

        // Deliberately not a MongoClient bean: that would stop Spring Boot from creating the
        // application's own MongoClient, silently pointing its data access at the config store.
        @Bean(destroyMethod = "close")
        ConfigCoreMongoClient configCoreMongoClient(ConfigCoreProperties properties) {
            ConfigCoreProperties.Mongo mongo = properties.getMongo();
            if (mongo.getUri() == null || mongo.getUri().isBlank()) {
                throw new IllegalStateException(
                        "config-core.mongo.uri is not set. Point it at your MongoDB replica set, e.g. "
                                + "mongodb://localhost:27017/mydb?replicaSet=rs0, or set config-core.enabled=false.");
            }
            ConnectionString uri = new ConnectionString(mongo.getUri());
            String database = mongo.getDatabase() != null ? mongo.getDatabase() : uri.getDatabase();
            if (database == null || database.isBlank()) {
                throw new IllegalStateException(
                        "No config database: add it to config-core.mongo.uri (mongodb://host/mydb?...) "
                                + "or set config-core.mongo.database.");
            }
            return new ConfigCoreMongoClient(MongoClients.create(uri), database);
        }

        @Bean(destroyMethod = "stop")
        ConfigChangeSource configCoreChangeSource(ConfigCoreMongoClient client, ConfigCoreProperties properties) {
            return new MongoChangeStreamSource(configCollection(client, properties));
        }

        @Bean
        @ConditionalOnMissingBean
        ConfigWriter configCoreWriter(ConfigCoreMongoClient client, ConfigCoreProperties properties) {
            return new MongoConfigWriter(configCollection(client, properties));
        }

        private static MongoCollection<Document> configCollection(
                ConfigCoreMongoClient client, ConfigCoreProperties properties) {
            return client.client().getDatabase(client.database()).getCollection(properties.getMongo().getCollection());
        }
    }

    /** config-core's own connection, kept out of the application's {@code MongoClient} bean slot. */
    record ConfigCoreMongoClient(MongoClient client, String database) implements AutoCloseable {
        @Override
        public void close() {
            client.close();
        }
    }
}
