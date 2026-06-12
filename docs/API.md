# API Reference

> REST endpoints, request/response schemas, pagination, and error handling.
> For interactive exploration, start the app and visit `http://localhost:8080/swagger-ui.html`.

## Base URL

```
http://localhost:8080
```

## Common Patterns

### Pagination

All list endpoints accept standard Spring Data `Pageable` query parameters:

| Parameter | Default | Description               |
| --------- | ------- | ------------------------- |
| `page`    | 0       | Zero-based page number    |
| `size`    | 20      | Items per page            |
| `sort`    | —       | e.g. `amount,desc`        |

Response body is a Spring `Page<T>` JSON object with `content`, `totalElements`, `totalPages`, etc.

### Swagger Annotations

Swagger annotations (`@Operation`, `@ApiResponses`, `@Parameter`) live on the **controller interface** in `domain/controller/`, not on the implementing `@RestController`. Do not add them to the impl class.

---

## Expense API

### `POST /api/expense`

Create a new expense.

**Request body (`ExpenseRequest`):**
```json
{
  "amount": 45.99,
  "name": "Grocery run",
  "category": "FOOD",
  "description": "Weekly groceries",
  "tags": ["ESSENTIAL", "RECURRING"]
}
```

**Response `201 Created` (`ExpenseResponse`):**
```json
{
  "id": 1,
  "amount": 45.99,
  "name": "Grocery run",
  "category": "FOOD",
  "description": "Weekly groceries",
  "tags": ["ESSENTIAL", "RECURRING"],
  "createdAt": "2026-06-13T10:00:00"
}
```

### `GET /api/expense/{id}`

Get a single expense by ID. Returns `404` if not found.

### `GET /api/expense`

List all expenses (paginated).

### `PUT /api/expense/{id}`

Replace an expense by ID. Same request body as `POST`. Returns `404` if not found.

### `DELETE /api/expense/{id}`

Delete an expense by ID. Returns `204 No Content`. Returns `404` if not found.

---

## Income API

Mirrors the Expense API under `/api/income`.

**`IncomeRequest` / `IncomeResponse`** differ only in `category` field, which uses `IncomeCategory` enum values: `SALARY`, `FREELANCE`, `INVESTMENT`, `GIFT`, `OTHER`.

---

## Import API

### `POST /api/import/trading212`

Upload a Trading 212 CSV export file to bulk-import investment transactions.

**Request:** `multipart/form-data` with a `file` field containing the CSV.

**Response `200 OK`:** Array of created `InvestmentTransaction` objects (or a summary — check `ImportController` for current shape).

**CSV format:** Trading 212 activity export. Expected columns (0-based index):

| Index | Column                          |
| ----- | ------------------------------- |
| 0     | Action                          |
| 1     | Time                            |
| 2     | ISIN                            |
| 3     | Ticker                          |
| 4     | Name                            |
| 5     | ID                              |
| 6     | Units                           |
| 7     | Price per unit                  |
| 8     | Currency (price)                |
| 9     | Exchange rate                   |
| 10    | Result                          |
| 11    | Currency (result)               |
| 12    | Gross total                     |
| 13    | Currency (gross total)          |
| 14    | Withholding tax                 |
| 15    | Currency (withholding tax)      |
| 16    | Currency conversion fee         |
| 17    | Currency (conversion fee)       |

---

## Error Responses

All errors return a JSON body with the following shape:

```json
{
  "timestamp": "2026-06-13T10:05:00",
  "status": 404,
  "error": "Not Found",
  "message": "Expense not found with id: 99",
  "path": "/api/expense"
}
```

| HTTP Status | Trigger                                                        |
| ----------- | -------------------------------------------------------------- |
| `400`       | Bean Validation failure (`@Valid` on request body)             |
| `404`       | `ExpenseNotFoundException`, `IncomeNotFoundException`, `InvestmentTransactionNotFoundException` |
| `500`       | Unhandled exception                                            |

Handled in `GlobalExceptionHandler` (`@RestControllerAdvice`).

---

## Monitoring

Spring Actuator endpoints (no auth required in dev):

| Endpoint                        | Description              |
| ------------------------------- | ------------------------ |
| `GET /actuator/health`          | Service health           |
| `GET /actuator/metrics`         | JVM + app metrics        |
| `GET /actuator/prometheus`      | Prometheus scrape target |
