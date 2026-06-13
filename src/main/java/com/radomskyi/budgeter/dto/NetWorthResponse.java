package com.radomskyi.budgeter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Net worth across all imported investments")
public class NetWorthResponse {

    @Schema(description = "Total portfolio value in EUR", example = "5782.60")
    private BigDecimal totalValue;

    @Schema(description = "Currency of all values in this response", example = "EUR")
    private String currency;

    @Schema(description = "Portfolio value grouped by brokerage")
    private Map<String, BigDecimal> byBrokerage;

    @Schema(description = "Portfolio value grouped by asset type")
    private Map<String, BigDecimal> byAssetType;

    @Schema(description = "All open positions")
    private List<NetWorthPosition> positions;
}
