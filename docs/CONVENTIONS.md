# Conventions & Technical Decisions

> Coding patterns, architectural decisions, and tooling choices worth understanding.

## Layered Architecture

```
Controller (HTTP in/out)
    └── Service (business logic, transactions)
            └── Repository (data access, Spring Data JPA)
```

- Controllers never call repositories directly.
- Services never return JPA entities to controllers — always map to DTOs.
- DTOs are flat data containers with no business logic.

## Interface-Driven Design

Every controller and service has a corresponding interface in `domain/controller/` or `domain/service/`:

```
domain/controller/BaseController<T, R>   ←  generic CRUD interface
    └── domain/controller/ExpenseControllerInterface
            └── controller/ExpenseController  (@RestController impl)

domain/service/BaseService<T, R>
    └── domain/service/ExpenseServiceInterface
            └── service/ExpenseService  (@Service impl)
```

**Swagger annotations belong on the interface, not the impl.** The `@RestController` class just delegates to the service.

## Transactions

- Class-level `@Transactional(readOnly = true)` on all `@Service` classes.
- Override with `@Transactional` on individual mutating methods (`create`, `update`, `delete`).
- Do NOT annotate controllers with `@Transactional`.

## Monetary Values

Always use `BigDecimal` for amounts, prices, units, fees, and exchange rates. Never use `double` or `float` — they lose precision with financial calculations.

Column precision conventions:
- Money amounts: `precision = 15, scale = 2`
- Unit quantities: `precision = 15, scale = 8`
- Prices per unit: `precision = 15, scale = 8`

## Lombok Usage

Prefer Lombok annotations over hand-written boilerplate:

| Annotation              | Use case                                     |
| ----------------------- | -------------------------------------------- |
| `@Data`                 | Entities and DTOs (getters, setters, equals, hashCode, toString) |
| `@SuperBuilder`         | Entities with inheritance (use on both parent and child) |
| `@Builder`              | Non-inherited classes                        |
| `@RequiredArgsConstructor` | Services, controllers (injects `final` fields) |
| `@Slf4j`                | Every service and controller (`log.info(...)`) |
| `@NoArgsConstructor` + `@AllArgsConstructor` | JPA entities (required by Hibernate) |

Entity inheritance chains (e.g., `Transaction → Expense`) require `@SuperBuilder` on **all** classes in the chain.

## Logging

All services and controllers use `@Slf4j`. Log at `INFO` level for:
- Incoming requests (controller)
- Business operations start/end (service)
- Key identifiers in results

Do not log sensitive data (passwords, personal info).

## Code Formatting

Spotless with Palantir Java Format is enforced at the `validate` phase of every Maven build. The CI pipeline runs `mvn spotless:check`.

Before committing: `mvn spotless:apply`

## Testing Strategy

### Unit Tests (`*Test.java`)
- JUnit 5 + Mockito
- Test the service layer in isolation — mock all repositories
- Placed in `src/test/java/.../service/` or `controller/`
- No Spring context loaded

### Integration Tests (`*IntegrationTest.java`)
- `@SpringBootTest` + H2 in-memory database
- `application-test.properties` configures H2 datasource
- Tests full stack: controller → service → repository → H2
- Placed in `src/test/java/.../controller/` (for controller integration tests)

### Repository Tests (`*RepositoryTest.java`)
- `@DataJpaTest` with H2
- Test custom queries and JPA behaviour

### Test Properties

`src/test/resources/application-test.properties` overrides datasource to use H2:
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
```

## Validation

- Use Bean Validation annotations on DTO fields: `@NotNull`, `@NotBlank`, `@DecimalMin`, `@Size`, etc.
- Annotate the controller method parameter with `@Valid` to trigger validation.
- `GlobalExceptionHandler` maps `MethodArgumentNotValidException` → `400 Bad Request`.

## Exception Handling

Domain exceptions (e.g., `ExpenseNotFoundException`) extend `RuntimeException`. Throw from the service layer when an entity is not found. `GlobalExceptionHandler` catches them and returns structured JSON error responses.

To add a new exception type:
1. Create `*NotFoundException extends RuntimeException` in `exception/`
2. Add a handler method in `GlobalExceptionHandler`
