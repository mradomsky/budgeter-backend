package com.radomskyi.budgeter.dto;

import com.radomskyi.budgeter.domain.entity.budgeting.ExpenseCategory;
import com.radomskyi.budgeter.domain.entity.budgeting.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Request DTO for creating or updating an expense")
public class ExpenseRequest {

    @NotNull(message = "Name is required")
    @Size(max = 50, message = "Name must not exceed 50 characters")
    @Schema(description = "Expense name", example = "Lunch", maxLength = 50)
    private String name;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Schema(description = "Expense amount", example = "25.50")
    private BigDecimal amount;

    @NotNull(message = "Category is required")
    @Schema(description = "Expense category")
    private ExpenseCategory category;

    @Schema(description = "Expense description", example = "Lunch at restaurant", maxLength = 333)
    private String description;

    @Schema(description = "List of tags associated with the expense")
    private List<Tag> tags;

    @Schema(description = "Id of the account the money was taken from, if known", example = "1")
    private Long accountId;

    @Schema(
            description = "When the expense was actually booked, if different from today",
            example = "2026-06-10T00:00:00")
    private LocalDateTime transactionDate;
}
