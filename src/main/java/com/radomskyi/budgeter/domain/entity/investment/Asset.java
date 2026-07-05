package com.radomskyi.budgeter.domain.entity.investment;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "asset")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nullable: Trade Republic exports identify securities by ISIN only
    @Column(name = "ticker", length = 10)
    private String ticker;

    @NotNull
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "isin", length = 12)
    private String isin;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    private AssetType assetType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "investment_style", nullable = false, length = 20)
    private InvestmentStyle investmentStyle;
}
