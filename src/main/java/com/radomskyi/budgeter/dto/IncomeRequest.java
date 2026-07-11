package com.radomskyi.budgeter.dto;

import com.radomskyi.budgeter.domain.entity.budgeting.IncomeCategory;
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
@Schema(description = "Request DTO for creating or updating an income")
public class IncomeRequest {

    @NotNull(message = "Name is required")
    @Size(max = 50, message = "Name must not exceed 50 characters")
    @Schema(description = "Income name", example = "Salary", maxLength = 50)
    private String name;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Schema(description = "Income amount", example = "3500.00")
    private BigDecimal amount;

    @NotNull(message = "Category is required")
    @Schema(description = "Income category")
    private IncomeCategory category;

    @Schema(description = "Income description", example = "Monthly salary", maxLength = 333)
    private String description;

    @Schema(description = "List of tags associated with the income")
    private List<Tag> tags;

    @Schema(description = "Id of the account the money was added to, if known", example = "1")
    private Long accountId;

    @Schema(
            description = "When the income was actually booked, if different from today",
            example = "2026-06-10T00:00:00")
    private LocalDateTime transactionDate;
}
