package com.radomskyi.budgeter.dto;

import com.radomskyi.budgeter.domain.entity.investment.AssetType;
import com.radomskyi.budgeter.domain.entity.investment.Currency;
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
@Schema(description = "A single open position in the portfolio")
public class NetWorthPosition {

    @Schema(description = "Asset ticker symbol", example = "AAPL")
    private String ticker;

    @Schema(description = "Asset name", example = "Apple Inc.")
    private String name;

    @Schema(description = "Asset ISIN", example = "US0378331005")
    private String isin;

    @Schema(description = "Type of the asset", example = "STOCK")
    private AssetType assetType;

    @Schema(description = "Brokerage holding the position", example = "Trading212")
    private String brokerage;

    @Schema(description = "Units currently held", example = "10.5")
    private BigDecimal units;

    @Schema(description = "Latest known price per unit (from the most recent imported trade)", example = "150.25")
    private BigDecimal latestPrice;

    @Schema(description = "Currency of the latest price", example = "USD")
    private Currency priceCurrency;

    @Schema(description = "Position value in EUR", example = "1502.50")
    private BigDecimal valueEur;
}
