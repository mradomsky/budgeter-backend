# Market Price Sync — Implementation Plan (Phase 2)

Status: **planned, not started**. This document is the hand-off spec for implementing daily
market-price synchronization. Read `PROJECT_OVERVIEW.md`, `DOMAIN_MODEL.md`, and
`CONVENTIONS.md` first; follow the existing interface/controller/service patterns exactly.

## Goal

Today the portfolio history (`GET /api/portfolio/history`, `PortfolioHistoryService`) values
positions at the **last imported trade price**, so the value line is flat between an asset's
trades. Phase 2 fetches real end-of-day close prices **once a day** from an external API and
stores them, so that:

1. The portfolio value chart shows true daily movement.
2. Net worth (`NetWorthService`) uses a current price instead of the last trade price.
3. Risk metrics become meaningful and get added to the summary: **Sharpe ratio, annualized
   volatility, max drawdown** (they were deliberately deferred — computing them on flat
   trade-price series produces garbage; see PortfolioHistoryService javadoc).

## Provider choice

Primary recommendation: **Twelve Data** (free tier: 800 credits/day, 8 req/min — enough for
a personal portfolio of ≤ ~50 assets refreshed daily with headroom for backfill spread over
days). Alternatives considered: Alpha Vantage (25 req/day free — too low for backfill),
Yahoo Finance (unofficial, breaks without notice), Stooq (free CSV, no key, but patchy ISIN
coverage). Keep the provider behind an interface so it can be swapped:

```
domain/service/PriceProviderInterface.java
    Optional<DailyPrice> getEodPrice(String symbol, LocalDate date);
    List<DailyPrice> getEodPriceRange(String symbol, LocalDate from, LocalDate to); // backfill
service/TwelveDataPriceProvider.java   (implements it; only class that knows the HTTP API)
```

FX rates (USD/GBP/GBX/CHF/CAD → EUR) come from **frankfurter.app** (ECB rates, free, no API
key, no meaningful rate limit). Separate `FxRateProviderInterface` + `FrankfurterFxProvider`.

## Configuration variables (all in `application.properties`, overridable by env)

| Property | Env var | Default | Purpose |
|---|---|---|---|
| `pricesync.enabled` | `PRICESYNC_ENABLED` | `false` | Master switch; keeps tests/CI offline |
| `pricesync.api-key` | `PRICESYNC_API_KEY` | — | Twelve Data key; **never commit it** |
| `pricesync.base-url` | `PRICESYNC_BASE_URL` | `https://api.twelvedata.com` | Swappable for tests (WireMock) |
| `pricesync.cron` | `PRICESYNC_CRON` | `0 30 5 * * *` | Daily 05:30 Europe/Berlin, after US close + ECB fix |
| `pricesync.timezone` | — | `Europe/Berlin` | Cron zone |
| `pricesync.backfill-start` | `PRICESYNC_BACKFILL_START` | `2021-01-01` | Earliest date the backfill walks back to (first trade is 2021-06-09) |
| `pricesync.rate-limit-per-minute` | — | `8` | Throttle guard for the free tier |

Production values go through the infrastructure repo's secret handling, same as the DB
password (see `INFRASTRUCTURE.md`).

## Data model (new entities, `domain/entity/investment/`)

```
AssetPrice
  id            bigint PK
  asset_id      FK -> asset, not null
  price_date    date, not null
  close         numeric(15,8), not null      -- in the instrument's currency
  currency      varchar(3), not null          -- Currency enum, same as Investment
  source        varchar(30), not null         -- "TWELVE_DATA", later others
  created_at
  UNIQUE (asset_id, price_date)

FxRate
  id            bigint PK
  rate_date     date, not null
  currency      varchar(3), not null          -- the non-EUR side
  rate_to_eur   numeric(15,8), not null       -- EUR per 1 unit of currency
  UNIQUE (rate_date, currency)
```

Repositories: `AssetPriceRepository` (`findByAssetIdAndPriceDateLessThanEqualOrderByPriceDateDesc`
— latest known price on/before a date; a bulk `findAllByPriceDateBetween` for the history
replay), `FxRateRepository` (same shape). `ddl-auto=update` creates the tables; no migration
tooling exists in this project.

### Symbol resolution (the hard part)

Twelve Data wants ticker symbols (`AAPL`, `VUSA.LSE`...). Assets have `ticker` (nullable —
Trade Republic imports are ISIN-only) and `isin`. Plan:

1. Add nullable `price_symbol` (varchar 20) column to `asset` — an explicit override that
   always wins.
2. Resolution order: `price_symbol` → `ticker` → Twelve Data symbol-search endpoint by ISIN
   (cache the result into `price_symbol` so the lookup happens once).
3. Assets that resolve to nothing get logged at WARN once per sync run and skipped — the
   replay falls back to trade prices for them (see below). Expose unresolved assets in the
   sync-status response so they are visible, not silent.

