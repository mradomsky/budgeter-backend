# Domain Model

> JPA entities, relationships, business rules, and important design decisions.

## Entity Hierarchy

### Budgeting Domain (`domain/entity/budgeting/`)

```
Transaction (abstract @MappedSuperclass)
├── id: Long (auto-generated)
├── amount: BigDecimal (not null)
├── name: String (nullable)
├── description: String (nullable)
├── externalId: String (nullable, unique) — source-system id for import deduplication
├── transactionDate: LocalDateTime (nullable) — when it happened at the source
├── createdAt: LocalDateTime (auto-set) — when the row was imported
└── updatedAt: LocalDateTime (auto-set)

Expense extends Transaction
├── category: ExpenseCategory (enum, not null)
└── tags: List<Tag> (element collection → expense_tags table)

Income extends Transaction
├── category: IncomeCategory (enum, not null)
└── tags: List<Tag> (element collection → income_tags table)
```

**`ExpenseCategory` enum values:** `FIXED`, `NEEDS`, `WANTS` (a 50/30/20-style budgeting split)

**`IncomeCategory` enum values:** `SALARY`, `FREELANCE`, `INVESTMENTS`, `BUSINESS`, `GIFTS_AND_BONUSES`, `RENTAL`, `GOVERNMENT_BENEFITS`, `OTHER_INCOME`

**`Tag` enum:** `FOOD`, `BARS_AND_RESTAURANTS`, `TRANSPORT`, `ENTERTAINMENT`, `SHOPPING`, `HEALTH`, `EDUCATION`, `HOUSING`, `CLOTHING`, `UTILITIES`, `INSURANCE`, `PETS`, `SUBSCRIPTIONS`, `SPORTS_AND_HOBBIES`, `PERSONAL_CARE`, `GIFTS`, `DONATIONS`, `BANKING_AND_TAXES`, `TRAVEL`, `VICES`, `DEBT`, `OTHER`

### Investment Domain (`domain/entity/investment/`)

```
Asset
├── id: Long
├── name: String (not null)
├── ticker: String (nullable) — Trade Republic identifies securities by ISIN only
├── isin: String (nullable)
├── assetType: AssetType (enum, not null)
└── investmentStyle: InvestmentStyle (enum, not null)

Investment
├── id: Long
├── asset: Asset (ManyToOne)
├── totalCost: BigDecimal
├── totalUnits: BigDecimal
├── costBasis: BigDecimal (average cost per unit)
├── latestPrice: BigDecimal (nullable) — price of the most recent imported BUY/SELL
├── latestExchangeRate: BigDecimal (nullable) — EUR per instrument-currency unit at that trade
├── currency: Currency (enum)
├── brokerage: String (nullable)
└── transactions: List<InvestmentTransaction> (OneToMany, cascade ALL)

InvestmentTransaction extends Transaction
├── transactionType: InvestmentTransactionType (enum)
├── investment: Investment (ManyToOne)
├── units: BigDecimal (not null)
├── pricePerUnit: BigDecimal (not null)
├── fees: BigDecimal (nullable)
├── currency: Currency (enum)
├── exchangeRate: BigDecimal (nullable)
└── realizedGainLoss: BigDecimal (nullable, set on SELL)
```

`InvestmentTransaction` inherits `id`, `amount`, `name`, `description`, `externalId`,
`transactionDate`, `createdAt`, `updatedAt` from `Transaction`.

**`AssetType` enum:** `INDEX_ETF`, `STOCK`, `BOND`, `COMMODITY`, `CRYPTO`, `DERIVATIVE`

**`InvestmentTransactionType` enum:** `BUY`, `SELL`, `DIVIDEND`

**`InvestmentStyle` enum:** `GROWTH`, `VALUE`, `DIVIDEND`, `INDEX`, `SPECULATIVE`

**`Currency` enum:** `USD`, `EUR`, `GBP`, `GBX`, `CHF`, `CAD`

## Key Business Rules

### Investment Cost Basis Tracking

`Investment.addTransaction(InvestmentTransaction)` recalculates `totalCost`, `totalUnits`, and `costBasis` whenever a transaction is added:

- **BUY**: `totalUnits += units`, `totalCost += units * pricePerUnit + fees`
- **SELL**: `totalUnits -= units`, `totalCost -= units * costBasis` (uses average cost method); `totalUnits`/`totalCost` are clamped at zero so partial-window imports (a sell whose matching buys predate the export) don't violate the non-negative invariants
- `costBasis = totalCost / totalUnits` (recalculated after each transaction)
- `latestPrice`/`latestExchangeRate` are updated only on BUY/SELL (not DIVIDEND, where `pricePerUnit` is the per-share payout, not market price)

### Net Worth Valuation

`Investment.getCurrentValueEur()` = `totalUnits × latestPrice × latestExchangeRate`, falling back to `totalCost` when no price is known. There is no live price feed — values reflect the most recent imported trade per asset. `NetWorthService` aggregates open positions (`totalUnits > 0`) by brokerage and asset type.

### Asset Deduplication

`InvestmentService.findOrCreateAsset()` looks up an asset by ISIN first, then ticker. If not found, it creates a new one (asset type taken from the import request, defaulting to `STOCK`). This prevents duplicate asset records across imports.

### Investment Deduplication

`InvestmentService` uses `investmentRepository.findByAsset(asset)` before creating a new `Investment`. One `Investment` record per `Asset`.

### Import Deduplication

Every import is idempotent. Each source record carries a stable id stored in `Transaction.externalId` (Trading 212 `ID`, Trade Republic `transaction_id`, Finanzguru `Buchungs-ID`); importers call `existsByExternalId` and skip records already present. See [API.md](API.md) for per-source parsing rules.

### Import Services

- `Trading212ImportService` — Trading 212 transaction CSV. Header-name-based column mapping (tolerates layout changes). Buy/sell/dividend only; derives EUR exchange rate from the EUR gross total.
- `TradeRepublicImportService` — Trade Republic `Transaktionsexport.csv`. `category=TRADING` buys/sells only; prices already in EUR.
- `FinanzguruImportService` — Finanzguru "Alle Buchungen" XLSX (Apache POI). Negative → `Expense`, positive → `Income`; skips transfers and `Sparen`.

## Database Tables

| Table                      | Entity                  |
| -------------------------- | ----------------------- |
| `expense`                  | `Expense`               |
| `expense_tags`             | `Expense.tags` (join)   |
| `income`                   | `Income`                |
| `income_tags`              | `Income.tags` (join)    |
| `asset`                    | `Asset`                 |
| `investment`               | `Investment`            |
| `investment_transaction`   | `InvestmentTransaction` |

`spring.jpa.hibernate.ddl-auto=update` — schema is auto-maintained by Hibernate in dev/prod.
