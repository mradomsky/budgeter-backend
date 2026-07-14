package com.radomskyi.budgeter.controller;

import com.radomskyi.budgeter.domain.controller.AccountControllerInterface;
import com.radomskyi.budgeter.dto.AccountResponse;
import com.radomskyi.budgeter.service.AccountService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AccountController implements AccountControllerInterface {

    private final AccountService accountService;

    @Override
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        log.info("Received request to list account balances");
        return ResponseEntity.ok(accountService.getAllAccounts());
    }
}
