# Infrastructure & Deployment

> Docker setup, database configuration, environment profiles, and deployment notes.

## Local Development

### Prerequisites

- Java 21
- Maven 3.9+
- Docker + Docker Compose

### Start PostgreSQL Only

```bash
docker-compose up postgres -d
```

Then run the app via Maven (uses `application.properties` defaults):

```bash
mvn spring-boot:run
```

### Full Docker Stack

Builds the app image and starts both `budgeter-app` and `postgres`:

```bash
docker-compose up --build
```

App is accessible at `http://localhost:8080`.

## Docker Compose Services

| Service         | Image               | Port  | Notes                              |
| --------------- | ------------------- | ----- | ---------------------------------- |
| `budgeter-app`  | Built from `Dockerfile` | 8080 | Waits for `postgres` health check |
| `postgres`      | `postgres:15-alpine` | 5432 | Volume: `postgres_data`            |

The app container receives `SPRING_PROFILES_ACTIVE=docker` which activates `application-docker.properties` (if present) or falls back to environment variable overrides set in `docker-compose.yml`.

## Environment Profiles

| Profile      | Config file                              | Used for            |
| ------------ | ---------------------------------------- | ------------------- |
| (default)    | `application.properties`                 | Local dev (bare JVM)|
| `production` | `application-production.properties`      | Production deployment |
| `test`       | `src/test/resources/application-test.properties` | Automated tests (H2) |

## application.properties (dev defaults)

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/budgeter
spring.datasource.username=budgeter
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

## Database

- **Engine:** PostgreSQL 15
- **Database name:** `budgeter`
- **Default user/password:** `budgeter` / `password` (dev only)
- **Schema management:** `ddl-auto=update` — Hibernate auto-applies schema changes. No migration tool (Flyway/Liquibase) is currently used.
- **Persistent volume:** `postgres_data` in Docker Compose (survives `docker-compose down`; removed by `docker-compose down -v`)

## Dockerfile

Multi-stage or single-stage build that packages the Spring Boot fat JAR. Check `Dockerfile` at the repo root for current implementation. The app JAR is built with `mvn package` and copied into the image.

## CI/CD

`.github/workflows/basic-ci.yml` runs on push/PR:
- `mvn spotless:check` — formatting validation
- `mvn test` — all tests

No automated deployment pipeline is currently configured.

## Actuator / Health Checks

Spring Actuator is available in all environments:

```
GET /actuator/health       → service + DB health
GET /actuator/metrics      → JVM and app metrics
GET /actuator/prometheus   → Prometheus scrape format
```

The Docker Compose healthcheck for `postgres` uses `pg_isready` before starting the app container.
