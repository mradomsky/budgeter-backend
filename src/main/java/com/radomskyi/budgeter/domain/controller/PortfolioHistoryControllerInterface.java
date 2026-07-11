package com.radomskyi.budgeter.domain.controller;

import com.radomskyi.budgeter.dto.PortfolioHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Portfolio history", description = "Daily portfolio value history reconstructed from imported trades")
@RequestMapping("/api/portfolio")
public interface PortfolioHistoryControllerInterface {

    @GetMapping("/history")
    @Operation(summary = "Get daily portfolio value history with a lifetime performance summary")
    @ApiResponse(responseCode = "200", description = "History calculated successfully")
    ResponseEntity<PortfolioHistoryResponse> getHistory();
}
