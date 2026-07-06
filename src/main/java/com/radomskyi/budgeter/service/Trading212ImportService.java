package com.radomskyi.budgeter.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.radomskyi.budgeter.domain.entity.investment.Currency;
import com.radomskyi.budgeter.domain.entity.investment.InvestmentTransactionType;
import com.radomskyi.budgeter.dto.ImportResult;
import com.radomskyi.budgeter.dto.InvestmentTransactionRequest;
import com.radomskyi.budgeter.repository.InvestmentTransactionRepository;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Imports the Trading212 transaction history CSV export. Columns are resolved by header name, so
 * both older and newer export layouts are supported. Only buy/sell/dividend rows become investment
 * transactions; cash movements (deposits, withdrawals, interest, currency conversions) are skipped.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Trading212ImportService {

    public static final String BROKERAGE = "Trading212";

    // Header names as they appear in Trading212 exports
    private static final String COL_ACTION = "Action";
    private static final String COL_TIME = "Time";
    private static final String COL_ISIN = "ISIN";
    private static final String COL_TICKER = "Ticker";
    private static final String COL_NAME = "Name";
    private static final String COL_ID = "ID";
    private static final String COL_UNITS = "No. of shares";
    private static final String COL_PRICE = "Price / share";
    private static final String COL_PRICE_CURRENCY = "Currency (Price / share)";
    private static final String COL_GROSS_TOTAL = "Gross Total";
    private static final String COL_GROSS_TOTAL_CURRENCY = "Currency (Gross Total)";
    private static final String COL_WITHHOLDING_TAX = "Withholding tax";
    private static final String COL_CONVERSION_FEE = "Currency conversion fee";

    // Seconds fraction varies between rows (none, .98, .631), so accept any precision
    private static final DateTimeFormatter TIME_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter();

    private final InvestmentService investmentService;
    private final InvestmentTransactionRepository investmentTransactionRepository;

    // Intentionally not @Transactional: each row commits via InvestmentService.create's own
    // transaction, so one bad row cannot poison the session and fail the whole import.
    public ImportResult importCsv(MultipartFile file) throws IOException, CsvException {
        log.info("Starting Trading212 CSV import for file: {}", file.getOriginalFilename());

        ImportResult result = ImportResult.builder().build();

        try (CSVReader csvReader =
                new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = csvReader.readAll();

            if (rows.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            Map<String, Integer> columns = mapHeader(rows.get(0));

            for (int i = 1; i < rows.size(); i++) {
                try {
                    processRow(rows.get(i), columns, result);
                } catch (Exception e) {
                    log.error("Error processing Trading212 row {}: {}", i, e.getMessage());
                    result.incrementFailedRows();
                }
            }
        }

        log.info(
                "Trading212 import finished: {} imported, {} duplicates, {} skipped, {} failed",
                result.getImported(),
                result.getSkippedDuplicates(),
                result.getSkippedRows(),
                result.getFailedRows());
        return result;
    }

    private Map<String, Integer> mapHeader(String[] header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            columns.put(header[i].trim(), i);
        }
        if (!columns.containsKey(COL_ACTION) || !columns.containsKey(COL_TICKER)) {
            throw new IllegalArgumentException("Not a Trading212 export: missing 'Action'/'Ticker' columns");
        }
        return columns;
    }

    private void processRow(String[] row, Map<String, Integer> columns, ImportResult result) {
        String action = value(row, columns, COL_ACTION);
        InvestmentTransactionType type = determineTransactionType(action);
        if (type == null) {
            // Deposits, withdrawals, interest, currency conversions, etc.
            result.incrementSkippedRows();
            return;
        }

        String externalId = buildExternalId(row, columns);
        if (investmentTransactionRepository.existsByExternalId(externalId)) {
            result.incrementSkippedDuplicates();
            return;
        }

        String ticker = value(row, columns, COL_TICKER);
        String name = value(row, columns, COL_NAME);
        String isin = value(row, columns, COL_ISIN);
        BigDecimal units = parseBigDecimal(value(row, columns, COL_UNITS));
        BigDecimal pricePerUnit = parseBigDecimal(value(row, columns, COL_PRICE));
        BigDecimal grossTotal = parseBigDecimal(value(row, columns, COL_GROSS_TOTAL));
        BigDecimal withholdingTax = parseBigDecimal(value(row, columns, COL_WITHHOLDING_TAX));
        BigDecimal conversionFee = parseBigDecimal(value(row, columns, COL_CONVERSION_FEE));

        if (units == null || units.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid units are required for action: " + action);
        }
        if (pricePerUnit == null || pricePerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid price per unit is required for action: " + action);
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Asset name is required for action: " + action);
        }

        Currency priceCurrency = parseCurrency(value(row, columns, COL_PRICE_CURRENCY));
        Currency grossTotalCurrency = parseCurrency(value(row, columns, COL_GROSS_TOTAL_CURRENCY));

        BigDecimal totalFees = (withholdingTax != null ? withholdingTax : BigDecimal.ZERO)
                .add(conversionFee != null ? conversionFee : BigDecimal.ZERO);

        InvestmentTransactionRequest request = InvestmentTransactionRequest.builder()
                .transactionType(type)
                .assetTicker(ticker)
                .assetName(name)
                .assetIsin(isin)
                .units(units)
                .pricePerUnit(pricePerUnit)
                .fees(totalFees.compareTo(BigDecimal.ZERO) > 0 ? totalFees : null)
                .currency(priceCurrency)
                .exchangeRate(deriveEurExchangeRate(
                        priceCurrency, grossTotalCurrency, units, pricePerUnit, grossTotal, conversionFee))
                .name(truncate(name + " " + ticker, 50))
                .description(truncate("Imported from Trading212 CSV: " + action, 200))
                .brokerage(BROKERAGE)
                .externalId(externalId)
                .transactionDate(parseTime(value(row, columns, COL_TIME)))
                .build();

        investmentService.create(request);
        result.incrementImported();
    }

    /**
     * Returns the EUR-per-instrument-currency rate, derived from the EUR gross total. The export's
     * own "Exchange rate" column is not used because its direction is inconsistent between buys
     * (instrument-per-EUR) and dividends (EUR-per-instrument).
     */
    private BigDecimal deriveEurExchangeRate(
            Currency priceCurrency,
            Currency grossTotalCurrency,
            BigDecimal units,
            BigDecimal pricePerUnit,
            BigDecimal grossTotal,
            BigDecimal conversionFee) {
        if (priceCurrency == Currency.EUR) {
            return null;
        }
        if (grossTotalCurrency != Currency.EUR || grossTotal == null) {
            log.warn("Cannot derive EUR exchange rate: gross total not in EUR");
            return null;
        }
        // Gross total includes the currency conversion fee on buys
        BigDecimal eurValue = conversionFee != null ? grossTotal.subtract(conversionFee) : grossTotal;
        BigDecimal instrumentValue = units.multiply(pricePerUnit);
        if (instrumentValue.compareTo(BigDecimal.ZERO) == 0 || eurValue.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return eurValue.divide(instrumentValue, 8, RoundingMode.HALF_UP);
    }

    /** Dividend rows have no ID in the export, so fall back to a deterministic synthetic key. */
    private String buildExternalId(String[] row, Map<String, Integer> columns) {
        String id = value(row, columns, COL_ID);
        if (id != null && !id.isEmpty()) {
            return id;
        }
        return truncate(
                String.join(
                        "|",
                        "t212",
                        value(row, columns, COL_TIME),
                        value(row, columns, COL_ISIN),
                        value(row, columns, COL_ACTION),
                        value(row, columns, COL_GROSS_TOTAL)),
                100);
    }

    private InvestmentTransactionType determineTransactionType(String action) {
        if (action == null) {
            return null;
        }
        String normalized = action.toLowerCase();
        if (normalized.contains("buy")) {
            return InvestmentTransactionType.BUY;
        }
        if (normalized.contains("sell")) {
            return InvestmentTransactionType.SELL;
        }
        if (normalized.contains("dividend")) {
            return InvestmentTransactionType.DIVIDEND;
        }
        return null;
    }

    private Currency parseCurrency(String currencyStr) {
        if (currencyStr == null || currencyStr.isEmpty()) {
            return Currency.EUR;
        }
        try {
            return Currency.valueOf(currencyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported currency: " + currencyStr);
        }
    }

    private LocalDateTime parseTime(String time) {
        if (time == null || time.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(time, TIME_FORMAT);
        } catch (Exception e) {
            log.warn("Could not parse Trading212 time: {}", time);
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Could not parse BigDecimal from value: {}", value);
            return null;
        }
    }

    private String value(String[] row, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        if (index == null || index >= row.length) {
            return null;
        }
        String value = row[index].trim();
        return value.isEmpty() ? null : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
