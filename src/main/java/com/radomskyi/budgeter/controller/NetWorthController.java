package com.radomskyi.budgeter.controller;

import com.radomskyi.budgeter.domain.controller.NetWorthControllerInterface;
import com.radomskyi.budgeter.dto.NetWorthResponse;
import com.radomskyi.budgeter.service.NetWorthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class NetWorthController implements NetWorthControllerInterface {

    private final NetWorthService netWorthService;

    @Override
    public ResponseEntity<NetWorthResponse> getNetWorth() {
        log.info("Received request to calculate net worth");
        return ResponseEntity.ok(netWorthService.getNetWorth());
    }
}
