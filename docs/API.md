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

All import endpoints accept `multipart/form-data` with a `file` field, are **idempotent**
(re-importing the same export skips already-imported records via a per-source external id), and
return a plain-text summary.

**Response `200 OK`** (example):
```
Imported 451 records from Trading212 file 'export.csv' (0 duplicates skipped, 394 rows not importable, 0 failed)
```

The counts come from `ImportResult`:

| Field               | Meaning                                                        |
| ------------------- | -------------------------------------------------------------- |
| `imported`          | Records created                                                |
| `skippedDuplicates` | Records skipped because their external id was already imported |
| `skippedRows`       | Rows not importable (cash movements, transfers, etc.)          |
| `failedRows`        | Rows that failed to parse                                      |

Columns are resolved by **header name**, not position, so layout changes between export versions
are tolerated. `400` is returned if the file is empty or the header doesn't match the expected
source.

### `POST /api/import/trading212`

Imports the Trading 212 transaction-history CSV. Only `Market buy` / `Limit buy` / `Stop sell` /
`Dividend …` and similar buy/sell/dividend actions become investment transactions; deposits,
withdrawals, interest, currency conversions, and tax adjustments are skipped. USD/GBP/CAD-priced
trades store an EUR exchange rate derived from the EUR gross total. Deduplicated by the export's
`ID` (dividend rows, which have no `ID`, use a deterministic synthetic key).

### `POST /api/import/traderepublic`

Imports the Trade Republic `Transaktionsexport.csv`. Only `category=TRADING` buys and sells become
investment transactions; cash movements, dividends-to-cash, and corporate actions are skipped.
Securities are matched by ISIN (`symbol` column), crypto by ticker. Prices are already in EUR.
Deduplicated by `transaction_id`.

### `POST /api/import/finanzguru`

Imports the Finanzguru "Alle Buchungen" XLSX. Negative `Betrag` → `Expense`, positive → `Income`.
Skipped: transfers between own accounts (`Analyse-Umbuchung=ja`) and the `Sparen` main category
(brokerage/savings transfers, already covered by the brokerage imports). German categories map to
`ExpenseCategory` (50/30/20) plus a `Tag`; contract rows (`Analyse-Vertrag=ja`) become `FIXED`.
Deduplicated by `Buchungs-ID`.

---

## Net Worth API

### `GET /api/net-worth`

Returns aggregated portfolio value across all imported investments. Position value is
`units × latestPrice × latestExchangeRate` (price from the most recent imported trade per asset;
no live price feed), falling back to cost basis when no price is known. Closed positions
(`totalUnits = 0`) are excluded.

**Response `200 OK` (`NetWorthResponse`):**
```json
{
  "totalValue": 4257.25,
  "currency": "EUR",
  "byBrokerage": { "Trading212": 3482.89, "TradeRepublic": 774.36 },
  "byAssetType": { "STOCK": 3482.89, "CRYPTO": 483.36, "DERIVATIVE": 291.00 },
  "positions": [
    {
      "ticker": "FWRA",
      "name": "Invesco FTSE All-World (Acc)",
      "isin": "IE000716YHJ7",
      "assetType": "INDEX_ETF",
      "brokerage": "Trading212",
      "units": 197.5544235,
      "latestPrice": 7.7750000000,
      "priceCurrency": "EUR",
      "valueEur": 1535.99
    }
  ]
}
```

Positions are sorted by `valueEur` descending.

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
