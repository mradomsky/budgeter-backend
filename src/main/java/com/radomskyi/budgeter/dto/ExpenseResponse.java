package com.radomskyi.budgeter.dto;

import com.radomskyi.budgeter.domain.entity.budgeting.ExpenseCategory;
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
@Schema(description = "Response DTO for expense data")
public class ExpenseResponse {

    @Schema(description = "Unique identifier of the expense", example = "1")
    private Long id;

    @Schema(description = "Expense name", example = "Lunch")
    private String name;

    @Schema(description = "Expense amount", example = "25.50")
    private BigDecimal amount;

    @Schema(description = "Expense category")
    private ExpenseCategory category;

    @Schema(description = "Expense description", example = "Lunch at restaurant")
    private String description;

    @Schema(description = "List of tags associated with the expense")
    private List<Tag> tags;

    @Schema(description = "Id of the account the money was taken from, if known", example = "1")
    private Long accountId;

    @Schema(description = "Name of the account the money was taken from, if known", example = "Bargeld")
    private String accountName;

    @Schema(description = "When the expense was actually booked (falls back to createdAt if unknown)")
    private LocalDateTime transactionDate;

    @Schema(description = "Date and time when the expense was created")
    private LocalDateTime createdAt;

    @Schema(description = "Date and time when the expense was last updated")
    private LocalDateTime updatedAt;
}
