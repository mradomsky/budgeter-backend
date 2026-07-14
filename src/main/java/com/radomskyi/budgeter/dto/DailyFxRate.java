package com.radomskyi.budgeter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** ECB reference rate for one day: EUR per 1 unit of the foreign currency. */
public record DailyFxRate(LocalDate date, BigDecimal rateToEur) {}
