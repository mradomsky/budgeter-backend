package com.radomskyi.budgeter.domain.service;

import com.radomskyi.budgeter.domain.entity.investment.Currency;
import com.radomskyi.budgeter.dto.DailyFxRate;
import java.time.LocalDate;
import java.util.List;

/** FX rate provider abstraction (EUR per 1 unit of the foreign currency). */
public interface FxRateProviderInterface {

    /** Daily rates for the currency in [from, to], oldest first. Weekends/holidays are absent. */
    List<DailyFxRate> getRatesToEur(Currency currency, LocalDate from, LocalDate to);
}
