package com.radomskyi.budgeter.domain.entity.investment;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ECB reference rate for converting one unit of a foreign currency to EUR on a given day. GBX is
 * never stored — it is derived as GBP/100 at lookup time.
 */
@Entity
@Table(name = "fx_rate", uniqueConstraints = @UniqueConstraint(columnNames = {"rate_date", "currency"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    // EUR per 1 unit of the currency
    @NotNull
    @Positive
    @Column(name = "rate_to_eur", nullable = false, precision = 15, scale = 8)
    private BigDecimal rateToEur;
}
