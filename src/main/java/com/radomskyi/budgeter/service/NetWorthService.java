package com.radomskyi.budgeter.service;

import com.radomskyi.budgeter.domain.entity.investment.AssetPrice;
import com.radomskyi.budgeter.domain.entity.investment.Currency;
import com.radomskyi.budgeter.domain.entity.investment.FxRate;
import com.radomskyi.budgeter.domain.entity.investment.Investment;
import com.radomskyi.budgeter.domain.service.NetWorthServiceInterface;
import com.radomskyi.budgeter.dto.NetWorthPosition;
import com.radomskyi.budgeter.dto.NetWorthResponse;
import com.radomskyi.budgeter.repository.AssetPriceRepository;
import com.radomskyi.budgeter.repository.FxRateRepository;
import com.radomskyi.budgeter.repository.InvestmentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates net worth from imported investments. Position values prefer the latest synced
 * end-of-day market price (asset_price + fx_rate), falling back to the price of the most recent
 * imported trade when no synced price exists.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NetWorthService implements NetWorthServiceInterface {

    private static final BigDecimal GBX_PER_GBP = BigDecimal.valueOf(100);

    private final InvestmentRepository investmentRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final FxRateRepository fxRateRepository;

    @Override
    public NetWorthResponse getNetWorth() {
        log.info("Calculating net worth");

        List<Investment> openInvestments = investmentRepository.findAll().stream()
                .filter(investment -> investment.getTotalUnits() != null
                        && investment.getTotalUnits().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        Map<String, BigDecimal> byBrokerage = new TreeMap<>();
        Map<String, BigDecimal> byAssetType = new TreeMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        List<NetWorthPosition> positions = openInvestments.stream()
                .map(this::toPosition)
                .sorted((a, b) -> b.getValueEur().compareTo(a.getValueEur()))
                .toList();

        for (NetWorthPosition position : positions) {
            totalValue = totalValue.add(position.getValueEur());
            String brokerage = position.getBrokerage() != null ? position.getBrokerage() : "Unknown";
            byBrokerage.merge(brokerage, position.getValueEur(), BigDecimal::add);
            byAssetType.merge(position.getAssetType().name(), position.getValueEur(), BigDecimal::add);
        }

        return NetWorthResponse.builder()
                .totalValue(totalValue)
                .currency("EUR")
                .byBrokerage(byBrokerage)
                .byAssetType(byAssetType)
                .positions(positions)
                .build();
    }

    private NetWorthPosition toPosition(Investment investment) {
        // Trade Republic securities are ISIN-only; fall back to ISIN so clients always have an id
        String ticker = investment.getAsset().getTicker() != null
                ? investment.getAsset().getTicker()
                : investment.getAsset().getIsin();

        BigDecimal latestPrice = investment.getLatestPrice();
        BigDecimal valueEur = investment.getCurrentValueEur();
        Optional<AssetPrice> syncedPrice =
                assetPriceRepository.findFirstByAssetIdAndPriceDateLessThanEqualOrderByPriceDateDesc(
                        investment.getAsset().getId(), LocalDate.now());
        if (syncedPrice.isPresent()) {
            BigDecimal syncedValue = syncedValueEur(investment, syncedPrice.get());
            if (syncedValue != null) {
                latestPrice = syncedPrice.get().getClose();
                valueEur = syncedValue;
            }
        }

        return NetWorthPosition.builder()
                .ticker(ticker)
                .name(investment.getAsset().getName())
                .isin(investment.getAsset().getIsin())
                .assetType(investment.getAsset().getAssetType())
                .brokerage(investment.getBrokerage())
                .units(investment.getTotalUnits())
                .latestPrice(latestPrice)
                .priceCurrency(investment.getCurrency())
                .valueEur(valueEur)
                .build();
    }

    /** EUR value from a synced close, or null when the needed FX rate is missing */
    private BigDecimal syncedValueEur(Investment investment, AssetPrice price) {
        BigDecimal value = investment.getTotalUnits().multiply(price.getClose());
        if (investment.getCurrency() != Currency.EUR) {
            // GBX (pence) converts through GBP/100 — fx_rate stores ISO currencies only
            Currency fxCurrency = investment.getCurrency() == Currency.GBX ? Currency.GBP : investment.getCurrency();
            Optional<FxRate> rate = fxRateRepository.findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
                    fxCurrency, LocalDate.now());
            if (rate.isEmpty()) {
                return null;
            }
            value = value.multiply(rate.get().getRateToEur());
            if (investment.getCurrency() == Currency.GBX) {
                value = value.divide(GBX_PER_GBP, 8, RoundingMode.HALF_UP);
            }
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
