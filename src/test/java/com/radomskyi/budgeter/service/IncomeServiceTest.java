package com.radomskyi.budgeter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.radomskyi.budgeter.domain.entity.budgeting.Account;
import com.radomskyi.budgeter.domain.entity.budgeting.Income;
import com.radomskyi.budgeter.domain.entity.budgeting.IncomeCategory;
import com.radomskyi.budgeter.domain.entity.budgeting.Tag;
import com.radomskyi.budgeter.dto.IncomeRequest;
import com.radomskyi.budgeter.dto.IncomeResponse;
import com.radomskyi.budgeter.repository.AccountRepository;
import com.radomskyi.budgeter.repository.IncomeRepository;
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
class IncomeServiceTest {
    // fixme add one more testIncome object and adjust tests accordingly

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private IncomeService incomeService;

    private Income testIncome;
    private Income testIncome2;
    private IncomeRequest testIncomeRequest;

    @BeforeEach
    void setUp() {
        testIncome = Income.builder()
                .id(1L)
                .amount(new BigDecimal("3500.00"))
                .name("Monthly Salary")
                .category(IncomeCategory.SALARY)
                .description("Monthly salary")
                .tags(Arrays.asList(Tag.BANKING_AND_TAXES))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testIncome2 = Income.builder()
                .id(2L)
                .amount(new BigDecimal("1500.00"))
                .name("Freelance Project")
                .category(IncomeCategory.FREELANCE)
                .description("Freelance project")
                .tags(Arrays.asList(Tag.OTHER))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testIncomeRequest = IncomeRequest.builder()
                .amount(new BigDecimal("3500.00"))
                .name("Monthly Salary")
                .category(IncomeCategory.SALARY)
                .description("Monthly salary")
                .tags(Arrays.asList(Tag.BANKING_AND_TAXES))
                .build();
    }

    @Test
    void create_ShouldReturnCreatedIncomeResponse() {
        // Given
        when(incomeRepository.save(any(Income.class))).thenReturn(testIncome);

        // When
        IncomeResponse result = incomeService.create(testIncomeRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Monthly Salary");
        assertThat(result.getAmount()).isEqualTo(new BigDecimal("3500.00"));
        assertThat(result.getCategory()).isEqualTo(IncomeCategory.SALARY);
        assertThat(result.getDescription()).isEqualTo("Monthly salary");
        assertThat(result.getTags()).isEqualTo(Arrays.asList(Tag.BANKING_AND_TAXES));

        verify(incomeRepository).save(any(Income.class));
    }

    @Test
    void getById_WhenIncomeExists_ShouldReturnIncomeResponse() {
        // Given
        when(incomeRepository.findById(1L)).thenReturn(Optional.of(testIncome));

        // When
        IncomeResponse result = incomeService.getById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Monthly Salary");
        assertThat(result.getAmount()).isEqualTo(new BigDecimal("3500.00"));
        assertThat(result.getCategory()).isEqualTo(IncomeCategory.SALARY);

        verify(incomeRepository).findById(1L);
    }

    @Test
    void getById_WhenIncomeDoesNotExist_ShouldThrowException() {
        // Given
        when(incomeRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> incomeService.getById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Income not found with id: 999");

        verify(incomeRepository).findById(999L);
    }

    @Test
    void getAll_ShouldReturnPageOfIncomeResponses() {
        // Given
        List<Income> incomes = Arrays.asList(testIncome);
        Page<Income> incomePage = new PageImpl<>(incomes, PageRequest.of(0, 10), 1);
        when(incomeRepository.findAll(any(Pageable.class))).thenReturn(incomePage);

        // When
        Page<IncomeResponse> result = incomeService.getAll(PageRequest.of(0, 10));

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).getAmount()).isEqualTo(new BigDecimal("3500.00"));

        verify(incomeRepository).findAll(any(Pageable.class));
    }

