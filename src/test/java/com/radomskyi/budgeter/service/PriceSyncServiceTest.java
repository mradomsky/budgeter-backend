package com.radomskyi.budgeter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radomskyi.budgeter.domain.entity.investment.Asset;
import com.radomskyi.budgeter.domain.entity.investment.AssetPrice;
import com.radomskyi.budgeter.domain.entity.investment.Currency;
import com.radomskyi.budgeter.domain.entity.investment.Investment;
import com.radomskyi.budgeter.domain.service.FxRateProviderInterface;
import com.radomskyi.budgeter.domain.service.PriceProviderInterface;
import com.radomskyi.budgeter.dto.DailyPrice;
import com.radomskyi.budgeter.dto.PriceSyncResult;
import com.radomskyi.budgeter.repository.AssetPriceRepository;
import com.radomskyi.budgeter.repository.AssetRepository;
import com.radomskyi.budgeter.repository.FxRateRepository;
import com.radomskyi.budgeter.repository.InvestmentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PriceSyncServiceTest {

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetPriceRepository assetPriceRepository;

    @Mock
    private FxRateRepository fxRateRepository;

    @Mock
    private PriceProviderInterface priceProvider;

    @Mock
    private FxRateProviderInterface fxRateProvider;

    @InjectMocks
    private PriceSyncService priceSyncService;

    private final LocalDate yesterday = LocalDate.now().minusDays(1);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(priceSyncService, "backfillStart", LocalDate.of(2021, 1, 1));
        ReflectionTestUtils.setField(priceSyncService, "requestDelayMs", 0L);
        lenient().when(priceProvider.sourceName()).thenReturn("TEST");
    }

    private Investment investmentWith(Asset asset, Currency currency) {
        return Investment.builder()
                .id(asset.getId())
                .asset(asset)
                .currency(currency)
                .totalUnits(BigDecimal.TEN)
                .build();
    }

    @Test
    void sync_ShouldStorePricesForActiveAssets_UsingTicker() {
        Asset asset = Asset.builder()
                .id(1L)
                .ticker("VWCE")
                .name("Vanguard FTSE All-World")
                .build();
        when(investmentRepository.findActiveInvestments()).thenReturn(List.of(investmentWith(asset, Currency.EUR)));
        when(assetPriceRepository.findFirstByAssetIdOrderByPriceDateDesc(1L)).thenReturn(Optional.empty());
        when(priceProvider.getEodPrices("VWCE", LocalDate.of(2021, 1, 1), yesterday))
                .thenReturn(List.of(new DailyPrice(yesterday, new BigDecimal("111.11"))));
        when(assetPriceRepository.existsByAssetIdAndPriceDate(1L, yesterday)).thenReturn(false);

        PriceSyncResult result = priceSyncService.sync(null, null);

        assertThat(result.getAssetsSynced()).isEqualTo(1);
        assertThat(result.getPricesStored()).isEqualTo(1);
        verify(assetPriceRepository).save(any(AssetPrice.class));
    }

    @Test
    void sync_ShouldSkipExistingRows_SoRerunsAreIdempotent() {
        Asset asset = Asset.builder().id(1L).ticker("VWCE").name("Vanguard").build();
        when(investmentRepository.findActiveInvestments()).thenReturn(List.of(investmentWith(asset, Currency.EUR)));
        when(assetPriceRepository.findFirstByAssetIdOrderByPriceDateDesc(1L)).thenReturn(Optional.empty());
        when(priceProvider.getEodPrices(anyString(), any(), any()))
                .thenReturn(List.of(new DailyPrice(yesterday, new BigDecimal("111.11"))));
        when(assetPriceRepository.existsByAssetIdAndPriceDate(1L, yesterday)).thenReturn(true);

        PriceSyncResult result = priceSyncService.sync(null, null);

        assertThat(result.getPricesStored()).isZero();
        assertThat(result.getSkippedExisting()).isEqualTo(1);
        verify(assetPriceRepository, never()).save(any());
    }

    @Test
    void sync_ShouldResolveSymbolFromIsin_AndCacheIt() {
        Asset asset =
                Asset.builder().id(1L).isin("IE00BK5BQT80").name("Vanguard").build();
        when(investmentRepository.findActiveInvestments()).thenReturn(List.of(investmentWith(asset, Currency.EUR)));
        when(priceProvider.findSymbolByIsin("IE00BK5BQT80")).thenReturn(Optional.of("VWCE"));
        when(assetPriceRepository.findFirstByAssetIdOrderByPriceDateDesc(1L)).thenReturn(Optional.empty());
        when(priceProvider.getEodPrices(anyString(), any(), any())).thenReturn(List.of());

        priceSyncService.sync(null, null);

        assertThat(asset.getPriceSymbol()).isEqualTo("VWCE");
        verify(assetRepository).save(asset);
        verify(priceProvider).getEodPrices("VWCE", LocalDate.of(2021, 1, 1), yesterday);
    }

    @Test
    void sync_ShouldReportUnresolvedAssets_WhenNoSymbolFound() {
        Asset asset =
                Asset.builder().id(1L).isin("XX0000000000").name("Mystery Fund").build();
        when(investmentRepository.findActiveInvestments()).thenReturn(List.of(investmentWith(asset, Currency.EUR)));
        when(priceProvider.findSymbolByIsin("XX0000000000")).thenReturn(Optional.empty());

        PriceSyncResult result = priceSyncService.sync(null, null);

        assertThat(result.getUnresolvedAssets()).containsExactly("Mystery Fund");
        assertThat(result.getAssetsSynced()).isZero();
    }

    @Test
    void sync_ShouldIsolateFailures_SoOneAssetCannotAbortTheRun() {
        Asset failing = Asset.builder().id(1L).ticker("BAD").name("Failing").build();
        Asset healthy = Asset.builder().id(2L).ticker("GOOD").name("Healthy").build();
        when(investmentRepository.findActiveInvestments())
                .thenReturn(List.of(investmentWith(failing, Currency.EUR), investmentWith(healthy, Currency.EUR)));
        when(assetPriceRepository.findFirstByAssetIdOrderByPriceDateDesc(any())).thenReturn(Optional.empty());
        when(priceProvider.getEodPrices("BAD", LocalDate.of(2021, 1, 1), yesterday))
                .thenThrow(new RuntimeException("boom"));
        when(priceProvider.getEodPrices("GOOD", LocalDate.of(2021, 1, 1), yesterday))
                .thenReturn(List.of(new DailyPrice(yesterday, BigDecimal.ONE)));
        when(assetPriceRepository.existsByAssetIdAndPriceDate(2L, yesterday)).thenReturn(false);

        PriceSyncResult result = priceSyncService.sync(null, null);

        assertThat(result.getFailedAssets()).containsExactly("Failing");
        assertThat(result.getPricesStored()).isEqualTo(1);
    }
}
