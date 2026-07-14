package com.radomskyi.budgeter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One end-of-day close from a market data provider, in the instrument's currency. */
public record DailyPrice(LocalDate date, BigDecimal close) {}