    @Test
    void update_WhenIncomeExists_ShouldReturnUpdatedIncomeResponse() {
        // Given
        Income updatedIncome = Income.builder()
                .id(1L)
                .amount(new BigDecimal("4000.00"))
                .name("Updated Salary")
                .category(IncomeCategory.SALARY)
                .description("Updated salary")
                .tags(Arrays.asList(Tag.BANKING_AND_TAXES))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(incomeRepository.findById(1L)).thenReturn(Optional.of(testIncome));
        when(incomeRepository.save(any(Income.class))).thenReturn(updatedIncome);

        IncomeRequest updateRequest = IncomeRequest.builder()
                .amount(new BigDecimal("4000.00"))
                .name("Updated Salary")
                .category(IncomeCategory.SALARY)
                .description("Updated salary")
                .tags(Arrays.asList(Tag.BANKING_AND_TAXES))
                .build();

        // When
        IncomeResponse result = incomeService.update(1L, updateRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Updated Salary");
        assertThat(result.getAmount()).isEqualTo(new BigDecimal("4000.00"));
        assertThat(result.getDescription()).isEqualTo("Updated salary");

        verify(incomeRepository).findById(1L);
        verify(incomeRepository).save(any(Income.class));
    }

    @Test
    void update_WhenIncomeDoesNotExist_ShouldThrowException() {
        // Given
        when(incomeRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> incomeService.update(999L, testIncomeRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Income not found with id: 999");

        verify(incomeRepository).findById(999L);
        verify(incomeRepository, never()).save(any(Income.class));
    }

    @Test
    void create_ShouldLinkAccount_WhenAccountIdProvided() {
        // Given
        Account account = Account.builder()
                .id(5L)
                .externalId("DE05")
                .name("Main Account")
                .balance(BigDecimal.TEN)
                .currency("EUR")
                .build();
        IncomeRequest requestWithAccount = IncomeRequest.builder()
                .amount(new BigDecimal("3500.00"))
                .name("Salary")
                .category(IncomeCategory.SALARY)
                .accountId(5L)
                .build();
        Income savedIncome = Income.builder()
                .id(1L)
                .amount(new BigDecimal("3500.00"))
                .name("Salary")
                .category(IncomeCategory.SALARY)
                .account(account)
                .build();

        when(accountRepository.findById(5L)).thenReturn(Optional.of(account));
        when(incomeRepository.save(any(Income.class))).thenReturn(savedIncome);

        // When
        IncomeResponse result = incomeService.create(requestWithAccount);

        // Then
        assertThat(result.getAccountId()).isEqualTo(5L);
        assertThat(result.getAccountName()).isEqualTo("Main Account");
    }

    @Test
    void create_ShouldThrowException_WhenAccountIdDoesNotExist() {
        // Given
        IncomeRequest requestWithAccount = IncomeRequest.builder()
                .amount(new BigDecimal("3500.00"))
                .name("Salary")
                .category(IncomeCategory.SALARY)
                .accountId(999L)
                .build();

        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> incomeService.create(requestWithAccount))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Account not found with id: 999");

        verify(incomeRepository, never()).save(any(Income.class));
    }

    @Test
    void delete_WhenIncomeExists_ShouldDeleteSuccessfully() {
        // Given
        when(incomeRepository.existsById(1L)).thenReturn(true);

        // When
        incomeService.delete(1L);

        // Then
        verify(incomeRepository).existsById(1L);
        verify(incomeRepository).deleteById(1L);
        // fixme check that the testIncome object is actually deleted
    }

    @Test
    void delete_WhenIncomeDoesNotExist_ShouldThrowException() {
        // Given
        when(incomeRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> incomeService.delete(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Income not found with id: 999");

        verify(incomeRepository).existsById(999L);
        verify(incomeRepository, never()).deleteById(anyLong());
    }

    @Test
    void getAll_ShouldReturnMultipleIncomes_WhenMultipleIncomesExist() {
        // Given
        List<Income> incomes = Arrays.asList(testIncome, testIncome2);
        Page<Income> incomePage = new PageImpl<>(incomes, PageRequest.of(0, 10), 2);
        when(incomeRepository.findAll(any(Pageable.class))).thenReturn(incomePage);

        // When
        Page<IncomeResponse> result = incomeService.getAll(PageRequest.of(0, 10));

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).getAmount()).isEqualTo(new BigDecimal("3500.00"));
        assertThat(result.getContent().get(1).getId()).isEqualTo(2L);
        assertThat(result.getContent().get(1).getAmount()).isEqualTo(new BigDecimal("1500.00"));

        verify(incomeRepository).findAll(any(Pageable.class));
    }

    @Test
    void delete_ShouldActuallyRemoveIncome() {
        // Given
        when(incomeRepository.existsById(1L)).thenReturn(true);
        when(incomeRepository.findById(1L)).thenReturn(Optional.of(testIncome));

        // When
        incomeService.delete(1L);

        // Then
        verify(incomeRepository).existsById(1L);
        verify(incomeRepository).deleteById(1L);

        // Verify the income is actually gone by checking findById returns empty
        when(incomeRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> incomeService.getById(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Income not found with id: 1");
    }
}
