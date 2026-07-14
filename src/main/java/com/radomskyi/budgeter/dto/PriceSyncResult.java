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
@Schema(description = "Result of a market price sync run")
public class PriceSyncResult {

    @Schema(description = "Number of assets a price fetch was attempted for")
    private int assetsSynced;

    @Schema(description = "Number of new price rows stored")
    private int pricesStored;

    @Schema(description = "Number of price rows skipped because they were already stored")
    private int skippedExisting;

    @Schema(description = "Number of new FX rate rows stored")
    private int fxRatesStored;

    @Schema(description = "Assets that could not be resolved to a provider symbol (fix via asset.price_symbol)")
    private List<String> unresolvedAssets;

    @Schema(description = "Assets whose fetch failed with an error")
    private List<String> failedAssets;
}
