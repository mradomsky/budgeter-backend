package com.radomskyi.budgeter.dto;

import com.radomskyi.budgeter.domain.entity.investment.AssetType;
import com.radomskyi.budgeter.domain.entity.investment.Currency;
import com.radomskyi.budgeter.domain.entity.investment.InvestmentTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for creating or updating an investment transaction")
public class InvestmentTransactionRequest {

    @NotNull(message = "Transaction type is required")
    @Schema(description = "Type of investment transaction", example = "BUY")
    private InvestmentTransactionType transactionType;

    @Size(max = 10, message = "Ticker must not exceed 10 characters")
    @Schema(description = "Asset ticker symbol", example = "AAPL", maxLength = 10)
    private String assetTicker;

    @NotNull(message = "Asset name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Schema(description = "Asset name", example = "Apple Inc.", maxLength = 100)
    private String assetName;

    @Size(max = 12, message = "ISIN must not exceed 12 characters")
    @Schema(description = "International Securities Identification Number", example = "US0378331005", maxLength = 12)
    private String assetIsin;

    @NotNull(message = "Units are required")
    @Positive(message = "Units must be positive")
    @Schema(description = "Number of units/shares", example = "10.5")
    private BigDecimal units;

    @NotNull(message = "Price per unit is required")
    @Positive(message = "Price per unit must be positive")
    @Schema(description = "Price per unit/share", example = "150.25")
    private BigDecimal pricePerUnit;

    @Schema(description = "Transaction fees", example = "2.50")
    private BigDecimal fees;

    @NotNull(message = "Currency is required")
    @Schema(description = "Transaction currency", example = "EUR")
    private Currency currency;

    @Schema(description = "Exchange rate for currency conversion", example = "1.15")
    private BigDecimal exchangeRate;

    @Size(max = 50, message = "Transaction name must not exceed 50 characters")
    @Schema(description = "Transaction name/description", example = "Apple Inc. Purchase", maxLength = 50)
    private String name;

    @Size(max = 333, message = "Description must not exceed 333 characters")
    @Schema(
            description = "Detailed transaction description",
            example = "Bought Apple shares via broker",
            maxLength = 333)
    private String description;

    @Size(max = 100, message = "Brokerage name must not exceed 100 characters")
    @Schema(description = "Brokerage company", example = "Trading212", maxLength = 100)
    private String brokerage;

    @Schema(description = "Type of the asset (defaults to STOCK when omitted)", example = "INDEX_ETF")
    private AssetType assetType;

    @Size(max = 100, message = "External id must not exceed 100 characters")
    @Schema(description = "Identifier from the source system, used for import deduplication", maxLength = 100)
    private String externalId;

    @Schema(description = "When the transaction happened at the source", example = "2025-06-10T07:04:05")
    private LocalDateTime transactionDate;
}
