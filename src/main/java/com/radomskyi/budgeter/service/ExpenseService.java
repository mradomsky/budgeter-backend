package com.radomskyi.budgeter.service;

import com.radomskyi.budgeter.domain.entity.budgeting.Account;
import com.radomskyi.budgeter.domain.entity.budgeting.Expense;
import com.radomskyi.budgeter.domain.service.ExpenseServiceInterface;
import com.radomskyi.budgeter.dto.ExpenseRequest;
import com.radomskyi.budgeter.dto.ExpenseResponse;
import com.radomskyi.budgeter.exception.AccountNotFoundException;
import com.radomskyi.budgeter.exception.ExpenseNotFoundException;
import com.radomskyi.budgeter.repository.AccountRepository;
import com.radomskyi.budgeter.repository.ExpenseRepository;
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
public class ExpenseService implements ExpenseServiceInterface {

    private final ExpenseRepository expenseRepository;
    private final AccountRepository accountRepository;

    /** Create a new expense */
    @Override
    @Transactional
    public ExpenseResponse create(ExpenseRequest request) {
        log.info("Creating new expense with amount: {} and category: {}", request.getAmount(), request.getCategory());

        Expense expense = Expense.builder()
                .amount(request.getAmount())
                .name(request.getName())
                .category(request.getCategory())
                .description(request.getDescription())
                .tags(request.getTags())
                .account(resolveAccount(request.getAccountId()))
                .transactionDate(
                        request.getTransactionDate() != null ? request.getTransactionDate() : LocalDateTime.now())
                .build();

        Expense savedExpense = expenseRepository.save(expense);
        log.info("Successfully created expense with id: {}", savedExpense.getId());

        return mapToResponse(savedExpense);
    }

    /** Get expense by ID */
    @Override
    public ExpenseResponse getById(Long id) {
        log.info("Fetching expense with id: {}", id);

        Expense expense = expenseRepository
                .findById(id)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found with id: " + id));

        return mapToResponse(expense);
    }

    /** Get all expenses with pagination */
    @Override
    public Page<ExpenseResponse> getAll(Pageable pageable) {
        log.info("Fetching all expenses with pagination: {}", pageable);

        Page<Expense> expenses = expenseRepository.findAll(pageable);
        return expenses.map(this::mapToResponse);
    }

    /** Update an existing expense */
    @Override
    @Transactional
    public ExpenseResponse update(Long id, ExpenseRequest request) {
        log.info("Updating expense with id: {}", id);

        Expense existingExpense = expenseRepository
                .findById(id)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found with id: " + id));

        existingExpense.setAmount(request.getAmount());
        existingExpense.setName(request.getName());
        existingExpense.setCategory(request.getCategory());
        existingExpense.setDescription(request.getDescription());
        existingExpense.setTags(request.getTags());
        existingExpense.setAccount(resolveAccount(request.getAccountId()));
        if (request.getTransactionDate() != null) {
            existingExpense.setTransactionDate(request.getTransactionDate());
        }

        Expense updatedExpense = expenseRepository.save(existingExpense);
        log.info("Successfully updated expense with id: {}", updatedExpense.getId());

        return mapToResponse(updatedExpense);
    }

    /** Delete an expense by ID */
    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Deleting expense with id: {}", id);

        if (!expenseRepository.existsById(id)) {
            throw new ExpenseNotFoundException("Expense not found with id: " + id);
        }

        expenseRepository.deleteById(id);
        log.info("Successfully deleted expense with id: {}", id);
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

    /** Map Expense entity to ExpenseResponse DTO */
    private ExpenseResponse mapToResponse(Expense expense) {
        Account account = expense.getAccount();
        return ExpenseResponse.builder()
                .id(expense.getId())
                .name(expense.getName())
                .amount(expense.getAmount())
                .category(expense.getCategory())
                .description(expense.getDescription())
                .tags(expense.getTags())
                .accountId(account != null ? account.getId() : null)
                .accountName(account != null ? account.getName() : null)
                .transactionDate(
                        expense.getTransactionDate() != null ? expense.getTransactionDate() : expense.getCreatedAt())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }
}
