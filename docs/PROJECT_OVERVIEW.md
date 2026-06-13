# budgeter-backend — Project Overview

> **Last updated:** 2026-06-13
> **Purpose:** AI/developer onboarding document. Read this first to build context about the project.

## What Is This?

A personal finance REST API for **Maksym Radomskyi**. Tracks:

- **Expenses** — categorized spending records with optional tags
- **Income** — categorized income records
- **Investments** — portfolio of assets (stocks, ETFs, crypto, etc.) with full transaction history and cost-basis tracking
- **Imports** — idempotent bulk import from Trading 212 (CSV), Trade Republic (CSV), and Finanzguru (XLSX) exports
- **Net worth** — aggregated portfolio value by brokerage and asset type

The API is consumed by a personal budgeting frontend. Swagger UI is available at `/swagger-ui.html` when running locally.

## Tech Stack

| Layer       | Technology                               | Notes                                                    |
| ----------- | ---------------------------------------- | -------------------------------------------------------- |
| Framework   | **Spring Boot 3.5.5**                    | Java 21                                                  |
| Web         | **Spring MVC**                           | REST API, JSON serialization via Jackson                 |
| Persistence | **Spring Data JPA + Hibernate**          | PostgreSQL in prod, H2 in tests                          |
| Validation  | **Jakarta Bean Validation**              | `@Valid` on request DTOs                                 |
| API Docs    | **SpringDoc OpenAPI 2 (Swagger UI)**     | Interface-driven Swagger annotations                     |
| Code Gen    | **Lombok**                               | `@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor` |
| Formatting  | **Spotless + Palantir Java Format**      | Enforced at `validate` phase in Maven                    |
| Testing     | **JUnit 5 + Mockito + Spring Boot Test** | H2 for integration tests                                 |
| CSV         | **OpenCSV 5.9**                          | Trading 212 + Trade Republic import                      |
| XLSX        | **Apache POI 5.3.0**                     | Finanzguru import                                        |
| Monitoring  | **Spring Actuator**                      | `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` |

## Package Structure

```
src/main/java/com/radomskyi/budgeter/
├── BudgeterApplication.java          # Spring Boot entry point
│
├── controller/                       # @RestController implementations
│   ├── ExpenseController.java        # /api/expense
│   ├── IncomeController.java         # /api/income
│   ├── ImportController.java         # /api/import/{trading212,traderepublic,finanzguru}
│   └── NetWorthController.java       # /api/net-worth
│
├── domain/                           # Domain model (interfaces + entities)
│   ├── controller/                   # Controller interfaces (Swagger annotations here)
│   │   ├── BaseController.java       # Generic CRUD interface (create, getById, getAll, update, delete)
│   │   ├── ExpenseControllerInterface.java
│   │   ├── IncomeControllerInterface.java
│   │   ├── ImportControllerInterface.java
│   │   └── NetWorthControllerInterface.java
│   ├── entity/
│   │   ├── budgeting/                # Expense, Income, Transaction (base), Tag, *Category enums
│   │   └── investment/               # Asset, Investment, InvestmentTransaction, enums
│   └── service/                      # Service interfaces
│       ├── BaseService.java
│       ├── ExpenseServiceInterface.java
│       ├── IncomeServiceInterface.java
│       ├── InvestmentServiceInterface.java
│       └── NetWorthServiceInterface.java
│
├── dto/                              # Request/response DTOs
│   ├── ExpenseRequest.java
│   ├── ExpenseResponse.java
│   ├── IncomeRequest.java
│   ├── IncomeResponse.java
│   ├── InvestmentTransactionRequest.java
│   ├── InvestmentTransactionResponse.java
│   ├── ImportResult.java
│   ├── NetWorthPosition.java
│   └── NetWorthResponse.java
│
├── exception/                        # Domain exceptions + global handler
│   ├── ExpenseNotFoundException.java
│   ├── IncomeNotFoundException.java
│   ├── InvestmentTransactionNotFoundException.java
│   └── GlobalExceptionHandler.java   # @RestControllerAdvice
│
├── repository/                       # Spring Data JPA repositories
│   ├── ExpenseRepository.java
│   ├── IncomeRepository.java
│   ├── AssetRepository.java
│   ├── InvestmentRepository.java
│   └── InvestmentTransactionRepository.java
│
└── service/                          # @Service implementations
    ├── ExpenseService.java
    ├── IncomeService.java
    ├── InvestmentService.java
    ├── NetWorthService.java
    ├── Trading212ImportService.java
    ├── TradeRepublicImportService.java
    └── FinanzguruImportService.java
```

## REST Endpoints Summary

| Method | Path                          | Description                              |
| ------ | ----------------------------- | ---------------------------------------- |
| POST   | `/api/expense`                | Create expense                           |
| GET    | `/api/expense/{id}`           | Get expense by ID                        |
| GET    | `/api/expense?page=&size=`    | List expenses (paginated)                |
| PUT    | `/api/expense/{id}`           | Update expense                           |
| DELETE | `/api/expense/{id}`           | Delete expense                           |
| POST   | `/api/income`                 | Create income                            |
| GET    | `/api/income/{id}`            | Get income by ID                         |
| GET    | `/api/income?page=&size=`     | List income (paginated)                  |
| PUT    | `/api/income/{id}`            | Update income                            |
| DELETE | `/api/income/{id}`            | Delete income                            |
| POST   | `/api/import/trading212`      | Import Trading 212 transactions CSV      |
| POST   | `/api/import/traderepublic`   | Import Trade Republic transactions CSV   |
| POST   | `/api/import/finanzguru`      | Import Finanzguru "Alle Buchungen" XLSX  |
| GET    | `/api/net-worth`              | Aggregated portfolio value               |

See [docs/API.md](API.md) for full request/response schemas.

## Quick Commands

```bash
docker-compose up postgres -d   # Start PostgreSQL
mvn spring-boot:run             # Run app on localhost:8080
mvn test                        # All tests
mvn spotless:apply              # Fix formatting
mvn package                     # Build fat JAR
docker-compose up --build       # Full Docker stack
```
