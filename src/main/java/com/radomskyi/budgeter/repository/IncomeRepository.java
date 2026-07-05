package com.radomskyi.budgeter.repository;

import com.radomskyi.budgeter.domain.entity.budgeting.Income;
import com.radomskyi.budgeter.domain.entity.budgeting.IncomeCategory;
import com.radomskyi.budgeter.domain.entity.budgeting.Tag;
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
public interface IncomeRepository extends JpaRepository<Income, Long> {

    // Check whether a transaction from a source system was already imported
    boolean existsByExternalId(String externalId);

    // Find incomes by category
    List<Income> findByCategory(IncomeCategory category);

    // Find incomes by category with pagination
    Page<Income> findByCategory(IncomeCategory category, Pageable pageable);

    // Find incomes within a date range
    List<Income> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    // Find incomes within a date range with pagination
    Page<Income> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // Find incomes by category and date range
    List<Income> findByCategoryAndCreatedAtBetween(
            IncomeCategory category, LocalDateTime startDate, LocalDateTime endDate);

    // Find incomes by category and date range with pagination
    Page<Income> findByCategoryAndCreatedAtBetween(
            IncomeCategory category, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // Find incomes with amount greater than specified amount
    List<Income> findByAmountGreaterThan(BigDecimal amount);

    // Find incomes with amount between specified amounts
    List<Income> findByAmountBetween(BigDecimal minAmount, BigDecimal maxAmount);

    // Find incomes by description containing text (case insensitive)
    List<Income> findByDescriptionContainingIgnoreCase(String description);

    // Find incomes by name containing text (case insensitive)
    List<Income> findByNameContainingIgnoreCase(String name);

    // Find incomes by name containing text with pagination
    Page<Income> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // Find incomes by tag
    @Query("SELECT i FROM Income i WHERE :tag MEMBER OF i.tags")
    List<Income> findByTag(@Param("tag") Tag tag);

    // Find incomes by multiple tags
    @Query("SELECT DISTINCT i FROM Income i JOIN i.tags t WHERE t IN :tags")
    List<Income> findByTagsIn(@Param("tags") List<Tag> tags);

    // Find incomes by tag with pagination
    @Query("SELECT i FROM Income i WHERE :tag MEMBER OF i.tags")
    Page<Income> findByTag(@Param("tag") Tag tag, Pageable pageable);

    // Calculate total incomes by category
    @Query("SELECT SUM(i.amount) FROM Income i WHERE i.category = :category")
    BigDecimal sumByCategory(@Param("category") IncomeCategory category);

    // Calculate total incomes by category within date range
    @Query(
            "SELECT SUM(i.amount) FROM Income i WHERE i.category = :category AND i.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumByCategoryAndDateRange(
            @Param("category") IncomeCategory category,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // Calculate total incomes within date range
    @Query("SELECT SUM(i.amount) FROM Income i WHERE i.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // Find incomes ordered by creation date (newest first)
    List<Income> findAllByOrderByCreatedAtDesc();

    // Find incomes ordered by creation date (newest first) with pagination
    Page<Income> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Find incomes ordered by amount (highest first)
    List<Income> findAllByOrderByAmountDesc();

    // Find incomes ordered by amount (highest first) with pagination
    Page<Income> findAllByOrderByAmountDesc(Pageable pageable);
}
