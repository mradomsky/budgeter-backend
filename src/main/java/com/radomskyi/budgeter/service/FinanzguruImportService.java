package com.radomskyi.budgeter.service;

import com.radomskyi.budgeter.domain.entity.budgeting.Expense;
import com.radomskyi.budgeter.domain.entity.budgeting.ExpenseCategory;
import com.radomskyi.budgeter.domain.entity.budgeting.Income;
import com.radomskyi.budgeter.domain.entity.budgeting.IncomeCategory;
import com.radomskyi.budgeter.domain.entity.budgeting.Tag;
import com.radomskyi.budgeter.dto.ImportResult;
import com.radomskyi.budgeter.repository.ExpenseRepository;
import com.radomskyi.budgeter.repository.IncomeRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Imports the Finanzguru "Alle Buchungen" XLSX export. Negative amounts become expenses, positive
 * amounts become incomes. Skipped rows: transfers between own accounts (Analyse-Umbuchung=ja) and
 * the "Sparen" main category (transfers to brokerages/savings — those are covered by the brokerage
 * imports and would double-count).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinanzguruImportService {

    private static final String COL_DATE = "Buchungstag";
    private static final String COL_AMOUNT = "Betrag";
    private static final String COL_COUNTERPARTY = "Beguenstigter/Auftraggeber";
    private static final String COL_PURPOSE = "Verwendungszweck";
    private static final String COL_MAIN_CATEGORY = "Analyse-Hauptkategorie";
    private static final String COL_SUB_CATEGORY = "Analyse-Unterkategorie";
    private static final String COL_CONTRACT = "Analyse-Vertrag";
    private static final String COL_TRANSFER = "Analyse-Umbuchung";
    private static final String COL_BOOKING_ID = "Buchungs-ID";

    private static final String MAIN_CATEGORY_SAVINGS = "Sparen";
    private static final String MAIN_CATEGORY_INCOME = "Einnahmen";

    // Hauptkategorie → 50/30/20 budget category. Contract rows (Analyse-Vertrag=ja) become FIXED.
    private static final Map<String, ExpenseCategory> CATEGORY_BY_MAIN = Map.ofEntries(
            Map.entry("Essen & Trinken", ExpenseCategory.NEEDS),
            Map.entry("Drogerie", ExpenseCategory.NEEDS),
            Map.entry("Gesundheit", ExpenseCategory.NEEDS),
            Map.entry("Haustiere", ExpenseCategory.NEEDS),
            Map.entry("Kinder", ExpenseCategory.NEEDS),
            Map.entry("Mobilitaet", ExpenseCategory.NEEDS),
            Map.entry("Wohnen", ExpenseCategory.FIXED),
            Map.entry("Versicherungen", ExpenseCategory.FIXED),
            Map.entry("Finanzen", ExpenseCategory.FIXED),
            Map.entry("Freizeit", ExpenseCategory.WANTS),
            Map.entry("Lifestyle", ExpenseCategory.WANTS),
            Map.entry("Sonstiges", ExpenseCategory.WANTS));

    // Unterkategorie overrides for budget category (dining out is a want, not a need)
    private static final Map<String, ExpenseCategory> CATEGORY_BY_SUB = Map.ofEntries(
            Map.entry("Restaurants", ExpenseCategory.WANTS),
            Map.entry("Lieferservice", ExpenseCategory.WANTS),
            Map.entry("Spende", ExpenseCategory.WANTS));

    private static final Map<String, Tag> TAG_BY_SUB = Map.ofEntries(
            Map.entry("Lebensmittel", Tag.FOOD),
            Map.entry("Restaurants", Tag.BARS_AND_RESTAURANTS),
            Map.entry("Lieferservice", Tag.BARS_AND_RESTAURANTS),
            Map.entry("Drogerie", Tag.PERSONAL_CARE),
            Map.entry("Miete", Tag.HOUSING),
            Map.entry("Strom", Tag.UTILITIES),
            Map.entry("Gas", Tag.UTILITIES),
            Map.entry("Internet & Telefon", Tag.UTILITIES),
            Map.entry("Rundfunkgebuehren", Tag.UTILITIES),
            Map.entry("Einrichtung", Tag.HOUSING),
            Map.entry("Bauen / Renovieren", Tag.HOUSING),
            Map.entry("Bankgebuehren", Tag.BANKING_AND_TAXES),
            Map.entry("Kredit", Tag.DEBT),
            Map.entry("Spende", Tag.DONATIONS),
            Map.entry("Sport", Tag.SPORTS_AND_HOBBIES),
            Map.entry("Urlaub", Tag.TRAVEL),
            Map.entry("Musik & Podcasts", Tag.SUBSCRIPTIONS),
            Map.entry("Serien & Filme", Tag.SUBSCRIPTIONS),
            Map.entry("Cloud-Dienste", Tag.SUBSCRIPTIONS),
            Map.entry("Prime-Mitgliedschaft", Tag.SUBSCRIPTIONS),
            Map.entry("Bekleidung", Tag.CLOTHING),
            Map.entry("Bildung", Tag.EDUCATION),
            Map.entry("Elektrohandel", Tag.SHOPPING),
            Map.entry("Geschenke", Tag.GIFTS),
            Map.entry("Bus & Bahn", Tag.TRANSPORT),
            Map.entry("Fahrrad", Tag.TRANSPORT),
            Map.entry("Taxi", Tag.TRANSPORT),
            Map.entry("Sharing / Gemietet", Tag.TRANSPORT),
            Map.entry("Apotheke", Tag.HEALTH),
            Map.entry("Aerztliche Behandlung", Tag.HEALTH),
            Map.entry("Futter & Tierbedarf", Tag.PETS),
            Map.entry("Tieraerztliche Behandlung", Tag.PETS));

    private static final Map<String, Tag> TAG_BY_MAIN = Map.ofEntries(
            Map.entry("Essen & Trinken", Tag.FOOD),
            Map.entry("Drogerie", Tag.PERSONAL_CARE),
            Map.entry("Gesundheit", Tag.HEALTH),
            Map.entry("Haustiere", Tag.PETS),
            Map.entry("Mobilitaet", Tag.TRANSPORT),
            Map.entry("Wohnen", Tag.HOUSING),
            Map.entry("Versicherungen", Tag.INSURANCE),
            Map.entry("Finanzen", Tag.BANKING_AND_TAXES),
            Map.entry("Freizeit", Tag.ENTERTAINMENT),
            Map.entry("Lifestyle", Tag.SHOPPING));

    private static final Map<String, IncomeCategory> INCOME_BY_SUB = Map.ofEntries(
            Map.entry("Lohn / Gehalt", IncomeCategory.SALARY),
            Map.entry("Kindergeld", IncomeCategory.GOVERNMENT_BENEFITS),
            Map.entry("Mieteinnahmen", IncomeCategory.RENTAL));

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;

    @Transactional
    public ImportResult importXlsx(MultipartFile file) throws IOException {
        log.info("Starting Finanzguru XLSX import for file: {}", file.getOriginalFilename());

        ImportResult result = ImportResult.builder().build();
        DataFormatter formatter = new DataFormatter();

        // Finanzguru exports contain a highly compressible styles.xml that trips POI's default
        // zip-bomb detection threshold (ratio < 0.01)
        ZipSecureFile.setMinInflateRatio(0.001);

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("XLSX file is empty");
            }

            Map<String, Integer> columns = mapHeader(headerRow, formatter);

            for (int i = headerRow.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                try {
                    processRow(row, columns, formatter, result);
                } catch (Exception e) {
                    log.error("Error processing Finanzguru row {}: {}", i, e.getMessage());
                    result.incrementFailedRows();
                }
            }
        }

        log.info(
                "Finanzguru import finished: {} imported, {} duplicates, {} skipped, {} failed",
                result.getImported(),
                result.getSkippedDuplicates(),
                result.getSkippedRows(),
                result.getFailedRows());
        return result;
    }

    private Map<String, Integer> mapHeader(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : headerRow) {
            columns.put(formatter.formatCellValue(cell).trim(), cell.getColumnIndex());
        }
        if (!columns.containsKey(COL_AMOUNT) || !columns.containsKey(COL_BOOKING_ID)) {
            throw new IllegalArgumentException("Not a Finanzguru export: missing 'Betrag'/'Buchungs-ID' columns");
        }
        return columns;
    }

    private void processRow(Row row, Map<String, Integer> columns, DataFormatter formatter, ImportResult result) {
        BigDecimal amount = numericValue(row, columns.get(COL_AMOUNT));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            result.incrementSkippedRows();
            return;
        }

        String mainCategory = stringValue(row, columns.get(COL_MAIN_CATEGORY), formatter);
        boolean isTransfer = "ja".equalsIgnoreCase(stringValue(row, columns.get(COL_TRANSFER), formatter));
        if (isTransfer || MAIN_CATEGORY_SAVINGS.equals(mainCategory)) {
            // Transfers between own accounts and to brokerages/savings — not real income/expense
            result.incrementSkippedRows();
            return;
        }

        String externalId = stringValue(row, columns.get(COL_BOOKING_ID), formatter);
        String subCategory = stringValue(row, columns.get(COL_SUB_CATEGORY), formatter);
        String counterparty = stringValue(row, columns.get(COL_COUNTERPARTY), formatter);
        String purpose = stringValue(row, columns.get(COL_PURPOSE), formatter);
        LocalDateTime bookingDate = dateValue(row, columns.get(COL_DATE));
        boolean isContract = "ja".equalsIgnoreCase(stringValue(row, columns.get(COL_CONTRACT), formatter));

        String name =
                truncate(counterparty != null ? counterparty : (subCategory != null ? subCategory : "Unknown"), 50);
        String description = truncate(purpose, 200);

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            if (externalId != null && expenseRepository.existsByExternalId(externalId)) {
                result.incrementSkippedDuplicates();
                return;
            }
            Expense expense = Expense.builder()
                    .amount(amount.abs())
                    .name(name)
                    .description(description)
                    .category(mapExpenseCategory(mainCategory, subCategory, isContract))
                    .tags(List.of(mapTag(mainCategory, subCategory)))
                    .externalId(externalId)
                    .transactionDate(bookingDate)
                    .build();
            expenseRepository.save(expense);
        } else {
            if (externalId != null && incomeRepository.existsByExternalId(externalId)) {
                result.incrementSkippedDuplicates();
                return;
            }
            Income income = Income.builder()
                    .amount(amount)
                    .name(name)
                    .description(description)
                    .category(mapIncomeCategory(mainCategory, subCategory))
                    .externalId(externalId)
                    .transactionDate(bookingDate)
                    .build();
            incomeRepository.save(income);
        }
        result.incrementImported();
    }

    private ExpenseCategory mapExpenseCategory(String mainCategory, String subCategory, boolean isContract) {
        if (subCategory != null && CATEGORY_BY_SUB.containsKey(subCategory)) {
            return CATEGORY_BY_SUB.get(subCategory);
        }
        if (isContract) {
            return ExpenseCategory.FIXED;
        }
        if (mainCategory != null && CATEGORY_BY_MAIN.containsKey(mainCategory)) {
            return CATEGORY_BY_MAIN.get(mainCategory);
        }
        return ExpenseCategory.WANTS;
    }

    private Tag mapTag(String mainCategory, String subCategory) {
        if (subCategory != null && TAG_BY_SUB.containsKey(subCategory)) {
            return TAG_BY_SUB.get(subCategory);
        }
        if (mainCategory != null && TAG_BY_MAIN.containsKey(mainCategory)) {
            return TAG_BY_MAIN.get(mainCategory);
        }
        return Tag.OTHER;
    }

    private IncomeCategory mapIncomeCategory(String mainCategory, String subCategory) {
        if (MAIN_CATEGORY_INCOME.equals(mainCategory) && subCategory != null) {
            return INCOME_BY_SUB.getOrDefault(subCategory, IncomeCategory.OTHER_INCOME);
        }
        // Positive amounts outside "Einnahmen" are refunds/reimbursements
        return IncomeCategory.OTHER_INCOME;
    }

    private String stringValue(Row row, Integer columnIndex, DataFormatter formatter) {
        if (columnIndex == null) {
            return null;
        }
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private BigDecimal numericValue(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return null;
        }
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        if (cell.getCellType() == CellType.STRING) {
            try {
                return new BigDecimal(cell.getStringCellValue().trim().replace(",", "."));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime dateValue(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return null;
        }
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
