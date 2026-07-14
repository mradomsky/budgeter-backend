package com.radomskyi.budgeter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Daily portfolio value history reconstructed from imported trades, plus lifetime summary")
public class PortfolioHistoryResponse {

    @Schema(description = "Lifetime performance summary")
    private PortfolioHistorySummary summary;

    @Schema(description = "One point per calendar day, from the first trade until today")
    private List<PortfolioHistoryPoint> points;
}
