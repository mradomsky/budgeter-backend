package com.radomskyi.budgeter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Portfolio state on a single day, reconstructed from imported trades")
public class PortfolioHistoryPoint {

    @Schema(description = "Calendar day this point describes", example = "2025-06-10")
    private LocalDate date;

    @Schema(description = "Market value of open positions in EUR, at last trade prices known on this day")
    private BigDecimal valueEur;

    @Schema(description = "Cost of open positions in EUR including buy fees")
    private BigDecimal investedEur;

    @Schema(description = "Unrealized gain/loss in EUR: value minus invested")
    private BigDecimal gainEur;

    @Schema(description = "Unrealized gain/loss percentage, null when nothing is invested")
    private BigDecimal gainPct;

    @Schema(description = "Cumulative realized gain/loss from sells up to this day, in EUR")
    private BigDecimal realizedEur;

    @Schema(description = "Cumulative dividends received up to this day, in EUR")
    private BigDecimal dividendsEur;
}
