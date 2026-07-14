package com.radomskyi.budgeter.domain.service;

import com.radomskyi.budgeter.dto.PriceSyncResult;
import java.time.LocalDate;

public interface PriceSyncServiceInterface {

    /**
     * Fetch and store EOD prices and FX rates. Null bounds mean: from the day after the last
     * stored row (or the configured backfill start), up to yesterday.
     */
    PriceSyncResult sync(LocalDate from, LocalDate to);
}
