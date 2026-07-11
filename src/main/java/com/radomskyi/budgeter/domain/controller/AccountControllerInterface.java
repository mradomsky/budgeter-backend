package com.radomskyi.budgeter.domain.controller;

import com.radomskyi.budgeter.dto.AccountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Accounts", description = "Balances of bank/cash accounts, kept up to date via Finanzguru imports")
@RequestMapping("/api/accounts")
public interface AccountControllerInterface {

    @GetMapping
    @Operation(summary = "Get the latest known balance of every tracked account")
    @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully")
    ResponseEntity<List<AccountResponse>> getAllAccounts();
}
