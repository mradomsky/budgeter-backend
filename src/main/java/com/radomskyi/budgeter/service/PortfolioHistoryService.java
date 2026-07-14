package com.radomskyi.budgeter.service;

import com.radomskyi.budgeter.domain.entity.investment.AssetPrice;
import com.radomskyi.budgeter.domain.entity.investment.Currency;
import com.radomskyi.budgeter.domain.entity.investment.FxRate;
import com.radomskyi.budgeter.domain.entity.investment.InvestmentTransaction;
import com.radomskyi.budgeter.domain.service.PortfolioHistoryServiceInterface;
import com.radomskyi.budgeter.dto.PortfolioHistoryPoint;
import com.radomskyi.budgeter.dto.PortfolioHistoryResponse;
import com.radomskyi.budgeter.dto.PortfolioHistorySummary;
import com.radomskyi.budgeter.repository.AssetPriceRepository;
import com.radomskyi.budgeter.repository.FxRateRepository;
import com.radomskyi.budgeter.repository.InvestmentTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconstructs daily portfolio value history by replaying all imported investment transactions in
 * chronological order. Positions are valued with synced end-of-day market prices (asset_price +
 * fx_rate, latest row on/before each day) when available, falling back to the last trade price for
 * dates before the backfill or for assets the price sync could not resolve. All amounts are EUR;
 * buy cost includes fees (consistent with InvestmentTransaction.amount).
 *
 * <p>Risk metrics (Sharpe, volatility, max drawdown) are computed from time-weighted daily returns
 * — same-day buy/sell cash flows are stripped out, so deposits don't count as performance. The
 * risk-free rate is 0. They are null until at least {@link #MIN_PRICED_DAYS} days were valued from
 * synced prices, because trade-price-only series are flat between trades and would fake near-zero
 * volatility.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PortfolioHistoryService implements PortfolioHistoryServiceInterface {

    private static final int MONEY_SCALE = 2;
    private static final int UNIT_SCALE = 8;
    private static final int MIN_PRICED_DAYS = 30;
    private static final double TRADING_DAYS_PER_YEAR = 252.0;
    private static final BigDecimal GBX_PER_GBP = BigDecimal.valueOf(100);

    private final InvestmentTransactionRepository investmentTransactionRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final FxRateRepository fxRateRepository;

    @Override
    public PortfolioHistoryResponse getHistory() {
        log.info("Reconstructing portfolio history");

        List<InvestmentTransaction> transactions = investmentTransactionRepository.findAllWithInvestment().stream()
                .sorted(Comparator.comparing(this::effectiveDate))
                .toList();

        if (transactions.isEmpty()) {
            return PortfolioHistoryResponse.builder()
                    .summary(emptySummary())
                    .points(List.of())
                    .build();
        }

        Map<Long, TreeMap<LocalDate, BigDecimal>> pricesByAsset = loadPrices(transactions);
        Map<Currency, TreeMap<LocalDate, BigDecimal>> fxByCurrency = loadFxRates();

        Map<Long, PositionState> positions = new HashMap<>();
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal dividends = BigDecimal.ZERO;
        BigDecimal totalBuyCost = BigDecimal.ZERO;

        List<PortfolioHistoryPoint> points = new ArrayList<>();
        List<BigDecimal> dailyFlows = new ArrayList<>(); // net external cash flow per day, for TWR
        int pricedDays = 0;

        LocalDate firstDay = effectiveDate(transactions.get(0));
        LocalDate lastDay = LocalDate.now();

        int txIndex = 0;
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            BigDecimal flow = BigDecimal.ZERO;
            while (txIndex < transactions.size()
                    && !effectiveDate(transactions.get(txIndex)).isAfter(day)) {
                InvestmentTransaction tx = transactions.get(txIndex++);
                PositionState position = positions.computeIfAbsent(
                        tx.getInvestment().getId(), id -> new PositionState(tx.getInvestment()));

                switch (tx.getTransactionType()) {
                    case BUY -> {
                        position.applyBuy(tx);
                        totalBuyCost = totalBuyCost.add(txAmount(tx));
                        flow = flow.add(txAmount(tx));
                    }
                    case SELL -> {
                        realized = realized.add(position.applySell(tx));
                        flow = flow.subtract(txAmount(tx));
                    }
                    case DIVIDEND -> dividends = dividends.add(txAmount(tx));
                }
            }

            BigDecimal value = BigDecimal.ZERO;
            BigDecimal invested = BigDecimal.ZERO;
            boolean anyMarketPrice = false;
            for (PositionState position : positions.values()) {
                invested = invested.add(position.investedEur);
                if (position.units.compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // closed position contributes nothing and must not count as priced
                }
                BigDecimal marketValue = marketValueEur(position, day, pricesByAsset, fxByCurrency);
                if (marketValue != null) {
                    value = value.add(marketValue);
                    anyMarketPrice = true;
                } else {
                    value = value.add(position.tradeValueEur());
                }
            }
            if (anyMarketPrice) {
                pricedDays++;
            }
            value = value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            invested = invested.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal gain = value.subtract(invested);

            points.add(PortfolioHistoryPoint.builder()
                    .date(day)
                    .valueEur(value)
                    .investedEur(invested)
                    .gainEur(gain)
                    .gainPct(percentage(gain, invested))
                    .realizedEur(realized.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                    .dividendsEur(dividends.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                    .build());
            dailyFlows.add(flow);
        }

        PortfolioHistoryPoint last = points.get(points.size() - 1);
        BigDecimal totalEarned = last.getGainEur().add(last.getRealizedEur()).add(last.getDividendsEur());
        PortfolioHistorySummary summary = PortfolioHistorySummary.builder()
                .currentValueEur(last.getValueEur())
                .investedEur(last.getInvestedEur())
                .unrealizedGainEur(last.getGainEur())
                .unrealizedGainPct(last.getGainPct())
                .realizedGainEur(last.getRealizedEur())
                .dividendsEur(last.getDividendsEur())
                .totalReturnPct(percentage(totalEarned, totalBuyCost))
                .build();
        if (pricedDays >= MIN_PRICED_DAYS) {
            applyRiskMetrics(summary, points, dailyFlows);
        }

        return PortfolioHistoryResponse.builder()
                .summary(summary)
                .points(points)
                .build();
    }

    /** All synced prices for the assets involved, keyed by asset id then date */
    private Map<Long, TreeMap<LocalDate, BigDecimal>> loadPrices(List<InvestmentTransaction> transactions) {
        List<Long> assetIds = transactions.stream()
                .map(tx -> tx.getInvestment().getAsset().getId())
                .distinct()
                .toList();
        Map<Long, TreeMap<LocalDate, BigDecimal>> prices = new HashMap<>();
        for (AssetPrice price : assetPriceRepository.findAllByAssetIds(assetIds)) {
            prices.computeIfAbsent(price.getAsset().getId(), id -> new TreeMap<>())
                    .put(price.getPriceDate(), price.getClose());
        }
        return prices;
    }

    private Map<Currency, TreeMap<LocalDate, BigDecimal>> loadFxRates() {
        Map<Currency, TreeMap<LocalDate, BigDecimal>> rates = new HashMap<>();
        for (FxRate rate : fxRateRepository.findAll()) {
            rates.computeIfAbsent(rate.getCurrency(), c -> new TreeMap<>())
                    .put(rate.getRateDate(), rate.getRateToEur());
        }
        return rates;
    }

    /**
     * EUR value of the position on the given day using synced market data, or null when no synced
     * price (or required FX rate) is available and the caller must fall back to trade prices.
     */
    private BigDecimal marketValueEur(
            PositionState position,
            LocalDate day,
            Map<Long, TreeMap<LocalDate, BigDecimal>> pricesByAsset,
            Map<Currency, TreeMap<LocalDate, BigDecimal>> fxByCurrency) {
        TreeMap<LocalDate, BigDecimal> prices = pricesByAsset.get(position.assetId);
        if (prices == null) {
            return null;
        }
        Map.Entry<LocalDate, BigDecimal> priceEntry = prices.floorEntry(day);
        if (priceEntry == null) {
            return null;
        }

        BigDecimal value = position.units.multiply(priceEntry.getValue());
        if (position.currency == Currency.EUR) {
            return value;
        }
        // GBX (pence) converts through GBP/100 — fx_rate stores ISO currencies only
        Currency fxCurrency = position.currency == Currency.GBX ? Currency.GBP : position.currency;
        TreeMap<LocalDate, BigDecimal> rates = fxByCurrency.get(fxCurrency);
        Map.Entry<LocalDate, BigDecimal> rateEntry = rates != null ? rates.floorEntry(day) : null;
        if (rateEntry == null) {
            return null;
        }
        value = value.multiply(rateEntry.getValue());
        if (position.currency == Currency.GBX) {
            value = value.divide(GBX_PER_GBP, UNIT_SCALE, RoundingMode.HALF_UP);
        }
        return value;
    }

    /**
     * Time-weighted daily returns: r_t = (V_t - F_t) / V_{t-1} - 1, F_t = net external flow on day
     * t. Sharpe = mean/std * sqrt(252) with risk-free 0; volatility = std * sqrt(252); drawdown =
     * max peak-to-trough decline of the value series.
     */
    private void applyRiskMetrics(
            PortfolioHistorySummary summary, List<PortfolioHistoryPoint> points, List<BigDecimal> dailyFlows) {
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            double previousValue = points.get(i - 1).getValueEur().doubleValue();
            if (previousValue <= 0) {
                continue;
            }
            double adjustedValue = points.get(i).getValueEur().doubleValue()
                    - dailyFlows.get(i).doubleValue();
            returns.add(adjustedValue / previousValue - 1);
        }
        if (returns.size() < 2) {
            return;
        }

        double mean =
                returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance =
                returns.stream().mapToDouble(r -> (r - mean) * (r - mean)).sum() / (returns.size() - 1);
        double std = Math.sqrt(variance);
        if (std > 0) {
            summary.setSharpe(round(mean / std * Math.sqrt(TRADING_DAYS_PER_YEAR)));
            summary.setVolatilityPct(round(std * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100));
        }

        double peak = 0;
        double maxDrawdown = 0;
        for (PortfolioHistoryPoint point : points) {
            double value = point.getValueEur().doubleValue();
            peak = Math.max(peak, value);
            if (peak > 0) {
                maxDrawdown = Math.max(maxDrawdown, 1 - value / peak);
            }
        }
        summary.setMaxDrawdownPct(round(maxDrawdown * 100));
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Booking date of the trade, falling back to import time for old manual entries */
    private LocalDate effectiveDate(InvestmentTransaction tx) {
        if (tx.getTransactionDate() != null) {
            return tx.getTransactionDate().toLocalDate();
        }
        return tx.getCreatedAt().toLocalDate();
    }

    /** EUR amount of the transaction (entity precomputes it, but be defensive about nulls) */
    private BigDecimal txAmount(InvestmentTransaction tx) {
        return tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
    }

    private BigDecimal percentage(BigDecimal part, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(base, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private PortfolioHistorySummary emptySummary() {
        BigDecimal zero = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return PortfolioHistorySummary.builder()
                .currentValueEur(zero)
                .investedEur(zero)
                .unrealizedGainEur(zero)
                .unrealizedGainPct(null)
                .realizedGainEur(zero)
                .dividendsEur(zero)
                .totalReturnPct(null)
                .build();
    }

    /** Mutable per-investment state during the replay. All money in EUR. */
    private static class PositionState {
        private final Long assetId;
        private final Currency currency;
        private BigDecimal units = BigDecimal.ZERO;
        private BigDecimal investedEur = BigDecimal.ZERO;
        private BigDecimal lastPrice; // instrument currency
        private BigDecimal lastExchangeRate; // EUR per unit of instrument currency, null = EUR

        PositionState(com.radomskyi.budgeter.domain.entity.investment.Investment investment) {
            this.assetId = investment.getAsset().getId();
            this.currency = investment.getCurrency();
        }

        void applyBuy(InvestmentTransaction tx) {
            units = units.add(tx.getUnits());
            investedEur = investedEur.add(tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO);
            lastPrice = tx.getPricePerUnit();
            lastExchangeRate = tx.getExchangeRate();
        }

        /** Returns the realized gain/loss of this sell in EUR */
        BigDecimal applySell(InvestmentTransaction tx) {
            BigDecimal soldUnits = tx.getUnits().min(units);
            BigDecimal avgCost = units.compareTo(BigDecimal.ZERO) > 0
                    ? investedEur.divide(units, UNIT_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal soldCost = soldUnits.multiply(avgCost);

            units = units.subtract(tx.getUnits()).max(BigDecimal.ZERO);
            investedEur = investedEur.subtract(soldCost).max(BigDecimal.ZERO);
            lastPrice = tx.getPricePerUnit();
            lastExchangeRate = tx.getExchangeRate();

            // Prefer the gain/loss the import already computed (matches Investment.addTransaction);
            // fall back to proceeds minus average cost for manual rows without one
            if (tx.getRealizedGainLoss() != null) {
                return tx.getRealizedGainLoss();
            }
            BigDecimal proceeds = tx.getUnits().multiply(tx.getPricePerUnit());
            if (tx.getExchangeRate() != null) {
                proceeds = proceeds.multiply(tx.getExchangeRate());
            }
            BigDecimal fees = tx.getFees() != null ? tx.getFees() : BigDecimal.ZERO;
            return proceeds.subtract(soldCost).subtract(fees);
        }

        /** Fallback valuation from the last trade of this position */
        BigDecimal tradeValueEur() {
            if (lastPrice == null || units.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal value = units.multiply(lastPrice);
            if (lastExchangeRate != null) {
                value = value.multiply(lastExchangeRate);
            }
            return value;
        }
    }
}
