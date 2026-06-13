package com.radomskyi.budgeter.domain.entity.investment;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "investment")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Investment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @NotNull
    @DecimalMin(value = "0.00")
    @Column(name = "total_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    @NotNull
    @DecimalMin(value = "0.00")
    @Column(name = "total_units", nullable = false, precision = 15, scale = 8)
    private BigDecimal totalUnits;

    @NotNull
    @DecimalMin(value = "0.00")
    @Column(name = "cost_basis", nullable = false, precision = 15, scale = 8)
    private BigDecimal costBasis;

    @OneToMany(mappedBy = "investment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InvestmentTransaction> transactions = new ArrayList<>();

    @DecimalMin(value = "0.00000001")
    @Column(name = "latest_price", precision = 15, scale = 8)
    private BigDecimal latestPrice;

    // EUR per one unit of the instrument currency at the time of the latest trade —
    // multiply latestPrice by this to get an EUR price. Null when the price is already in EUR.
    @DecimalMin(value = "0.00000001")
    @Column(name = "latest_exchange_rate", precision = 15, scale = 8)
    private BigDecimal latestExchangeRate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "brokerage", length = 100)
    private String brokerage;

    /**
     * Calculates total realized gain/loss by summing up all SELL transaction gains/losses.
     * This is a computed field that aggregates data from the transactions list.
     *
     * @return The total realized gain/loss across all SELL transactions
     */
    public BigDecimal getRealizedGainLoss() {
        if (transactions == null || transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return transactions.stream()
                .filter(t -> t.getRealizedGainLoss() != null)
                .map(InvestmentTransaction::getRealizedGainLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Static factory method to create a new Investment with initial values.
     *
     * @param asset The asset for this investment
     * @param currency The currency for this investment
     * @param brokerage The brokerage company holding the portfolio
     * @return A new Investment instance
     */
    public static Investment createNew(Asset asset, Currency currency, String brokerage) {
        return Investment.builder()
                .asset(asset)
                .totalCost(BigDecimal.ZERO)
                .totalUnits(BigDecimal.ZERO)
                .costBasis(BigDecimal.ZERO)
                .currency(currency)
                .brokerage(brokerage)
                .build();
    }

    /**
     * Updates the cost basis based on current total cost and total units.
     * Cost basis = Total Cost / Total Units
     */
    public void updateCostBasis() {
        if (totalUnits != null && totalUnits.compareTo(BigDecimal.ZERO) > 0) {
            this.costBasis = totalCost.divide(totalUnits, 8, java.math.RoundingMode.HALF_UP);
        } else {
            this.costBasis = BigDecimal.ZERO;
        }
    }

    /**
     * Adds a transaction to this investment and updates the investment metrics.
     * Fees are included in the total cost calculation to accurately reflect the cost basis.
     * For SELL transactions, calculates and sets the realized gain/loss.
     *
     * @param transaction The transaction to add
     */
    public void addTransaction(InvestmentTransaction transaction) {
        transactions.add(transaction);
        transaction.setInvestment(this);

        BigDecimal fees = transaction.getFees() != null ? transaction.getFees() : BigDecimal.ZERO;

        // Update total units and total cost based on transaction type
        if (transaction.getTransactionType() == InvestmentTransactionType.BUY) {
            totalUnits = totalUnits.add(transaction.getUnits());
            // Total cost includes purchase price plus fees
            BigDecimal purchaseCost = transaction.getUnits().multiply(transaction.getPricePerUnit());
            totalCost = totalCost.add(purchaseCost).add(fees);
        } else if (transaction.getTransactionType() == InvestmentTransactionType.SELL) {
            // Calculate realized gain/loss before updating totals
            BigDecimal saleProceeds = transaction.getUnits().multiply(transaction.getPricePerUnit());
            BigDecimal costOfSoldUnits = transaction.getUnits().multiply(costBasis);
            BigDecimal transactionGainLoss =
                    saleProceeds.subtract(costOfSoldUnits).subtract(fees);

            // Set the realized gain/loss on the transaction
            transaction.setRealizedGainLoss(transactionGainLoss);

            // Update totals. Imports may cover a partial time window (sells of units bought
            // before the window), so clamp at zero instead of violating the non-negative invariants
            totalUnits = totalUnits.subtract(transaction.getUnits());
            totalCost = totalCost.subtract(costOfSoldUnits);
            if (totalUnits.compareTo(BigDecimal.ZERO) < 0) {
                totalUnits = BigDecimal.ZERO;
            }
            if (totalCost.compareTo(BigDecimal.ZERO) < 0) {
                totalCost = BigDecimal.ZERO;
            }
        }

        // Update cost basis
        updateCostBasis();

        // Update latest price from the transaction — but not for dividends, where
        // pricePerUnit is the payout per share, not the market price
        if (transaction.getTransactionType() == InvestmentTransactionType.BUY
                || transaction.getTransactionType() == InvestmentTransactionType.SELL) {
            this.latestPrice = transaction.getPricePerUnit();
            this.latestExchangeRate = transaction.getExchangeRate();
        }
    }

    /**
     * Current market value in EUR: totalUnits × latestPrice × latestExchangeRate.
     * Falls back to totalCost when no price is known yet.
     *
     * @return The current value of this investment in EUR
     */
    public BigDecimal getCurrentValueEur() {
        if (latestPrice == null || totalUnits == null) {
            return totalCost != null ? totalCost : BigDecimal.ZERO;
        }
        BigDecimal value = totalUnits.multiply(latestPrice);
        if (latestExchangeRate != null) {
            value = value.multiply(latestExchangeRate);
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Removes a transaction from this investment.
     *
     * @param transaction The transaction to remove
     */
    public void removeTransaction(InvestmentTransaction transaction) {
        transactions.remove(transaction);
        transaction.setInvestment(null);
    }
}
