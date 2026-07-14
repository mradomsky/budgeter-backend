package com.radomskyi.budgeter.dto;

import com.radomskyi.budgeter.domain.entity.budgeting.IncomeCategory;
import com.radomskyi.budgeter.domain.entity.budgeting.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for income data")
public class IncomeResponse {

    @Schema(description = "Unique identifier of the income", example = "1")
    private Long id;

    @Schema(description = "Income name", example = "Salary")
    private String name;

    @Schema(description = "Income amount", example = "3500.00")
    private BigDecimal amount;

    @Schema(description = "Income category")
    private IncomeCategory category;

    @Schema(description = "Income description", example = "Monthly salary")
    private String description;

    @Schema(description = "List of tags associated with the income")
    private List<Tag> tags;

    @Schema(description = "Id of the account the money was added to, if known", example = "1")
    private Long accountId;

    @Schema(description = "Name of the account the money was added to, if known", example = "Bargeld")
    private String accountName;

    @Schema(description = "When the income was actually booked (falls back to createdAt if unknown)")
    private LocalDateTime transactionDate;

    @Schema(description = "Date and time when the income was created")
    private LocalDateTime createdAt;

    @Schema(description = "Date and time when the income was last updated")
    private LocalDateTime updatedAt;
}
