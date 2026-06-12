# budgeter-backend — Claude Code Context

## Project Context

Personal finance REST API for Maksym Radomskyi. Tracks expenses, income, and investment portfolio. Built with Spring Boot 3 + PostgreSQL, deployed via Docker.

## Read Before Making Changes

1. **[docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md)** — architecture, tech stack, package structure, commands. Start here.
2. **Then read the relevant detail doc:**
   - [docs/DOMAIN_MODEL.md](docs/DOMAIN_MODEL.md) — JPA entities, relationships, business rules
   - [docs/API.md](docs/API.md) — REST endpoints, request/response schemas, error handling
   - [docs/CONVENTIONS.md](docs/CONVENTIONS.md) — coding patterns, formatting, testing
   - [docs/INFRASTRUCTURE.md](docs/INFRASTRUCTURE.md) — Docker, PostgreSQL, deployment

## Documentation Maintenance

After changes, update corresponding doc(s):

- New/removed endpoints → PROJECT_OVERVIEW.md + API.md
- Entity or relationship changes → DOMAIN_MODEL.md
- New services, patterns, or tooling → CONVENTIONS.md
- Docker or deployment changes → INFRASTRUCTURE.md
- Structural changes (packages, moved files) → PROJECT_OVERVIEW.md

Update "Last updated" date in PROJECT_OVERVIEW.md on significant changes.

## Coding Style

### Formatting (Spotless-enforced, Palantir Java Format)
- Run `mvn spotless:apply` to auto-fix; `mvn spotless:check` in CI
- 4-space indentation (enforced by formatter)

### Java / Spring Boot
- Java 21; Spring Boot 3.x
- Lombok: `@Data`, `@SuperBuilder`, `@RequiredArgsConstructor`, `@Slf4j` — do not write boilerplate manually
- Controller → Service → Repository layering; never skip layers
- Controllers implement interface from `domain/controller/` (Swagger annotations live on the interface, not the impl)
- Services implement interface from `domain/service/`
- `@Transactional(readOnly = true)` at class level; `@Transactional` on mutating methods
- Use `BigDecimal` for all monetary and unit values — never `double` or `float`

### Validation & Error Handling
- Bean Validation (`@Valid`, `@NotNull`, etc.) on DTOs
- `GlobalExceptionHandler` handles `*NotFoundException` → 404, `MethodArgumentNotValidException` → 400
- Throw domain-specific exceptions (`ExpenseNotFoundException`, etc.) from service layer

### Testing
- Unit tests: JUnit 5 + Mockito; class suffix `Test` (e.g., `ExpenseServiceTest`)
- Integration tests: `@SpringBootTest` + H2 in-memory; class suffix `IntegrationTest`
- Test properties in `src/test/resources/application-test.properties`

## Quick Commands

```bash
# Dev
docker-compose up postgres -d   # Start PostgreSQL
mvn spring-boot:run             # Run app (localhost:8080)

# Build & test
mvn test                        # All tests
mvn test jacoco:report          # Tests + coverage report
mvn package                     # Build fat JAR → target/

# Code quality
mvn spotless:check              # Check formatting (CI)
mvn spotless:apply              # Auto-fix formatting

# Docker
docker-compose up --build       # Build image + start all services
docker-compose down             # Stop all services

# API docs (app must be running)
open http://localhost:8080/swagger-ui.html
```
