package com.radomskyi.budgeter.service;

import com.radomskyi.budgeter.domain.entity.budgeting.Account;
import com.radomskyi.budgeter.domain.entity.budgeting.Income;
import com.radomskyi.budgeter.domain.service.IncomeServiceInterface;
import com.radomskyi.budgeter.dto.IncomeRequest;
import com.radomskyi.budgeter.dto.IncomeResponse;
import com.radomskyi.budgeter.exception.AccountNotFoundException;
import com.radomskyi.budgeter.exception.IncomeNotFoundException;
import com.radomskyi.budgeter.repository.AccountRepository;
import com.radomskyi.budgeter.repository.IncomeRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class IncomeService implements IncomeServiceInterface {

    private final IncomeRepository incomeRepository;
    private final AccountRepository accountRepository;

    /** Create a new income */
    @Override
    @Transactional
    public IncomeResponse create(IncomeRequest request) {
        log.info("Creating new income with amount: {} and category: {}", request.getAmount(), request.getCategory());

        Income income = Income.builder()
                .amount(request.getAmount())
                .name(request.getName())
                .category(request.getCategory())
                .description(request.getDescription())
                .tags(request.getTags())
                .account(resolveAccount(request.getAccountId()))
                .transactionDate(
                        request.getTransactionDate() != null ? request.getTransactionDate() : LocalDateTime.now())
                .build();

        Income savedIncome = incomeRepository.save(income);
        log.info("Successfully created income with id: {}", savedIncome.getId());

        return mapToResponse(savedIncome);
    }

    /** Get income by ID */
    @Override
    public IncomeResponse getById(Long id) {
        log.info("Fetching income with id: {}", id);

        Income income = incomeRepository
                .findById(id)
                .orElseThrow(() -> new IncomeNotFoundException("Income not found with id: " + id));

        return mapToResponse(income);
    }

    /** Get all incomes with pagination */
    @Override
    public Page<IncomeResponse> getAll(Pageable pageable) {
        log.info("Fetching all incomes with pagination: {}", pageable);

        Page<Income> incomes = incomeRepository.findAll(pageable);
        return incomes.map(this::mapToResponse);
    }

    /** Update an existing income */
    @Override
    @Transactional
    public IncomeResponse update(Long id, IncomeRequest request) {
        log.info("Updating income with id: {}", id);

        Income existingIncome = incomeRepository
                .findById(id)
                .orElseThrow(() -> new IncomeNotFoundException("Income not found with id: " + id));

        existingIncome.setAmount(request.getAmount());
        existingIncome.setName(request.getName());
        existingIncome.setCategory(request.getCategory());
        existingIncome.setDescription(request.getDescription());
        existingIncome.setTags(request.getTags());
        existingIncome.setAccount(resolveAccount(request.getAccountId()));
        if (request.getTransactionDate() != null) {
            existingIncome.setTransactionDate(request.getTransactionDate());
        }

        Income updatedIncome = incomeRepository.save(existingIncome);
        log.info("Successfully updated income with id: {}", updatedIncome.getId());

        return mapToResponse(updatedIncome);
    }

    /** Delete an income by ID */
    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Deleting income with id: {}", id);

        if (!incomeRepository.existsById(id)) {
            throw new IncomeNotFoundException("Income not found with id: " + id);
        }

        incomeRepository.deleteById(id);
        log.info("Successfully deleted income with id: {}", id);
    }

    /** Resolve an optional account id to an Account, throwing if it doesn't exist */
    private Account resolveAccount(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository
                .findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + accountId));
    }

    /** Map Income entity to IncomeResponse DTO */
    private IncomeResponse mapToResponse(Income income) {
        Account account = income.getAccount();
        return IncomeResponse.builder()
                .id(income.getId())
                .name(income.getName())
                .amount(income.getAmount())
                .category(income.getCategory())
                .description(income.getDescription())
                .tags(income.getTags())
                .accountId(account != null ? account.getId() : null)
                .accountName(account != null ? account.getName() : null)
                .transactionDate(
                        income.getTransactionDate() != null ? income.getTransactionDate() : income.getCreatedAt())
                .createdAt(income.getCreatedAt())
                .updatedAt(income.getUpdatedAt())
                .build();
    }
}
