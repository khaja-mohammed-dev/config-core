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

## Local development

Requirements: JDK 17+, Maven 3.9+, Docker.

```bash
docker compose up -d        # local single-node Mongo replica set
mvn verify                  # build + unit + integration tests
```

## License

Apache License 2.0
