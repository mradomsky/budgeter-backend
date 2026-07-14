package com.radomskyi.budgeter.repository;

import com.radomskyi.budgeter.domain.entity.investment.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvestmentTransactionRepository extends JpaRepository<InvestmentTransaction, Long> {

    // Check whether a transaction from a source system was already imported
    boolean existsByExternalId(String externalId);

    // Find all transactions for a specific investment
    List<InvestmentTransaction> findByInvestment(Investment investment);

    // Find all transactions for a specific investment with pagination
    Page<InvestmentTransaction> findByInvestment(Investment investment, Pageable pageable);

    // Find all transactions for a specific investment ordered by creation date (oldest first for FIFO)
    List<InvestmentTransaction> findByInvestmentOrderByCreatedAtAsc(Investment investment);

    // Find BUY transactions for an investment (for cost basis calculation)
    List<InvestmentTransaction> findByInvestmentAndTransactionTypeOrderByCreatedAtAsc(
            Investment investment, InvestmentTransactionType transactionType);

    // Find SELL transactions for an investment (for realized gains/losses)
    List<InvestmentTransaction> findByInvestmentAndTransactionTypeOrderByCreatedAtDesc(
            Investment investment, InvestmentTransactionType transactionType);

    // Find transactions by investment and date range
    List<InvestmentTransaction> findByInvestmentAndCreatedAtBetween(
            Investment investment, LocalDateTime startDate, LocalDateTime endDate);

    // Find transactions by investment, type and date range
    List<InvestmentTransaction> findByInvestmentAndTransactionTypeAndCreatedAtBetween(
            Investment investment,
            InvestmentTransactionType transactionType,
            LocalDateTime startDate,
            LocalDateTime endDate);

    // Find transactions by currency
    List<InvestmentTransaction> findByCurrency(Currency currency);

    // Find transactions by currency with pagination
    Page<InvestmentTransaction> findByCurrency(Currency currency, Pageable pageable);

    // Find transactions within a date range
    List<InvestmentTransaction> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    // Find transactions within a date range with pagination
    Page<InvestmentTransaction> findByCreatedAtBetween(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // Find transactions by asset type
    @Query("SELECT it FROM InvestmentTransaction it WHERE it.investment.asset.assetType = :assetType")
    List<InvestmentTransaction> findByAssetType(@Param("assetType") AssetType assetType);

    // Find transactions by investment style
    @Query("SELECT it FROM InvestmentTransaction it WHERE it.investment.asset.investmentStyle = :investmentStyle")
    List<InvestmentTransaction> findByInvestmentStyle(@Param("investmentStyle") InvestmentStyle investmentStyle);

    // Calculate total units held for an investment (current position)
    @Query("SELECT COALESCE(SUM(CASE WHEN it.transactionType = 'BUY' THEN it.units ELSE -it.units END), 0) "
            + "FROM InvestmentTransaction it WHERE it.investment = :investment")
    BigDecimal getTotalUnitsForInvestment(@Param("investment") Investment investment);

    // Calculate total cost basis for an investment (sum of all BUY transactions in EUR)
    @Query("SELECT COALESCE(SUM(it.amount), 0) FROM InvestmentTransaction it "
            + "WHERE it.investment = :investment AND it.transactionType = 'BUY'")
    BigDecimal getTotalCostBasisForInvestment(@Param("investment") Investment investment);

    // Calculate total realized gains/losses for an investment
    @Query("SELECT COALESCE(SUM(it.realizedGainLoss), 0) FROM InvestmentTransaction it "
            + "WHERE it.investment = :investment AND it.transactionType = 'SELL' AND it.realizedGainLoss IS NOT NULL")
    BigDecimal getTotalRealizedGainsForInvestment(@Param("investment") Investment investment);

    // Calculate total portfolio value by asset type
    @Query("SELECT it.investment.asset.assetType, SUM(it.amount) FROM InvestmentTransaction it "
            + "WHERE it.transactionType = 'BUY' GROUP BY it.investment.asset.assetType")
    List<Object[]> getPortfolioValueByAssetType();

    // Calculate total portfolio value by investment style
    @Query("SELECT it.investment.asset.investmentStyle, SUM(it.amount) FROM InvestmentTransaction it "
            + "WHERE it.transactionType = 'BUY' GROUP BY it.investment.asset.investmentStyle")
    List<Object[]> getPortfolioValueByInvestmentStyle();

    // Calculate current portfolio value in EUR
    @Query("SELECT SUM(CASE WHEN it.transactionType = 'BUY' THEN it.amount ELSE -it.amount END) "
            + "FROM InvestmentTransaction it")
    BigDecimal getTotalPortfolioValue();

    // Find transactions for tax year (German tax year is calendar year)
    List<InvestmentTransaction> findByCreatedAtBetweenAndTransactionType(
            LocalDateTime startDate, LocalDateTime endDate, InvestmentTransactionType transactionType);

    // Count transactions by type for reporting
    long countByTransactionType(InvestmentTransactionType transactionType);

    // Load every transaction with its investment and asset eagerly, for portfolio history replay
    @Query("SELECT it FROM InvestmentTransaction it JOIN FETCH it.investment i JOIN FETCH i.asset")
    List<InvestmentTransaction> findAllWithInvestment();

    // Find transactions ordered by creation date (newest first)
    List<InvestmentTransaction> findAllByOrderByCreatedAtDesc();

    // Find transactions ordered by creation date (newest first) with pagination
    Page<InvestmentTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
