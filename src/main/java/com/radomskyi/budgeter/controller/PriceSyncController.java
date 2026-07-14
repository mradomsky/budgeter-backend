package com.radomskyi.budgeter.controller;

import com.radomskyi.budgeter.domain.controller.PriceSyncControllerInterface;
import com.radomskyi.budgeter.dto.PriceSyncResult;
import com.radomskyi.budgeter.service.PriceSyncService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PriceSyncController implements PriceSyncControllerInterface {

    private final PriceSyncService priceSyncService;

    @Override
    public ResponseEntity<PriceSyncResult> sync(LocalDate from, LocalDate to) {
        log.info("Received manual price sync request (from={}, to={})", from, to);
        return ResponseEntity.ok(priceSyncService.sync(from, to));
    }
}
