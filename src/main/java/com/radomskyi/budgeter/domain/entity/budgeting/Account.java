package com.radomskyi.budgeter.domain.entity.budgeting;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A bank/cash account, tracked by balance snapshot. Balances are not derived from Expense/Income
 * rows (those are filtered/categorized for budgeting and don't cover every account movement);
 * instead each import updates the balance to the most recent "Kontostand" seen for that account.
 */
@Entity
@Table(name = "account")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identifier from the source system (IBAN, Finanzguru account UUID, or email for wallets)
    @NotNull
    @Column(name = "external_id", nullable = false, unique = true, length = 100)
    private String externalId;

    @NotNull
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @NotNull
    @Column(name = "balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    @NotNull
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    // Booking date of the transaction the balance was read from — used to only accept newer
    // snapshots on re-import, never let a stale export roll a balance backwards
    @Column(name = "balance_as_of")
    private LocalDateTime balanceAsOf;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
