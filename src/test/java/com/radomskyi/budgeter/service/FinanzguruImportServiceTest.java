package com.radomskyi.budgeter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.radomskyi.budgeter.domain.entity.budgeting.Account;
import com.radomskyi.budgeter.domain.entity.budgeting.Expense;
import com.radomskyi.budgeter.domain.entity.budgeting.ExpenseCategory;
import com.radomskyi.budgeter.domain.entity.budgeting.Income;
import com.radomskyi.budgeter.domain.entity.budgeting.IncomeCategory;
import com.radomskyi.budgeter.domain.entity.budgeting.Tag;
import com.radomskyi.budgeter.dto.ImportResult;
import com.radomskyi.budgeter.repository.AccountRepository;
import com.radomskyi.budgeter.repository.ExpenseRepository;
import com.radomskyi.budgeter.repository.IncomeRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class FinanzguruImportServiceTest {

    private static final String[] HEADERS = {
        "Buchungstag",
        "Referenzkonto",
        "Name Referenzkonto",
        "Betrag",
        "Kontostand",
        "Waehrung",
        "Beguenstigter/Auftraggeber",
        "Verwendungszweck",
        "Analyse-Hauptkategorie",
        "Analyse-Unterkategorie",
        "Analyse-Vertrag",
        "Analyse-Umbuchung",
        "Buchungs-ID"
    };

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private FinanzguruImportService importService;

    /** Builds an in-memory Finanzguru-style XLSX. Each row: date, account IBAN, account name,
     * amount, balance, currency, counterparty, purpose, main category, sub category, contract,
     * transfer, booking id. */
    private MockMultipartFile xlsxFile(Object[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Buchungen");
            CreationHelper helper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("dd.MM.yyyy"));

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(HEADERS[i]);
            }

            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object value = rows[r][c];
                    if (value == null) {
                        continue;
                    }
                    Cell cell = row.createCell(c);
                    if (value instanceof LocalDate date) {
                        cell.setCellValue(date);
                        cell.setCellStyle(dateStyle);
                    } else if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                    } else {
                        cell.setCellValue(value.toString());
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile(
                    "file",
                    "Export-Alle_Buchungen.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    @Test
    void importXlsx_ShouldCreateExpense_WhenAmountIsNegative() throws IOException {
        MockMultipartFile file = xlsxFile(new Object[][] {
            {
                LocalDate.of(2024, 8, 12),
                "DE05380700240067051300",
                "Persoenliches Konto",
                -21.43,
                5659.22,
                "EUR",
                "Kaufland",
                "KAUFLAND KOELN",
                "Essen & Trinken",
                "Lebensmittel",
                "nein",
                "nein",
                "booking-1"
            }
        });

        when(expenseRepository.existsByExternalId(anyString())).thenReturn(false);
        when(accountRepository.findByExternalId(anyString())).thenReturn(Optional.empty());

        ImportResult result = importService.importXlsx(file);

        assertThat(result.getImported()).isEqualTo(1);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());

        Expense expense = captor.getValue();
        assertThat(expense.getAmount()).isEqualByComparingTo(new BigDecimal("21.43"));
        assertThat(expense.getName()).isEqualTo("Kaufland");
        assertThat(expense.getCategory()).isEqualTo(ExpenseCategory.NEEDS);
        assertThat(expense.getTags()).containsExactly(Tag.FOOD);
        assertThat(expense.getExternalId()).isEqualTo("booking-1");
        assertThat(expense.getTransactionDate()).isEqualTo(LocalDateTime.of(2024, 8, 12, 0, 0));
        verify(incomeRepository, never()).save(any());

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        assertThat(account.getExternalId()).isEqualTo("DE05380700240067051300");
        assertThat(account.getName()).isEqualTo("Persoenliches Konto");
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("5659.22"));
        assertThat(account.getCurrency()).isEqualTo("EUR");
        assertThat(account.getBalanceAsOf()).isEqualTo(LocalDateTime.of(2024, 8, 12, 0, 0));
    }

    @Test
    void importXlsx_ShouldCreateSalaryIncome_WhenAmountIsPositive() throws IOException {
        MockMultipartFile file = xlsxFile(new Object[][] {
            {
                LocalDate.of(2024, 8, 28),
                null,
                null,
                3200.00,
                null,
                null,
                "Arbeitgeber GmbH",
                "Gehalt August",
                "Einnahmen",
                "Lohn / Gehalt",
                "nein",
                "nein",
                "booking-2"
            }
        });

        when(incomeRepository.existsByExternalId(anyString())).thenReturn(false);

        ImportResult result = importService.importXlsx(file);

        assertThat(result.getImported()).isEqualTo(1);

        ArgumentCaptor<Income> captor = ArgumentCaptor.forClass(Income.class);
        verify(incomeRepository).save(captor.capture());

        Income income = captor.getValue();
        assertThat(income.getAmount()).isEqualByComparingTo(new BigDecimal("3200.00"));
        assertThat(income.getCategory()).isEqualTo(IncomeCategory.SALARY);
        assertThat(income.getExternalId()).isEqualTo("booking-2");
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void importXlsx_ShouldSkipTransfersAndSavings_WhenRowsAreNotRealExpenses() throws IOException {
        MockMultipartFile file = xlsxFile(new Object[][] {
            // Transfer between own accounts
            {
                LocalDate.of(2024, 8, 1),
                null,
                null,
                -500.00,
                null,
                null,
                "Eigenes Konto",
                "Umbuchung",
                "Sonstiges",
                "Bargeld",
                "nein",
                "ja",
                "b-1"
            },
            // Transfer to brokerage — covered by investment imports
            {
                LocalDate.of(2024, 8, 2),
                null,
                null,
                -300.00,
                null,
                null,
                "Trading 212",
                "Einzahlung",
                "Sparen",
                "Kapitalanlage",
                "nein",
                "nein",
                "b-2"
            },
            // Zero amount
            {
                LocalDate.of(2024, 8, 3),
                null,
                null,
                0.00,
                null,
                null,
                "Test",
                "Nichts",
                "Sonstiges",
                "Sonstige Ausgaben",
                "nein",
                "nein",
                "b-3"
            }
        });

        ImportResult result = importService.importXlsx(file);

        assertThat(result.getImported()).isZero();
        assertThat(result.getSkippedRows()).isEqualTo(3);
        verify(expenseRepository, never()).save(any());
        verify(incomeRepository, never()).save(any());
    }

    @Test
    void importXlsx_ShouldSkipDuplicates_WhenBookingIdAlreadyImported() throws IOException {
        MockMultipartFile file = xlsxFile(new Object[][] {
            {
                LocalDate.of(2024, 8, 12),
                null,
                null,
                -21.43,
                null,
                null,
                "Kaufland",
                "KAUFLAND KOELN",
                "Essen & Trinken",
                "Lebensmittel",
                "nein",
                "nein",
                "booking-1"
            }
        });

        when(expenseRepository.existsByExternalId("booking-1")).thenReturn(true);

        ImportResult result = importService.importXlsx(file);

        assertThat(result.getImported()).isZero();
        assertThat(result.getSkippedDuplicates()).isEqualTo(1);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void importXlsx_ShouldMapContractToFixed_WhenAnalyseVertragIsJa() throws IOException {
        MockMultipartFile file = xlsxFile(new Object[][] {
            // Electricity bill with contract flag → FIXED + UTILITIES
            {
                LocalDate.of(2024, 8, 12),
                null,
                null,
                -75.00,
                null,
                null,
                "MONTANA",
                "Strom Abschlag",
                "Wohnen",
                "Strom",
                "ja",
                "nein",
                "b-1"
            },
            // Gym membership contract → FIXED via contract flag (Freizeit alone would be WANTS)
            {
                LocalDate.of(2024, 8, 13),
                null,
                null,
                -29.99,
                null,
                null,
                "FitX",
                "Mitgliedschaft",
                "Freizeit",
                "Sport",
                "ja",
                "nein",
                "b-2"
            },
            // Restaurant stays WANTS even though category default differs
            {
                LocalDate.of(2024, 8, 14),
                null,
                null,
                -45.00,
                null,
                null,
                "Vapiano",
                "Essen",
                "Essen & Trinken",
                "Restaurants",
                "nein",
                "nein",
                "b-3"
            }
        });

        when(expenseRepository.existsByExternalId(anyString())).thenReturn(false);

        importService.importXlsx(file);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository, times(3)).save(captor.capture());

        assertThat(captor.getAllValues().get(0).getCategory()).isEqualTo(ExpenseCategory.FIXED);
        assertThat(captor.getAllValues().get(0).getTags()).containsExactly(Tag.UTILITIES);
        assertThat(captor.getAllValues().get(1).getCategory()).isEqualTo(ExpenseCategory.FIXED);
        assertThat(captor.getAllValues().get(1).getTags()).containsExactly(Tag.SPORTS_AND_HOBBIES);
        assertThat(captor.getAllValues().get(2).getCategory()).isEqualTo(ExpenseCategory.WANTS);
        assertThat(captor.getAllValues().get(2).getTags()).containsExactly(Tag.BARS_AND_RESTAURANTS);
    }

    @Test
    void importXlsx_ShouldCreateOtherIncome_WhenPositiveAmountIsRefund() throws IOException {
        MockMultipartFile file = xlsxFile(new Object[][] {
            {
                LocalDate.of(2024, 9, 1),
                null,
                null,
                12.50,
                null,
                null,
                "Vapiano",
                "Erstattung",
                "Essen & Trinken",
                "Restaurants",
                "nein",
                "nein",
                "b-1"
            }
        });

        when(incomeRepository.existsByExternalId(anyString())).thenReturn(false);

        importService.importXlsx(file);

        ArgumentCaptor<Income> captor = ArgumentCaptor.forClass(Income.class);
        verify(incomeRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(IncomeCategory.OTHER_INCOME);
    }

    @Test
    void importXlsx_ShouldLinkExpenseToAccount_WhenAccountResolved() throws IOException {
        MockMultipartFile file = xlsxFile(new Object[][] {
            {
                LocalDate.of(2024, 8, 12),
                "DE05380700240067051300",
                "Persoenliches Konto",
                -21.43,
                5659.22,
                "EUR",
                "Kaufland",
                "KAUFLAND KOELN",
                "Essen & Trinken",
                "Lebensmittel",
                "nein",
                "nein",
                "booking-1"
            }
        });

        when(expenseRepository.existsByExternalId(anyString())).thenReturn(false);
        when(accountRepository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        importService.importXlsx(file);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        assertThat(captor.getValue().getAccount()).isNotNull();
        assertThat(captor.getValue().getAccount().getExternalId()).isEqualTo("DE05380700240067051300");
    }

    @Test
    void importXlsx_ShouldNotRollBackBalance_WhenRowIsOlderThanStoredSnapshot() throws IOException {
        MockMultipartFile file = xlsxFile(new Object[][] {
            {
                LocalDate.of(2024, 8, 1),
                "DE05380700240067051300",
                "Persoenliches Konto",
                -10.00,
                4000.00,
                "EUR",
                "Kaufland",
                "KAUFLAND KOELN",
                "Essen & Trinken",
                "Lebensmittel",
                "nein",
                "nein",
                "booking-old"
            }
        });

        Account existing = Account.builder()
                .externalId("DE05380700240067051300")
                .name("Persoenliches Konto")
                .balance(new BigDecimal("5000.00"))
                .currency("EUR")
                .balanceAsOf(LocalDateTime.of(2024, 8, 12, 0, 0))
                .build();
        when(accountRepository.findByExternalId("DE05380700240067051300")).thenReturn(Optional.of(existing));
        when(expenseRepository.existsByExternalId(anyString())).thenReturn(false);

        importService.importXlsx(file);

        verify(accountRepository, never()).save(any());
    }
}
