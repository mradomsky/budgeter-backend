package com.radomskyi.budgeter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.radomskyi.budgeter.domain.entity.budgeting.Expense;
import com.radomskyi.budgeter.domain.entity.budgeting.ExpenseCategory;
import com.radomskyi.budgeter.domain.entity.budgeting.Income;
import com.radomskyi.budgeter.domain.entity.budgeting.IncomeCategory;
import com.radomskyi.budgeter.domain.entity.budgeting.Tag;
import com.radomskyi.budgeter.dto.ImportResult;
import com.radomskyi.budgeter.repository.ExpenseRepository;
import com.radomskyi.budgeter.repository.IncomeRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        "Betrag",
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

    @InjectMocks
    private FinanzguruImportService importService;

    /** Builds an in-memory Finanzguru-style XLSX. Each row: date, amount, counterparty, purpose,
     * main category, sub category, contract, transfer, booking id. */
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
                -21.43,
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
    }

    @Test
    void importXlsx_ShouldCreateSalaryIncome_WhenAmountIsPositive() throws IOException {
        MockMultipartFile file = xlsxFile(new Object[][] {
            {
                LocalDate.of(2024, 8, 28),
                3200.00,
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
                -500.00,
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
                -300.00,
                "Trading 212",
                "Einzahlung",
                "Sparen",
                "Kapitalanlage",
                "nein",
                "nein",
                "b-2"
            },
            // Zero amount
            {LocalDate.of(2024, 8, 3), 0.00, "Test", "Nichts", "Sonstiges", "Sonstige Ausgaben", "nein", "nein", "b-3"}
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
                -21.43,
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
            {LocalDate.of(2024, 8, 12), -75.00, "MONTANA", "Strom Abschlag", "Wohnen", "Strom", "ja", "nein", "b-1"},
            // Gym membership contract → FIXED via contract flag (Freizeit alone would be WANTS)
            {LocalDate.of(2024, 8, 13), -29.99, "FitX", "Mitgliedschaft", "Freizeit", "Sport", "ja", "nein", "b-2"},
            // Restaurant stays WANTS even though category default differs
            {
                LocalDate.of(2024, 8, 14),
                -45.00,
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
                12.50,
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
}
