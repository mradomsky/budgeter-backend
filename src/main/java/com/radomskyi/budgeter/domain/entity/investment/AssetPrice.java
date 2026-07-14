package com.radomskyi.budgeter.domain.entity.investment;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * End-of-day close price of an asset, fetched from an external market data provider. One row per
 * asset per trading day; weekends/holidays have no rows and lookups carry the last close forward.
 */
@Entity
@Table(name = "asset_price", uniqueConstraints = @UniqueConstraint(columnNames = {"asset_id", "price_date"}))
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @NotNull
    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    // In the instrument's own currency (same as Investment.currency)
    @NotNull
    @Positive
    @Column(name = "close", nullable = false, precision = 15, scale = 8)
    private BigDecimal close;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @NotNull
    @Column(name = "source", nullable = false, length = 30)
    private String source;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
