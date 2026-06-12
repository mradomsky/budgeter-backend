# Domain Model

> JPA entities, relationships, business rules, and important design decisions.

## Entity Hierarchy

### Budgeting Domain (`domain/entity/budgeting/`)

```
Transaction (abstract @MappedSuperclass)
├── id: Long (auto-generated)
├── amount: BigDecimal (not null)
├── name: String (not null)
├── description: String (nullable)
└── createdAt: LocalDateTime (auto-set)

Expense extends Transaction
├── category: ExpenseCategory (enum, not null)
└── tags: List<Tag> (element collection → expense_tags table)

Income extends Transaction
└── category: IncomeCategory (enum, not null)
```

**`ExpenseCategory` enum values:** `FOOD`, `TRANSPORT`, `UTILITIES`, `ENTERTAINMENT`, `HEALTH`, `SHOPPING`, `EDUCATION`, `OTHER`

**`IncomeCategory` enum values:** `SALARY`, `FREELANCE`, `INVESTMENT`, `GIFT`, `OTHER`

**`Tag` enum:** `ESSENTIAL`, `RECURRING`, `DISCRETIONARY`, `SUBSCRIPTION`, `REIMBURSABLE`

### Investment Domain (`domain/entity/investment/`)

```
Asset
├── id: Long
├── name: String (not null, unique)
├── ticker: String (nullable)
├── isin: String (nullable)
└── assetType: AssetType (enum)

Investment
├── id: Long
├── asset: Asset (ManyToOne)
├── totalCost: BigDecimal
├── totalUnits: BigDecimal
├── costBasis: BigDecimal (average cost per unit)
├── latestPrice: BigDecimal (nullable)
├── currency: Currency (enum)
├── brokerage: String (nullable)
└── transactions: List<InvestmentTransaction> (OneToMany, cascade ALL)

InvestmentTransaction
├── id: Long
├── investment: Investment (ManyToOne)
├── transactionType: InvestmentTransactionType (enum)
├── units: BigDecimal (not null)
├── pricePerUnit: BigDecimal (not null)
├── fees: BigDecimal (nullable)
├── currency: Currency (enum)
├── exchangeRate: BigDecimal (nullable)
├── name: String (nullable)
├── description: String (nullable)
└── transactionDate: LocalDateTime
```

**`AssetType` enum:** `STOCK`, `ETF`, `BOND`, `CRYPTO`, `COMMODITY`, `OTHER`

**`InvestmentTransactionType` enum:** `BUY`, `SELL`, `DIVIDEND`, `DEPOSIT`, `WITHDRAWAL`

**`Currency` enum:** ISO 4217 codes (e.g., `EUR`, `USD`, `GBP`)

**`InvestmentStyle` enum:** `GROWTH`, `VALUE`, `DIVIDEND`, `INDEX`, `SPECULATIVE`

## Key Business Rules

### Investment Cost Basis Tracking

`Investment.addTransaction(InvestmentTransaction)` recalculates `totalCost`, `totalUnits`, and `costBasis` whenever a transaction is added:

- **BUY**: `totalUnits += units`, `totalCost += units * pricePerUnit + fees`
- **SELL**: `totalUnits -= units`, `totalCost -= units * costBasis` (uses average cost method)
- `costBasis = totalCost / totalUnits` (recalculated after each transaction)

### Asset Deduplication

`InvestmentService.findOrCreateAsset()` looks up an asset by `name` (case-insensitive). If not found, it creates a new one. This prevents duplicate asset records when importing CSV files.

### Investment Deduplication

`InvestmentService` uses `investmentRepository.findByAsset(asset)` before creating a new `Investment`. One `Investment` record per `Asset`.

### Trading 212 CSV Import

`Trading212CsvImportService` parses a specific CSV format:
- Column indices are defined as constants (0-based)
- Date format: `yyyy-MM-dd HH:mm:ss.SSS`
- Only rows with valid `ACTION` values are imported (skips the header row)
- Each row delegates to `InvestmentService.create()` — same business rules apply

## Database Tables

| Table                      | Entity                  |
| -------------------------- | ----------------------- |
| `expense`                  | `Expense`               |
| `expense_tags`             | `Expense.tags` (join)   |
| `income`                   | `Income`                |
| `asset`                    | `Asset`                 |
| `investment`               | `Investment`            |
| `investment_transaction`   | `InvestmentTransaction` |

`spring.jpa.hibernate.ddl-auto=update` — schema is auto-maintained by Hibernate in dev/prod.
