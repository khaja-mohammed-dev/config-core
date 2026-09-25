package io.github.khajamohammeddev.configcore.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code config-core.*} in {@code application.yml}. */
@ConfigurationProperties(prefix = "config-core")
public class ConfigCoreProperties {

    /** Whether to start config-core at all. */
    private boolean enabled = true;

    private final Mongo mongo = new Mongo();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mongo getMongo() {
        return mongo;
    }

    public static class Mongo {

        /**
         * Connection string for the config store. Must point at a replica set, since change streams
         * need one, e.g. {@code mongodb://host:27017/mydb?replicaSet=rs0}.
         */
        private String uri;

        /** Database holding the config collection. Defaults to the database named in the URI. */
        private String database;

        /** Collection holding one document per config key. */
        private String collection = "config";

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }
    }
}
