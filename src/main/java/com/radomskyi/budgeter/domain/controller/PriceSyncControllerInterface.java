package com.radomskyi.budgeter.domain.controller;

import com.radomskyi.budgeter.dto.PriceSyncResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Price sync", description = "Manual trigger and backfill for daily market price synchronization")
@RequestMapping("/api/prices")
public interface PriceSyncControllerInterface {

    @PostMapping("/sync")
    @Operation(summary = "Fetch and store EOD prices and FX rates (idempotent; omit bounds to sync what's missing)")
    ResponseEntity<PriceSyncResult> sync(
            @Parameter(description = "Backfill start date, e.g. 2021-06-01")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "Backfill end date, defaults to yesterday")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to);
}
