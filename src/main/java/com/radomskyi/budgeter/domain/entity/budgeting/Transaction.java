package com.radomskyi.budgeter.domain.entity.budgeting;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    protected BigDecimal amount;

    @Column(name = "name", length = 50)
    protected String name;

    @Column(name = "description", length = 200)
    protected String description;

    // Identifier from the source system (Trading212 ID, Trade Republic transaction_id,
    // Finanzguru Buchungs-ID) — used to skip duplicates on re-import
    @Column(name = "external_id", length = 100, unique = true)
    protected String externalId;

    // When the transaction actually happened at the source (booking date / trade time),
    // as opposed to createdAt which is when the row was imported
    @Column(name = "transaction_date")
    protected LocalDateTime transactionDate;

    // Which bank/cash account the money moved to/from. Nullable: manual entries and rows
    // imported before this field existed have no account attached.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    protected Account account;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    protected LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    protected LocalDateTime updatedAt;
}
