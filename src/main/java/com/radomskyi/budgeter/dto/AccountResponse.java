package com.radomskyi.budgeter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Balance snapshot of a bank/cash account")
public class AccountResponse {

    @Schema(description = "Unique identifier of the account", example = "1")
    private Long id;

    @Schema(description = "Account name", example = "Bargeld")
    private String name;

    @Schema(description = "Current balance", example = "80.00")
    private BigDecimal balance;

    @Schema(description = "Currency of the balance", example = "EUR")
    private String currency;

    @Schema(description = "Booking date the balance was last read from")
    private LocalDateTime balanceAsOf;
}
