package com.radomskyi.budgeter.controller;

import com.radomskyi.budgeter.domain.controller.PortfolioHistoryControllerInterface;
import com.radomskyi.budgeter.dto.PortfolioHistoryResponse;
import com.radomskyi.budgeter.service.PortfolioHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PortfolioHistoryController implements PortfolioHistoryControllerInterface {

    private final PortfolioHistoryService portfolioHistoryService;

    @Override
    public ResponseEntity<PortfolioHistoryResponse> getHistory() {
        log.info("Received request for portfolio history");
        return ResponseEntity.ok(portfolioHistoryService.getHistory());
    }
}