## Sync service

`service/PriceSyncService.java` + `domain/service/PriceSyncServiceInterface.java`:

- `@Scheduled(cron = "${pricesync.cron}", zone = "${pricesync.timezone}")` guarded by
  `pricesync.enabled`. Add `@EnableScheduling` on the application class.
- Daily run: for every `Investment` with `totalUnits > 0`, resolve symbol, fetch yesterday's
  EOD close, upsert into `asset_price` (skip if row exists — idempotent, safe to rerun).
  Fetch ECB rates for every non-EUR currency present in open positions into `fx_rate`.
- Manual trigger + backfill: `POST /api/prices/sync` (optional `from`/`to` query params) on a
  new `PriceSyncController`, returning counts `{assetsSynced, pricesStored, skippedExisting,
  unresolvedAssets, failed}` — mirror the `ImportResult` style. Backfill must respect the
  rate limit (sleep between calls; free tier = 8/min) and be resumable: because upserts are
  idempotent, rerunning continues where it left off.
- Weekends/holidays: markets closed, no data for those dates — that is fine. The replay uses
  "latest price on or before day X", so gaps carry forward automatically. Do NOT store
  carried-forward rows.
- Failure policy: one asset failing must not abort the run (same philosophy as the import
  services — per-row try/catch, count failures, log, continue).

## Changes to existing code

`PortfolioHistoryService.getHistory()`:

- Load all `AssetPrice` and `FxRate` rows once (two queries), group by asset/currency into
  `TreeMap<LocalDate, …>` for floor lookups.
- When valuing a position on day D: use the latest `AssetPrice` ≤ D if one exists, converted
  with the latest `FxRate` ≤ D (EUR assets skip FX); **fall back to the existing
  last-trade-price mechanism when no stored price exists** (pre-backfill dates, unresolved
  assets). This keeps the endpoint working identically when sync is disabled.
- Add to `PortfolioHistorySummary`: `sharpe`, `volatilityPct`, `maxDrawdownPct`
  (nullable — null when the series has fewer than ~30 real price observations, so the
  numbers never appear based on flat fallback data).
  - Daily returns must be **time-weighted**: r_t = (V_t − F_t) / V_{t−1}, where F_t is the
    net external flow on day t (buys at cost − sell proceeds), so deposits don't count as
    performance. Sum buy/sell `amount`s per day during the replay to get F_t.
  - Volatility = stddev(r) × √252. Sharpe = mean(r)/stddev(r) × √252, risk-free rate 0
    (document this choice in the javadoc). Max drawdown = max peak-to-trough decline of V.

`NetWorthService.toPosition()`: prefer latest `AssetPrice`+`FxRate` over
`investment.getLatestPrice()`; keep the old path as fallback. Response shape unchanged.

## Frontend (budgeter-frontend)

- `types.ts`/summary: add `sharpe`, `volatilityPct`, `maxDrawdownPct` (nullable) — extend the
  summary strip on `/portfolio` with three more tiles, rendered as `—` when null.
- Remove the "flat between trades" footnote once prices flow (or make it conditional: show
  only when the backend reports no synced prices — add a `pricesAvailable` boolean to the
  summary if you want this polish).
- Import page or portfolio page: a small "sync prices now" button hitting
  `POST /api/prices/sync` is enough UI for the manual trigger.

## Testing

- Unit-test the replay's price-source selection: stored price wins over trade price, floor
  lookup on gap days, fallback when no stored prices, FX conversion (follow
  `PortfolioHistoryServiceTest` patterns — Mockito, no Spring context).
- Unit-test TWR/Sharpe/drawdown math with a hand-computed 5-day fixture.
- Provider client: `@RestClientTest`-or-WireMock against canned Twelve Data / Frankfurter
  JSON; error and rate-limit responses.
- `PriceSyncService`: idempotency (second run stores nothing), per-asset failure isolation,
  disabled-flag short-circuit. Scheduling itself is config, not logic — don't test the cron.
- Every test must carry at least one assertion (project convention, enforced).

## Suggested implementation order

1. Entities + repositories (`AssetPrice`, `FxRate`, `asset.price_symbol`).
2. Provider interfaces + Twelve Data / Frankfurter clients with tests.
3. `PriceSyncService` + manual-trigger controller; backfill; verify locally with a real key.
4. Rewire `PortfolioHistoryService` valuation (fallback-safe) + tests.
5. Risk metrics (TWR returns, Sharpe/vol/drawdown) + tests.
6. `NetWorthService` rewire.
7. Frontend summary tiles + sync button.
8. Docs: update `API.md`, `DOMAIN_MODEL.md`, `PROJECT_OVERVIEW.md`; add the new properties
   to `INFRASTRUCTURE.md` secrets section.

Steps 1–3 ship value alone (data accrues from day one even before the replay uses it) —
land them first, backfill overnight, then do 4–8.
