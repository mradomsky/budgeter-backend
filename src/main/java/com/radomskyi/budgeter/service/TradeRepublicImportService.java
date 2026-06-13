package com.radomskyi.budgeter.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.radomskyi.budgeter.domain.entity.investment.AssetType;
import com.radomskyi.budgeter.domain.entity.investment.Currency;
import com.radomskyi.budgeter.domain.entity.investment.InvestmentTransactionType;
import com.radomskyi.budgeter.dto.ImportResult;
import com.radomskyi.budgeter.dto.InvestmentTransactionRequest;
import com.radomskyi.budgeter.repository.InvestmentTransactionRepository;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Imports the Trade Republic transaction CSV export (Transaktionsexport.csv). Only
 * category=TRADING rows (buys and sells of stocks, funds, crypto, derivatives) become investment
 * transactions; cash movements, dividends paid to cash, and corporate actions are skipped. Prices
 * and amounts in the export are already in EUR.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeRepublicImportService {

    public static final String BROKERAGE = "TradeRepublic";

    private static final String COL_DATETIME = "datetime";
    private static final String COL_CATEGORY = "category";
    private static final String COL_TYPE = "type";
    private static final String COL_ASSET_CLASS = "asset_class";
    private static final String COL_NAME = "name";
    private static final String COL_SYMBOL = "symbol";
    private static final String COL_SHARES = "shares";
    private static final String COL_PRICE = "price";
    private static final String COL_FEE = "fee";
    private static final String COL_TAX = "tax";
    private static final String COL_CURRENCY = "currency";
    private static final String COL_TRANSACTION_ID = "transaction_id";

    private static final String CATEGORY_TRADING = "TRADING";

    private static final Pattern ISIN_PATTERN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    private final InvestmentService investmentService;
    private final InvestmentTransactionRepository investmentTransactionRepository;

    @Transactional
    public ImportResult importCsv(MultipartFile file) throws IOException, CsvException {
        log.info("Starting Trade Republic CSV import for file: {}", file.getOriginalFilename());

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
                    log.error("Error processing Trade Republic row {}: {}", i, e.getMessage());
                    result.incrementFailedRows();
                }
            }
        }

        log.info(
                "Trade Republic import finished: {} imported, {} duplicates, {} skipped, {} failed",
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
        if (!columns.containsKey(COL_CATEGORY) || !columns.containsKey(COL_TRANSACTION_ID)) {
            throw new IllegalArgumentException(
                    "Not a Trade Republic export: missing 'category'/'transaction_id' columns");
        }
        return columns;
    }

    private void processRow(String[] row, Map<String, Integer> columns, ImportResult result) {
        if (!CATEGORY_TRADING.equals(value(row, columns, COL_CATEGORY))) {
            result.incrementSkippedRows();
            return;
        }

        String type = value(row, columns, COL_TYPE);
        InvestmentTransactionType transactionType;
        if ("BUY".equals(type)) {
            transactionType = InvestmentTransactionType.BUY;
        } else if ("SELL".equals(type)) {
            transactionType = InvestmentTransactionType.SELL;
        } else {
            result.incrementSkippedRows();
            return;
        }

        String externalId = value(row, columns, COL_TRANSACTION_ID);
        if (externalId != null && investmentTransactionRepository.existsByExternalId(externalId)) {
            result.incrementSkippedDuplicates();
            return;
        }

        String name = value(row, columns, COL_NAME);
        String symbol = value(row, columns, COL_SYMBOL);
        BigDecimal units = abs(parseBigDecimal(value(row, columns, COL_SHARES)));
        BigDecimal pricePerUnit = parseBigDecimal(value(row, columns, COL_PRICE));
        BigDecimal fee = abs(parseBigDecimal(value(row, columns, COL_FEE)));
        BigDecimal tax = abs(parseBigDecimal(value(row, columns, COL_TAX)));

        if (units == null || units.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid units are required for trade: " + name);
        }
        if (pricePerUnit == null || pricePerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid price per unit is required for trade: " + name);
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Asset name is required");
        }

        // Securities are identified by ISIN in the symbol column; crypto by ticker (BTC, ETH, ...)
        boolean symbolIsIsin = symbol != null && ISIN_PATTERN.matcher(symbol).matches();

        BigDecimal totalFees = (fee != null ? fee : BigDecimal.ZERO).add(tax != null ? tax : BigDecimal.ZERO);

        InvestmentTransactionRequest request = InvestmentTransactionRequest.builder()
                .transactionType(transactionType)
                .assetTicker(symbolIsIsin ? null : truncate(symbol, 10))
                .assetName(truncate(name, 100))
                .assetIsin(symbolIsIsin ? symbol : null)
                .units(units)
                .pricePerUnit(pricePerUnit)
                .fees(totalFees.compareTo(BigDecimal.ZERO) > 0 ? totalFees : null)
                .currency(parseCurrency(value(row, columns, COL_CURRENCY)))
                .exchangeRate(null) // prices in the export are already EUR
                .name(truncate(name, 50))
                .description(truncate("Imported from Trade Republic CSV: " + type, 200))
                .brokerage(BROKERAGE)
                .assetType(mapAssetType(value(row, columns, COL_ASSET_CLASS)))
                .externalId(externalId)
                .transactionDate(parseDateTime(value(row, columns, COL_DATETIME)))
                .build();

        investmentService.create(request);
        result.incrementImported();
    }

    private AssetType mapAssetType(String assetClass) {
        if (assetClass == null) {
            return AssetType.STOCK;
        }
        return switch (assetClass) {
            case "STOCK" -> AssetType.STOCK;
            case "FUND" -> AssetType.INDEX_ETF;
            case "CRYPTO" -> AssetType.CRYPTO;
            case "DERIVATIVE" -> AssetType.DERIVATIVE;
            default -> AssetType.STOCK;
        };
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

    private LocalDateTime parseDateTime(String dateTime) {
        if (dateTime == null || dateTime.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(dateTime).toLocalDateTime();
        } catch (Exception e) {
            log.warn("Could not parse Trade Republic datetime: {}", dateTime);
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Could not parse BigDecimal from value: {}", value);
            return null;
        }
    }

    private BigDecimal abs(BigDecimal value) {
        return value == null ? null : value.abs();
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
