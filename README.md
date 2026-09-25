# config-core

> Push-based, restart-free feature flags and configuration for Spring Boot — using the database you already run.

**Status:** 🚧 Early development (pre-0.1.0). Not ready for production use.

## What it does

`config-core` keeps feature flags and non-secret properties in a database collection/table, loads them into an
in-memory cache at startup, and updates that cache **instantly across every running instance** when a value changes —
using the database's native change notifications (MongoDB Change Streams; PostgreSQL `LISTEN/NOTIFY` planned).
No restarts, no polling, no message broker, no new infrastructure.

## Who it's for

Java / Spring Boot teams already running **MongoDB** (PostgreSQL support planned) who want fast, self-hosted flag and
config propagation without operating a separate config server.

**Not for:** secrets/credentials (use Vault or your cloud secrets manager), or teams needing percentage rollouts /
A/B experimentation (see Unleash or LaunchDarkly).

## Modules

| Module | Purpose |
|---|---|
| `config-core-api` | Storage-agnostic contracts + in-memory cache |
| `config-core-mongo` | MongoDB Change Streams backend |
| `config-core-spring-boot-starter` | Spring Boot auto-configuration: `ConfigService` bean + `ConfigChangedEvent` |

## Usage (Spring Boot)

Add `config-core-spring-boot-starter` to your dependencies, then point it at a MongoDB replica set:

```yaml
config-core:
  mongo:
    uri: mongodb://localhost:27017/mydb?replicaSet=rs0
    collection: config        # optional, defaults to "config"
```

Store one document per key: `{ "_id": "feature.x.enabled", "value": true }`.

```java
@Service
class Checkout {
    private final ConfigService config;

    Checkout(ConfigService config) { this.config = config; }

    void run() {
        if (config.getBoolean("feature.x.enabled", false)) { /* ... */ }
    }

    @EventListener
    void onChange(ConfigChangedEvent e) {
        // e.key(), e.oldValue(), e.newValue(), fired within ~1s of the change in Mongo
    }
}
```

config-core uses its own connection and does not replace your application's `MongoClient` bean.

## Local development

Requirements: JDK 17+, Maven 3.9+, Docker.

```bash
docker compose up -d        # local single-node Mongo replica set
mvn verify                  # build + unit + integration tests
```

## License

Apache License 2.0
