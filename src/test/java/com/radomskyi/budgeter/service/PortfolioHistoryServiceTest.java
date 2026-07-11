package com.radomskyi.budgeter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.radomskyi.budgeter.domain.entity.investment.Currency;
import com.radomskyi.budgeter.domain.entity.investment.Investment;
import com.radomskyi.budgeter.domain.entity.investment.InvestmentTransaction;
import com.radomskyi.budgeter.domain.entity.investment.InvestmentTransactionType;
import com.radomskyi.budgeter.dto.PortfolioHistoryPoint;
import com.radomskyi.budgeter.dto.PortfolioHistoryResponse;
import com.radomskyi.budgeter.repository.InvestmentTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioHistoryServiceTest {

    @Mock
    private InvestmentTransactionRepository investmentTransactionRepository;

    @InjectMocks
    private PortfolioHistoryService portfolioHistoryService;

    private final Investment investment = Investment.builder().id(1L).build();
    private final Investment otherInvestment = Investment.builder().id(2L).build();

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
}
