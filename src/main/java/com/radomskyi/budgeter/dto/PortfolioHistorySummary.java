package com.radomskyi.budgeter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lifetime portfolio performance summary")
public class PortfolioHistorySummary {

    @Schema(description = "Current market value of open positions in EUR")
    private BigDecimal currentValueEur;

    @Schema(description = "Cost of open positions in EUR including buy fees")
    private BigDecimal investedEur;

    @Schema(description = "Unrealized gain/loss in EUR")
    private BigDecimal unrealizedGainEur;

    @Schema(description = "Unrealized gain/loss percentage, null when nothing is invested")
    private BigDecimal unrealizedGainPct;

    @Schema(description = "Total realized gain/loss from sells, in EUR")
    private BigDecimal realizedGainEur;

    @Schema(description = "Total dividends received, in EUR")
    private BigDecimal dividendsEur;

    @Schema(
            description = "Lifetime total return percentage: (unrealized + realized + dividends)"
                    + " over everything ever paid for buys. Null when there were no buys")
    private BigDecimal totalReturnPct;

    @Schema(
            description = "Annualized Sharpe ratio (risk-free rate 0) of time-weighted daily returns."
                    + " Null until enough synced market prices exist (>= 30 priced days)")
    private BigDecimal sharpe;

    @Schema(description = "Annualized volatility of time-weighted daily returns, percent. Null like sharpe")
    private BigDecimal volatilityPct;

    @Schema(description = "Maximum peak-to-trough decline of portfolio value, percent. Null like sharpe")
    private BigDecimal maxDrawdownPct;
}
