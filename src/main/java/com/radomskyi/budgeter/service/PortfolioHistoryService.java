package com.radomskyi.budgeter.service;

import com.radomskyi.budgeter.domain.entity.investment.InvestmentTransaction;
import com.radomskyi.budgeter.domain.service.PortfolioHistoryServiceInterface;
import com.radomskyi.budgeter.dto.PortfolioHistoryPoint;
import com.radomskyi.budgeter.dto.PortfolioHistoryResponse;
import com.radomskyi.budgeter.dto.PortfolioHistorySummary;
import com.radomskyi.budgeter.repository.InvestmentTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconstructs daily portfolio value history by replaying all imported investment transactions in
 * chronological order. Positions are valued at the last trade price known as of each day (no
 * external price feed), so the value line stays flat between trades of an asset. All amounts are
 * EUR; buy cost includes fees (consistent with InvestmentTransaction.amount).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PortfolioHistoryService implements PortfolioHistoryServiceInterface {

    private static final int MONEY_SCALE = 2;
    private static final int UNIT_SCALE = 8;

    private final InvestmentTransactionRepository investmentTransactionRepository;

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

        Map<Long, PositionState> positions = new HashMap<>();
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal dividends = BigDecimal.ZERO;
        BigDecimal totalBuyCost = BigDecimal.ZERO;

        List<PortfolioHistoryPoint> points = new ArrayList<>();
        LocalDate firstDay = effectiveDate(transactions.get(0));
        LocalDate lastDay = LocalDate.now();

        int txIndex = 0;
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            while (txIndex < transactions.size()
                    && !effectiveDate(transactions.get(txIndex)).isAfter(day)) {
                InvestmentTransaction tx = transactions.get(txIndex++);
                PositionState position =
                        positions.computeIfAbsent(tx.getInvestment().getId(), id -> new PositionState());

                switch (tx.getTransactionType()) {
                    case BUY -> {
                        position.applyBuy(tx);
                        totalBuyCost = totalBuyCost.add(txAmount(tx));
                    }
                    case SELL -> realized = realized.add(position.applySell(tx));
                    case DIVIDEND -> dividends = dividends.add(txAmount(tx));
                }
            }

            BigDecimal value = BigDecimal.ZERO;
            BigDecimal invested = BigDecimal.ZERO;
            for (PositionState position : positions.values()) {
                value = value.add(position.valueEur());
                invested = invested.add(position.investedEur);
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

        return PortfolioHistoryResponse.builder()
                .summary(summary)
                .points(points)
                .build();
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
        private BigDecimal units = BigDecimal.ZERO;
        private BigDecimal investedEur = BigDecimal.ZERO;
        private BigDecimal lastPrice; // instrument currency
        private BigDecimal lastExchangeRate; // EUR per unit of instrument currency, null = EUR

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

        BigDecimal valueEur() {
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
