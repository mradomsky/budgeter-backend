package com.radomskyi.budgeter.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencsv.exceptions.CsvException;
import com.radomskyi.budgeter.domain.entity.investment.*;
import com.radomskyi.budgeter.dto.ImportResult;
import com.radomskyi.budgeter.repository.AssetRepository;
import com.radomskyi.budgeter.repository.InvestmentRepository;
import com.radomskyi.budgeter.repository.InvestmentTransactionRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Trading212ImportIntegrationTest {

    private static final String HEADER =
            "Action,Time,ISIN,Ticker,Name,Notes,ID,No. of shares,Price / share,Currency (Price / share),"
                    + "Exchange rate,Result,Currency (Result),Gross Total,Currency (Gross Total),Withholding tax,"
                    + "Currency (Withholding tax),Currency conversion fee,Currency (Currency conversion fee)";

    @Autowired
    private Trading212ImportService importService;

    @Autowired
    private InvestmentTransactionRepository investmentTransactionRepository;

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private AssetRepository assetRepository;

    @BeforeEach
    void setUp() {
        investmentTransactionRepository.deleteAll();
        investmentRepository.deleteAll();
        assetRepository.deleteAll();
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "trading212.csv", "text/csv", content.getBytes());
    }

    @Test
    void importCsv_ShouldPersistTransactionsAndSkipCashRows_WhenImportingRealisticExport()
            throws IOException, CsvException {
        String csv = HEADER + "\n"
                + "Interest on cash,2025-07-01 00:15:08,,,,Interest on cash,IC1,,,,,,,0.10,GBP,,,,\n"
                + "Market buy,2025-06-10 07:04:05.631,US0378331005,AAPL,Apple Inc.,,EOF1,10.0,150.25,EUR,1.0,,EUR,1502.50,EUR,,,,\n"
                + "Market sell,2025-06-11 11:41:39.98,US0378331005,AAPL,Apple Inc.,,EOF2,5.0,155.00,EUR,1.0,,EUR,775.00,EUR,,,,\n"
                + "Deposit,2025-07-02 10:00:00,,,,Bank Transfer,DEP1,,,,,,,500.00,EUR,,,,";

        ImportResult result = importService.importCsv(csvFile(csv));

        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getSkippedRows()).isEqualTo(2);
        assertThat(result.getFailedRows()).isZero();

        List<InvestmentTransaction> transactions = investmentTransactionRepository.findAll();
        assertThat(transactions).hasSize(2);

        List<Asset> assets = assetRepository.findAll();
        assertThat(assets).hasSize(1);
        assertThat(assets.get(0).getTicker()).isEqualTo("AAPL");
        assertThat(assets.get(0).getIsin()).isEqualTo("US0378331005");

        // One investment per asset, with position metrics updated by both transactions
        List<Investment> investments = investmentRepository.findAll();
        assertThat(investments).hasSize(1);
        assertThat(investments.get(0).getTotalUnits()).isEqualByComparingTo(new BigDecimal("5.0"));
        assertThat(investments.get(0).getLatestPrice()).isEqualByComparingTo(new BigDecimal("155.00"));
        assertThat(investments.get(0).getBrokerage()).isEqualTo("Trading212");
    }

    @Test
    void importCsv_ShouldSkipAllRows_WhenSameFileIsImportedTwice() throws IOException, CsvException {
        String csv = HEADER + "\n"
                + "Market buy,2025-06-10 07:04:05.631,US0378331005,AAPL,Apple Inc.,,EOF1,10.0,150.25,EUR,1.0,,EUR,1502.50,EUR,,,,";

        ImportResult firstImport = importService.importCsv(csvFile(csv));
        ImportResult secondImport = importService.importCsv(csvFile(csv));

        assertThat(firstImport.getImported()).isEqualTo(1);
        assertThat(secondImport.getImported()).isZero();
        assertThat(secondImport.getSkippedDuplicates()).isEqualTo(1);

        assertThat(investmentTransactionRepository.findAll()).hasSize(1);
    }

    @Test
    void importCsv_ShouldStoreDerivedExchangeRate_WhenInstrumentIsPricedInUsd() throws IOException, CsvException {
        // 10 × 100 USD bought for 850 EUR → derived rate 0.85 EUR/USD
        String csv = HEADER + "\n"
                + "Market buy,2025-06-10 07:04:05,US0378331005,AAPL,Apple Inc.,,EOF1,10.0,100.0,USD,1.17647059,,EUR,850.00,EUR,,,,";

        importService.importCsv(csvFile(csv));

        List<InvestmentTransaction> transactions = investmentTransactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getCurrency()).isEqualTo(Currency.USD);
        assertThat(transactions.get(0).getExchangeRate()).isEqualByComparingTo(new BigDecimal("0.85"));
        // EUR amount: 10 × 100 × 0.85 = 850
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("850.00"));

        // Net worth inputs on the investment record
        Investment investment = investmentRepository.findAll().get(0);
        assertThat(investment.getLatestPrice()).isEqualByComparingTo(new BigDecimal("100.0"));
        assertThat(investment.getLatestExchangeRate()).isEqualByComparingTo(new BigDecimal("0.85"));
        assertThat(investment.getCurrentValueEur()).isEqualByComparingTo(new BigDecimal("850.00"));
    }
}
