package com.radomskyi.budgeter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.radomskyi.budgeter.domain.entity.investment.Asset;
import com.radomskyi.budgeter.domain.entity.investment.AssetPrice;
import com.radomskyi.budgeter.domain.entity.investment.AssetType;
import com.radomskyi.budgeter.domain.entity.investment.Currency;
import com.radomskyi.budgeter.domain.entity.investment.FxRate;
import com.radomskyi.budgeter.domain.entity.investment.Investment;
import com.radomskyi.budgeter.domain.entity.investment.InvestmentStyle;
import com.radomskyi.budgeter.dto.NetWorthResponse;
import com.radomskyi.budgeter.repository.AssetPriceRepository;
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

@ExtendWith(MockitoExtension.class)
class NetWorthServiceTest {

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private AssetPriceRepository assetPriceRepository;

    @Mock
    private FxRateRepository fxRateRepository;

    @InjectMocks
    private NetWorthService netWorthService;

    @BeforeEach
    void setUp() {
        // No synced market data by default — positions valued from the last trade
        lenient()
                .when(assetPriceRepository.findFirstByAssetIdAndPriceDateLessThanEqualOrderByPriceDateDesc(
                        any(), any()))
                .thenReturn(Optional.empty());
    }

    private Investment investment(
            String ticker,
            AssetType assetType,
            String brokerage,
            String units,
            String latestPrice,
            String latestExchangeRate,
            Currency currency) {
        Asset asset = Asset.builder()
                .ticker(ticker)
                .name(ticker + " asset")
                .assetType(assetType)
                .investmentStyle(InvestmentStyle.GROWTH)
                .build();
        return Investment.builder()
                .asset(asset)
                .totalUnits(new BigDecimal(units))
                .totalCost(BigDecimal.TEN)
                .costBasis(BigDecimal.ONE)
                .latestPrice(latestPrice != null ? new BigDecimal(latestPrice) : null)
                .latestExchangeRate(latestExchangeRate != null ? new BigDecimal(latestExchangeRate) : null)
                .currency(currency)
                .brokerage(brokerage)
                .build();
    }

    @Test
    void getNetWorth_ShouldAggregateByBrokerageAndAssetType_WhenMultiplePositionsExist() {
        when(investmentRepository.findAll())
                .thenReturn(List.of(
                        // 10 × 100 EUR = 1000 EUR
                        investment("VWCE", AssetType.INDEX_ETF, "Trading212", "10", "100.00", null, Currency.EUR),
                        // 2 × 50 USD × 0.85 = 85 EUR
                        investment("AAPL", AssetType.STOCK, "Trading212", "2", "50.00", "0.85", Currency.USD),
                        // 0.5 × 200 EUR = 100 EUR
                        investment("BTC", AssetType.CRYPTO, "TradeRepublic", "0.5", "200.00", null, Currency.EUR),
                        // Closed position — must be excluded
                        investment("SOLD", AssetType.STOCK, "TradeRepublic", "0", "999.00", null, Currency.EUR)));

        NetWorthResponse response = netWorthService.getNetWorth();

        assertThat(response.getTotalValue()).isEqualByComparingTo(new BigDecimal("1185.00"));
        assertThat(response.getCurrency()).isEqualTo("EUR");
        assertThat(response.getPositions()).hasSize(3);

        assertThat(response.getByBrokerage().get("Trading212")).isEqualByComparingTo(new BigDecimal("1085.00"));
        assertThat(response.getByBrokerage().get("TradeRepublic")).isEqualByComparingTo(new BigDecimal("100.00"));

        assertThat(response.getByAssetType().get("INDEX_ETF")).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(response.getByAssetType().get("STOCK")).isEqualByComparingTo(new BigDecimal("85.00"));
        assertThat(response.getByAssetType().get("CRYPTO")).isEqualByComparingTo(new BigDecimal("100.00"));

        // Positions sorted by value, largest first
        assertThat(response.getPositions().get(0).getTicker()).isEqualTo("VWCE");
    }

    @Test
    void getNetWorth_ShouldFallBackToTotalCost_WhenNoLatestPriceKnown() {
        Investment noPrice = investment("XGLD", AssetType.COMMODITY, "Trading212", "5", null, null, Currency.EUR);
        noPrice.setTotalCost(new BigDecimal("555.00"));
        when(investmentRepository.findAll()).thenReturn(List.of(noPrice));

        NetWorthResponse response = netWorthService.getNetWorth();

        assertThat(response.getTotalValue()).isEqualByComparingTo(new BigDecimal("555.00"));
    }

    @Test
    void getNetWorth_ShouldPreferSyncedPrice_WhenAvailable() {
        Investment vwce = investment("VWCE", AssetType.INDEX_ETF, "Trading212", "10", "100.00", null, Currency.EUR);
        vwce.getAsset().setId(11L);
        when(investmentRepository.findAll()).thenReturn(List.of(vwce));
        when(assetPriceRepository.findFirstByAssetIdAndPriceDateLessThanEqualOrderByPriceDateDesc(any(), any()))
                .thenReturn(Optional.of(AssetPrice.builder()
                        .asset(vwce.getAsset())
                        .priceDate(LocalDate.now().minusDays(1))
                        .close(new BigDecimal("110.00"))
                        .currency(Currency.EUR)
                        .source("TEST")
                        .build()));

        NetWorthResponse response = netWorthService.getNetWorth();

        // 10 × synced 110 = 1100, not 10 × trade 100
        assertThat(response.getTotalValue()).isEqualByComparingTo(new BigDecimal("1100.00"));
        assertThat(response.getPositions().get(0).getLatestPrice()).isEqualByComparingTo(new BigDecimal("110.00"));
    }

    @Test
    void getNetWorth_ShouldConvertSyncedPriceWithFxRate_WhenNotEur() {
        Investment aapl = investment("AAPL", AssetType.STOCK, "Trading212", "2", "50.00", "0.85", Currency.USD);
        aapl.getAsset().setId(12L);
        when(investmentRepository.findAll()).thenReturn(List.of(aapl));
        when(assetPriceRepository.findFirstByAssetIdAndPriceDateLessThanEqualOrderByPriceDateDesc(any(), any()))
                .thenReturn(Optional.of(AssetPrice.builder()
                        .asset(aapl.getAsset())
                        .priceDate(LocalDate.now().minusDays(1))
                        .close(new BigDecimal("60.00"))
                        .currency(Currency.USD)
                        .source("TEST")
                        .build()));
        when(fxRateRepository.findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(any(), any()))
                .thenReturn(Optional.of(FxRate.builder()
                        .rateDate(LocalDate.now().minusDays(1))
                        .currency(Currency.USD)
                        .rateToEur(new BigDecimal("0.9"))
                        .build()));

        NetWorthResponse response = netWorthService.getNetWorth();

        // 2 × 60 USD × 0.9 = 108 EUR
        assertThat(response.getTotalValue()).isEqualByComparingTo(new BigDecimal("108.00"));
    }

    @Test
    void getNetWorth_ShouldReturnZeroTotal_WhenNoInvestmentsExist() {
        when(investmentRepository.findAll()).thenReturn(List.of());

        NetWorthResponse response = netWorthService.getNetWorth();

        assertThat(response.getTotalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getPositions()).isEmpty();
        assertThat(response.getByBrokerage()).isEmpty();
    }
}
