package com.radomskyi.budgeter.service;

import com.radomskyi.budgeter.domain.entity.budgeting.Account;
import com.radomskyi.budgeter.domain.service.AccountServiceInterface;
import com.radomskyi.budgeter.dto.AccountResponse;
import com.radomskyi.budgeter.repository.AccountRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AccountService implements AccountServiceInterface {

    private final AccountRepository accountRepository;

    @Override
    public List<AccountResponse> getAllAccounts() {
        log.info("Fetching all account balances");
        return accountRepository.findAll().stream()
                .sorted(Comparator.comparing(Account::getName))
                .map(this::toResponse)
                .toList();
    }

    private AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .name(account.getName())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .balanceAsOf(account.getBalanceAsOf())
                .build();
    }
}
