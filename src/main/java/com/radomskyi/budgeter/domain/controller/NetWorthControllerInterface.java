package com.radomskyi.budgeter.domain.controller;

import com.radomskyi.budgeter.dto.NetWorthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Net worth", description = "Aggregated portfolio value across all imported investments")
@RequestMapping("/api/net-worth")
public interface NetWorthControllerInterface {

    @GetMapping
    @Operation(summary = "Get current net worth with breakdowns by brokerage and asset type")
    @ApiResponse(responseCode = "200", description = "Net worth calculated successfully")
    ResponseEntity<NetWorthResponse> getNetWorth();
}
