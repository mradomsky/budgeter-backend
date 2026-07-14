package com.radomskyi.budgeter.repository;

import com.radomskyi.budgeter.domain.entity.investment.Currency;
import com.radomskyi.budgeter.domain.entity.investment.FxRate;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    boolean existsByRateDateAndCurrency(LocalDate rateDate, Currency currency);

    // Latest stored rate on/before a date — carries the last fix over weekends/holidays
    Optional<FxRate> findFirstByCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
            Currency currency, LocalDate rateDate);

    Optional<FxRate> findFirstByCurrencyOrderByRateDateDesc(Currency currency);
}
