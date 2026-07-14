package com.radomskyi.budgeter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.radomskyi.budgeter.domain.entity.investment.Asset;
import com.radomskyi.budgeter.domain.entity.investment.AssetPrice;
import com.radomskyi.budgeter.domain.entity.investment.Currency;
import com.radomskyi.budgeter.domain.entity.investment.FxRate;
import com.radomskyi.budgeter.domain.entity.investment.Investment;
import com.radomskyi.budgeter.domain.entity.investment.InvestmentTransaction;
import com.radomskyi.budgeter.domain.entity.investment.InvestmentTransactionType;
import com.radomskyi.budgeter.dto.PortfolioHistoryPoint;
import com.radomskyi.budgeter.dto.PortfolioHistoryResponse;
import com.radomskyi.budgeter.repository.AssetPriceRepository;
import com.radomskyi.budgeter.repository.FxRateRepository;
import com.radomskyi.budgeter.repository.InvestmentTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioHistoryServiceTest {

    @Mock
    private InvestmentTransactionRepository investmentTransactionRepository;

    @Mock
    private AssetPriceRepository assetPriceRepository;

    @Mock
    private FxRateRepository fxRateRepository;

    @InjectMocks
    private PortfolioHistoryService portfolioHistoryService;

    private final Asset asset = Asset.builder().id(11L).build();
    private final Asset otherAsset = Asset.builder().id(22L).build();
    private final Investment investment =
            Investment.builder().id(1L).asset(asset).currency(Currency.EUR).build();
    private final Investment otherInvestment =
            Investment.builder().id(2L).asset(otherAsset).currency(Currency.EUR).build();

    @BeforeEach
    void setUp() {
        // No synced market data by default — replay falls back to trade prices
        lenient().when(assetPriceRepository.findAllByAssetIds(anyList())).thenReturn(List.of());
        lenient().when(fxRateRepository.findAll()).thenReturn(List.of());
    }

    private AssetPrice price(Asset priceAsset, LocalDate date, String close, Currency currency) {
        return AssetPrice.builder()
                .asset(priceAsset)
                .priceDate(date)
                .close(new BigDecimal(close))
                .currency(currency)
                .source("TEST")
                .build();
    }

    private InvestmentTransaction tx(
            Investment inv,
            InvestmentTransactionType type,
            LocalDate date,
            String units,
            String pricePerUnit,
            String amountEur,
            String exchangeRate,
            String realizedGainLoss) {
        return InvestmentTransaction.builder()
                .investment(inv)
                .transactionType(type)
                .units(new BigDecimal(units))
                .pricePerUnit(new BigDecimal(pricePerUnit))
                .currency(exchangeRate == null ? Currency.EUR : Currency.USD)
                .exchangeRate(exchangeRate == null ? null : new BigDecimal(exchangeRate))
                .amount(new BigDecimal(amountEur))
                .realizedGainLoss(realizedGainLoss == null ? null : new BigDecimal(realizedGainLoss))
                .transactionDate(date.atStartOfDay())
                .build();
    }

    @Test
    void getHistory_ShouldReturnEmpty_WhenNoTransactions() {
        when(investmentTransactionRepository.findAllWithInvestment()).thenReturn(List.of());

        PortfolioHistoryResponse response = portfolioHistoryService.getHistory();

        assertThat(response.getPoints()).isEmpty();
        assertThat(response.getSummary().getCurrentValueEur()).isEqualByComparingTo("0");
        assertThat(response.getSummary().getTotalReturnPct()).isNull();
    }

    @Test
    void getHistory_ShouldProduceDailyPoints_FromFirstTradeUntilToday() {
        LocalDate start = LocalDate.now().minusDays(9);
        when(investmentTransactionRepository.findAllWithInvestment())
                .thenReturn(
                        List.of(tx(investment, InvestmentTransactionType.BUY, start, "10", "10", "100", null, null)));

        PortfolioHistoryResponse response = portfolioHistoryService.getHistory();

        assertThat(response.getPoints()).hasSize(10);
        assertThat(response.getPoints().get(0).getDate()).isEqualTo(start);
        assertThat(response.getPoints().get(9).getDate()).isEqualTo(LocalDate.now());
        // Flat between trades: every point valued at the last trade price
        for (PortfolioHistoryPoint point : response.getPoints()) {
            assertThat(point.getValueEur()).isEqualByComparingTo("100.00");
            assertThat(point.getInvestedEur()).isEqualByComparingTo("100.00");
            assertThat(point.getGainEur()).isEqualByComparingTo("0.00");
        }
    }

    @Test
    void getHistory_ShouldRevaluePosition_WhenLaterTradeChangesPrice() {
        LocalDate start = LocalDate.now().minusDays(5);
        when(investmentTransactionRepository.findAllWithInvestment())
                .thenReturn(List.of(
                        tx(investment, InvestmentTransactionType.BUY, start, "10", "10", "100", null, null),
                        tx(
                                investment,
                                InvestmentTransactionType.BUY,
                                start.plusDays(2),
                                "10",
                                "20",
                                "200",
                                null,
                                null)));

        PortfolioHistoryResponse response = portfolioHistoryService.getHistory();

        PortfolioHistoryPoint beforeSecondBuy = response.getPoints().get(1);
        assertThat(beforeSecondBuy.getValueEur()).isEqualByComparingTo("100.00");

        // After the second buy all 20 units are valued at the new price of 20
        PortfolioHistoryPoint afterSecondBuy = response.getPoints().get(2);
        assertThat(afterSecondBuy.getValueEur()).isEqualByComparingTo("400.00");
        assertThat(afterSecondBuy.getInvestedEur()).isEqualByComparingTo("300.00");
        assertThat(afterSecondBuy.getGainEur()).isEqualByComparingTo("100.00");
        assertThat(afterSecondBuy.getGainPct()).isEqualByComparingTo("33.33");
    }

    @Test
    void getHistory_ShouldTrackRealizedGains_WhenSelling() {
        LocalDate start = LocalDate.now().minusDays(4);
        when(investmentTransactionRepository.findAllWithInvestment())
                .thenReturn(List.of(
                        tx(investment, InvestmentTransactionType.BUY, start, "10", "10", "100", null, null),
                        tx(
                                investment,
                                InvestmentTransactionType.SELL,
                                start.plusDays(2),
                                "5",
                                "20",
                                "100",
                                null,
                                "50")));

        PortfolioHistoryResponse response = portfolioHistoryService.getHistory();

        PortfolioHistoryPoint afterSell = response.getPoints().get(2);
        assertThat(afterSell.getValueEur()).isEqualByComparingTo("100.00"); // 5 units left at price 20
        assertThat(afterSell.getInvestedEur()).isEqualByComparingTo("50.00");
        assertThat(afterSell.getRealizedEur()).isEqualByComparingTo("50.00");

        // Total return: unrealized 50 + realized 50 over 100 paid = 100%
        assertThat(response.getSummary().getTotalReturnPct()).isEqualByComparingTo("100.00");
    }

    @Test
    void getHistory_ShouldAccumulateDividends_WithoutTouchingPositionValue() {
        LocalDate start = LocalDate.now().minusDays(3);
        when(investmentTransactionRepository.findAllWithInvestment())
                .thenReturn(List.of(
                        tx(investment, InvestmentTransactionType.BUY, start, "10", "10", "100", null, null),
                        // Dividend pricePerUnit is payout per share, must not become the market price
                        tx(
                                investment,
                                InvestmentTransactionType.DIVIDEND,
                                start.plusDays(1),
                                "10",
                                "0.5",
                                "5",
                                null,
                                null)));

        PortfolioHistoryResponse response = portfolioHistoryService.getHistory();

        PortfolioHistoryPoint afterDividend = response.getPoints().get(1);
        assertThat(afterDividend.getValueEur()).isEqualByComparingTo("100.00");
        assertThat(afterDividend.getDividendsEur()).isEqualByComparingTo("5.00");
        assertThat(response.getSummary().getDividendsEur()).isEqualByComparingTo("5.00");
    }

    @Test
    void getHistory_ShouldConvertWithExchangeRate_WhenInstrumentNotInEur() {
        LocalDate start = LocalDate.now().minusDays(2);
        when(investmentTransactionRepository.findAllWithInvestment())
                .thenReturn(List.of(
                        // 10 units at 10 USD, 0.90 EUR per USD → 90 EUR value
                        tx(investment, InvestmentTransactionType.BUY, start, "10", "10", "90", "0.9", null)));

        PortfolioHistoryResponse response = portfolioHistoryService.getHistory();

        assertThat(response.getPoints().get(0).getValueEur()).isEqualByComparingTo("90.00");
    }

    @Test
    void getHistory_ShouldSumAcrossInvestments() {
        LocalDate start = LocalDate.now().minusDays(2);
        when(investmentTransactionRepository.findAllWithInvestment())
                .thenReturn(List.of(
                        tx(investment, InvestmentTransactionType.BUY, start, "10", "10", "100", null, null),
                        tx(
                                otherInvestment,
                                InvestmentTransactionType.BUY,
                                start.plusDays(1),
                                "1",
                                "50",
                                "50",
                                null,
                                null)));

        PortfolioHistoryResponse response = portfolioHistoryService.getHistory();

        assertThat(response.getPoints().get(0).getValueEur()).isEqualByComparingTo("100.00");
        assertThat(response.getPoints().get(1).getValueEur()).isEqualByComparingTo("150.00");
        assertThat(response.getSummary().getCurrentValueEur()).isEqualByComparingTo("150.00");
    }

    @Test
    void getHistory_ShouldPreferSyncedMarketPrice_AndFallBackBeforeIt() {
        LocalDate start = LocalDate.now().minusDays(4);
        when(investmentTransactionRepository.findAllWithInvestment())
                .thenReturn(
                        List.of(tx(investment, InvestmentTransactionType.BUY, start, "10", "10", "100", null, null)));
        // Synced close of 15 exists only from day+2; before that the trade price of 10 applies
        when(assetPriceRepository.findAllByAssetIds(anyList()))
                .thenReturn(List.of(price(asset, start.plusDays(2), "15", Currency.EUR)));

        PortfolioHistoryResponse response = portfolioHistoryService.getHistory();

        assertThat(response.getPoints().get(1).getValueEur()).isEqualByComparingTo("100.00");
        assertThat(response.getPoints().get(2).getValueEur()).isEqualByComparingTo("150.00");
        // Weekend/holiday behaviour: no newer row, last close carries forward
        assertThat(response.getPoints().get(4).getValueEur()).isEqualByComparingTo("150.00");
    }

    @Test
    void getHistory_ShouldConvertSyncedPriceWithFxRate_WhenInstrumentNotInEur() {
        LocalDate start = LocalDate.now().minusDays(2);
        Investment usdInvestment =
                Investment.builder().id(3L).asset(asset).currency(Currency.USD).build();
        when(investmentTransactionRepository.findAllWithInvestment())
                .thenReturn(List.of(
                        tx(usdInvestment, InvestmentTransactionType.BUY, start, "10", "10", "90", "0.9", null)));
        when(assetPriceRepository.findAllByAssetIds(anyList()))
                .thenReturn(List.of(price(asset, start, "12", Currency.USD)));
        when(fxRateRepository.findAll())
                .thenReturn(List.of(FxRate.builder()
                        .rateDate(start)
                        .currency(Currency.USD)
                        .rateToEur(new BigDecimal("0.8"))
                        .build()));

        PortfolioHistoryResponse response = portfolioHistoryService.getHistory();

        // 10 units × 12 USD × 0.8 = 96 EUR (synced data wins over the 90 EUR trade valuation)
        assertThat(response.getPoints().get(0).getValueEur()).isEqualByComparingTo("96.00");
    }

    @Test
    void getHistory_ShouldFallBackToTradePrice_WhenFxRateMissing() {
        LocalDate start = LocalDate.now().minusDays(1);
        Investment usdInvestment =
                Investment.builder().id(3L).asset(asset).currency(Currency.USD).build();
        when(investmentTransactionRepository.findAllWithInvestment())
                .thenReturn(List.of(
                        tx(usdInvestment, InvestmentTransactionType.BUY, start, "10", "10", "90", "0.9", null)));
        when(assetPriceRepository.findAllByAssetIds(anyList()))
                .thenReturn(List.of(price(asset, start, "12", Currency.USD)));
        // No FX rates stored → synced price unusable → trade valuation

        PortfolioHistoryResponse response = portfolioHistoryService.getHistory();

        assertThat(response.getPoints().get(0).getValueEur()).isEqualByComparingTo("90.00");
    }

    @Test
    void getHistory_ShouldLeaveRiskMetricsNull_WhenTooFewPricedDays() {
        LocalDate start = LocalDate.now().minusDays(5);
        when(investmentTransactionRepository.findAllWithInvestment())
                .thenReturn(
                        List.of(tx(investment, InvestmentTransactionType.BUY, start, "10", "10", "100", null, null)));
        when(assetPriceRepository.findAllByAssetIds(anyList()))
                .thenReturn(List.of(price(asset, start, "11", Currency.EUR)));

        PortfolioHistoryResponse response = portfolioHistoryService.getHistory();

        assertThat(response.getSummary().getSharpe()).isNull();
        assertThat(response.getSummary().getVolatilityPct()).isNull();
        assertThat(response.getSummary().getMaxDrawdownPct()).isNull();
    }

    @Test
    void getHistory_ShouldComputeRiskMetrics_WhenEnoughPricedDays() {
        LocalDate start = LocalDate.now().minusDays(39);
        when(investmentTransactionRepository.findAllWithInvestment())
                .thenReturn(
                        List.of(tx(investment, InvestmentTransactionType.BUY, start, "10", "10", "100", null, null)));

        // 40 days of closes alternating 10/11: peak 110, trough 100 → max drawdown 9.09%
        List<AssetPrice> prices = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            prices.add(price(asset, start.plusDays(i), i % 2 == 0 ? "10" : "11", Currency.EUR));
        }
        when(assetPriceRepository.findAllByAssetIds(anyList())).thenReturn(prices);

        PortfolioHistoryResponse response = portfolioHistoryService.getHistory();

        assertThat(response.getSummary().getSharpe()).isNotNull();
        assertThat(response.getSummary().getVolatilityPct()).isNotNull();
        assertThat(response.getSummary().getMaxDrawdownPct()).isEqualByComparingTo("9.09");
    }
}
