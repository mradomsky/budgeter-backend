package com.radomskyi.budgeter.service;

import com.radomskyi.budgeter.domain.entity.investment.Asset;
import com.radomskyi.budgeter.domain.entity.investment.AssetPrice;
import com.radomskyi.budgeter.domain.entity.investment.Currency;
import com.radomskyi.budgeter.domain.entity.investment.FxRate;
import com.radomskyi.budgeter.domain.entity.investment.Investment;
import com.radomskyi.budgeter.domain.service.FxRateProviderInterface;
import com.radomskyi.budgeter.domain.service.PriceProviderInterface;
import com.radomskyi.budgeter.domain.service.PriceSyncServiceInterface;
import com.radomskyi.budgeter.dto.DailyFxRate;
import com.radomskyi.budgeter.dto.DailyPrice;
import com.radomskyi.budgeter.dto.PriceSyncResult;
import com.radomskyi.budgeter.repository.AssetPriceRepository;
import com.radomskyi.budgeter.repository.AssetRepository;
import com.radomskyi.budgeter.repository.FxRateRepository;
import com.radomskyi.budgeter.repository.InvestmentRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Fetches end-of-day close prices and ECB FX rates once a day for every asset with an open
 * position, and stores them idempotently (existing rows are never touched, so reruns and
 * backfills are safe to repeat). See docs/MARKET_SYNC_PLAN.md for the design.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceSyncService implements PriceSyncServiceInterface {

    private final InvestmentRepository investmentRepository;
    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final FxRateRepository fxRateRepository;
    private final PriceProviderInterface priceProvider;
    private final FxRateProviderInterface fxRateProvider;

    @Value("${pricesync.enabled:false}")
    private boolean enabled;

    @Value("${pricesync.backfill-start:2021-01-01}")
    private LocalDate backfillStart;

    @Value("${pricesync.request-delay-ms:7600}")
    private long requestDelayMs;

    @Scheduled(cron = "${pricesync.cron:0 30 5 * * *}", zone = "${pricesync.timezone:Europe/Berlin}")
    public void scheduledDailySync() {
        if (!enabled) {
            return;
        }
        PriceSyncResult result = sync(null, null);
        log.info("Scheduled price sync finished: {}", result);
    }

    // One repository transaction per row via save(); a failing asset must not roll back the rest
    @Override
    public PriceSyncResult sync(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = to != null ? to : LocalDate.now().minusDays(1);
        log.info("Starting price sync up to {} (from={})", effectiveTo, from);

        List<Investment> active = investmentRepository.findActiveInvestments();

        // One fetch per asset even when held at several brokerages; keep the instrument currency
        Map<Long, Investment> byAsset = new LinkedHashMap<>();
        for (Investment investment : active) {
            byAsset.putIfAbsent(investment.getAsset().getId(), investment);
        }

        PriceSyncResult result = PriceSyncResult.builder()
                .unresolvedAssets(new ArrayList<>())
                .failedAssets(new ArrayList<>())
                .build();

        boolean first = true;
        for (Investment investment : byAsset.values()) {
            Asset asset = investment.getAsset();
            try {
                if (!first) {
                    pause();
                }
                first = false;
                syncAsset(asset, investment.getCurrency(), from, effectiveTo, result);
            } catch (Exception e) {
                log.error("Price sync failed for asset {}: {}", asset.getName(), e.getMessage());
                result.getFailedAssets().add(asset.getName());
            }
        }

        syncFxRates(active, from, effectiveTo, result);

        log.info(
                "Price sync finished: {} assets, {} prices stored, {} skipped, {} fx rates, {} unresolved, {} failed",
                result.getAssetsSynced(),
                result.getPricesStored(),
                result.getSkippedExisting(),
                result.getFxRatesStored(),
                result.getUnresolvedAssets().size(),
                result.getFailedAssets().size());
        return result;
    }

    private void syncAsset(Asset asset, Currency currency, LocalDate from, LocalDate to, PriceSyncResult result) {
        String symbol = resolveSymbol(asset);
        if (symbol == null) {
            result.getUnresolvedAssets().add(asset.getName());
            return;
        }

        LocalDate effectiveFrom = from != null ? from : nextMissingDate(asset.getId());
        if (effectiveFrom.isAfter(to)) {
            return; // already up to date
        }

        result.setAssetsSynced(result.getAssetsSynced() + 1);
        for (DailyPrice price : priceProvider.getEodPrices(symbol, effectiveFrom, to)) {
            if (assetPriceRepository.existsByAssetIdAndPriceDate(asset.getId(), price.date())) {
                result.setSkippedExisting(result.getSkippedExisting() + 1);
                continue;
            }
            assetPriceRepository.save(AssetPrice.builder()
                    .asset(asset)
                    .priceDate(price.date())
                    .close(price.close())
                    .currency(currency)
                    .source(priceProvider.sourceName())
                    .build());
            result.setPricesStored(result.getPricesStored() + 1);
        }
    }

    private void syncFxRates(List<Investment> active, LocalDate from, LocalDate to, PriceSyncResult result) {
        Set<Currency> currencies = new LinkedHashSet<>();
        for (Investment investment : active) {
            // GBX quotes convert via GBP/100 — store GBP, never GBX
            Currency currency = investment.getCurrency() == Currency.GBX ? Currency.GBP : investment.getCurrency();
            if (currency != Currency.EUR) {
                currencies.add(currency);
            }
        }

        for (Currency currency : currencies) {
            try {
                LocalDate effectiveFrom = from != null
                        ? from
                        : fxRateRepository
                                .findFirstByCurrencyOrderByRateDateDesc(currency)
                                .map(rate -> rate.getRateDate().plusDays(1))
                                .orElse(backfillStart);
                if (effectiveFrom.isAfter(to)) {
                    continue;
                }
                for (DailyFxRate rate : fxRateProvider.getRatesToEur(currency, effectiveFrom, to)) {
                    if (fxRateRepository.existsByRateDateAndCurrency(rate.date(), currency)) {
                        continue;
                    }
                    fxRateRepository.save(FxRate.builder()
                            .rateDate(rate.date())
                            .currency(currency)
                            .rateToEur(rate.rateToEur())
                            .build());
                    result.setFxRatesStored(result.getFxRatesStored() + 1);
                }
            } catch (Exception e) {
                log.error("FX sync failed for {}: {}", currency, e.getMessage());
            }
        }
    }

    /** price_symbol override → ticker → one-time ISIN lookup cached into price_symbol */
    private String resolveSymbol(Asset asset) {
        if (asset.getPriceSymbol() != null) {
            return asset.getPriceSymbol();
        }
        if (asset.getTicker() != null) {
            return asset.getTicker();
        }
        if (asset.getIsin() == null) {
            return null;
        }
        Optional<String> resolved = priceProvider.findSymbolByIsin(asset.getIsin());
        resolved.ifPresent(symbol -> {
            asset.setPriceSymbol(symbol);
            assetRepository.save(asset);
        });
        return resolved.orElse(null);
    }

    private LocalDate nextMissingDate(Long assetId) {
        return assetPriceRepository
                .findFirstByAssetIdOrderByPriceDateDesc(assetId)
                .map(price -> price.getPriceDate().plusDays(1))
                .orElse(backfillStart);
    }

    /** Keeps backfill under the provider's free-tier rate limit */
    private void pause() {
        if (requestDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(requestDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
