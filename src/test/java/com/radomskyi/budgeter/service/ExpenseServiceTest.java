package com.radomskyi.budgeter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.radomskyi.budgeter.domain.entity.budgeting.Account;
import com.radomskyi.budgeter.domain.entity.budgeting.Expense;
import com.radomskyi.budgeter.domain.entity.budgeting.ExpenseCategory;
import com.radomskyi.budgeter.domain.entity.budgeting.Tag;
import com.radomskyi.budgeter.dto.ExpenseRequest;
import com.radomskyi.budgeter.dto.ExpenseResponse;
import com.radomskyi.budgeter.repository.AccountRepository;
import com.radomskyi.budgeter.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private ExpenseService expenseService;

    private Expense testExpense;
    private ExpenseRequest testExpenseRequest;

    @BeforeEach
    void setUp() {
        testExpense = Expense.builder()
                .id(1L)
                .amount(new BigDecimal("25.50"))
                .name("Test Expense")
                .category(ExpenseCategory.WANTS)
                .description("Test expense")
                .tags(Arrays.asList(Tag.FOOD, Tag.BARS_AND_RESTAURANTS))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testExpenseRequest = ExpenseRequest.builder()
                .amount(new BigDecimal("25.50"))
                .name("Test Expense")
                .category(ExpenseCategory.WANTS)
                .description("Test expense")
                .tags(Arrays.asList(Tag.FOOD, Tag.BARS_AND_RESTAURANTS))
                .build();
    }

    @Test
    void create_ShouldReturnExpenseResponse_WhenValidRequest() {
        // Given
        when(expenseRepository.save(any(Expense.class))).thenReturn(testExpense);

        // When
        ExpenseResponse result = expenseService.create(testExpenseRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test Expense");
        assertThat(result.getAmount()).isEqualTo(new BigDecimal("25.50"));
        assertThat(result.getCategory()).isEqualTo(ExpenseCategory.WANTS);
        assertThat(result.getDescription()).isEqualTo("Test expense");
        assertThat(result.getTags()).containsExactly(Tag.FOOD, Tag.BARS_AND_RESTAURANTS);

        verify(expenseRepository).save(any(Expense.class));
    }

    @Test
    void getById_ShouldReturnExpenseResponse_WhenExpenseExists() {
        // Given
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(testExpense));

        // When
        ExpenseResponse result = expenseService.getById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test Expense");
        assertThat(result.getAmount()).isEqualTo(new BigDecimal("25.50"));
        assertThat(result.getCategory()).isEqualTo(ExpenseCategory.WANTS);
        assertThat(result.getDescription()).isEqualTo("Test expense");
        assertThat(result.getTags()).containsExactly(Tag.FOOD, Tag.BARS_AND_RESTAURANTS);

        verify(expenseRepository).findById(1L);
    }

    @Test
    void getById_ShouldThrowException_WhenExpenseNotFound() {
        // Given
        when(expenseRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> expenseService.getById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Expense not found with id: 999");

        verify(expenseRepository).findById(999L);
    }

    @Test
    void getAll_ShouldReturnPageOfExpenseResponses_WhenExpensesExist() {
        // Given
        List<Expense> expenses = Arrays.asList(testExpense);
        Page<Expense> expensePage = new PageImpl<>(expenses);
        Pageable pageable = PageRequest.of(0, 10);

        when(expenseRepository.findAll(pageable)).thenReturn(expensePage);

        // When
        Page<ExpenseResponse> result = expenseService.getAll(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).getAmount()).isEqualTo(new BigDecimal("25.50"));

        verify(expenseRepository).findAll(pageable);
    }

    @Test
    void update_ShouldReturnUpdatedExpenseResponse_WhenExpenseExists() {
        // Given
        ExpenseRequest updateRequest = ExpenseRequest.builder()
                .amount(new BigDecimal("30.00"))
                .name("Updated Expense")
                .category(ExpenseCategory.NEEDS)
                .description("Updated expense")
                .tags(Arrays.asList(Tag.TRANSPORT))
                .build();

        Expense updatedExpense = Expense.builder()
                .id(1L)
                .amount(new BigDecimal("30.00"))
                .name("Updated Expense")
                .category(ExpenseCategory.NEEDS)
                .description("Updated expense")
                .tags(Arrays.asList(Tag.TRANSPORT))
                .createdAt(testExpense.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        when(expenseRepository.findById(1L)).thenReturn(Optional.of(testExpense));
        when(expenseRepository.save(any(Expense.class))).thenReturn(updatedExpense);

        // When
        ExpenseResponse result = expenseService.update(1L, updateRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Updated Expense");
        assertThat(result.getAmount()).isEqualTo(new BigDecimal("30.00"));
        assertThat(result.getCategory()).isEqualTo(ExpenseCategory.NEEDS);
        assertThat(result.getDescription()).isEqualTo("Updated expense");
        assertThat(result.getTags()).containsExactly(Tag.TRANSPORT);

        verify(expenseRepository).findById(1L);
        verify(expenseRepository).save(any(Expense.class));
    }

    @Test
    void update_ShouldThrowException_WhenExpenseNotFound() {
        // Given
        when(expenseRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> expenseService.update(999L, testExpenseRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Expense not found with id: 999");

        verify(expenseRepository).findById(999L);
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void create_ShouldLinkAccount_WhenAccountIdProvided() {
        // Given
        Account account = Account.builder()
                .id(5L)
                .externalId("DE05")
                .name("Bargeld")
                .balance(BigDecimal.TEN)
                .currency("EUR")
                .build();
        ExpenseRequest requestWithAccount = ExpenseRequest.builder()
                .amount(new BigDecimal("25.50"))
                .name("Test Expense")
                .category(ExpenseCategory.WANTS)
                .accountId(5L)
                .build();
        Expense savedExpense = Expense.builder()
                .id(1L)
                .amount(new BigDecimal("25.50"))
                .name("Test Expense")
                .category(ExpenseCategory.WANTS)
                .account(account)
                .build();

        when(accountRepository.findById(5L)).thenReturn(Optional.of(account));
        when(expenseRepository.save(any(Expense.class))).thenReturn(savedExpense);

        // When
        ExpenseResponse result = expenseService.create(requestWithAccount);

        // Then
        assertThat(result.getAccountId()).isEqualTo(5L);
        assertThat(result.getAccountName()).isEqualTo("Bargeld");
    }

    @Test
    void create_ShouldThrowException_WhenAccountIdDoesNotExist() {
        // Given
        ExpenseRequest requestWithAccount = ExpenseRequest.builder()
                .amount(new BigDecimal("25.50"))
                .name("Test Expense")
                .category(ExpenseCategory.WANTS)
                .accountId(999L)
                .build();

        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> expenseService.create(requestWithAccount))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Account not found with id: 999");

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void delete_ShouldDeleteExpense_WhenExpenseExists() {
        // Given
        when(expenseRepository.existsById(1L)).thenReturn(true);

        // When
        expenseService.delete(1L);

        // Then
        verify(expenseRepository).existsById(1L);
        verify(expenseRepository).deleteById(1L);
    }

    @Test
    void delete_ShouldThrowException_WhenExpenseNotFound() {
        // Given
        when(expenseRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> expenseService.delete(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Expense not found with id: 999");

        verify(expenseRepository).existsById(999L);
        verify(expenseRepository, never()).deleteById(anyLong());
    }
}
