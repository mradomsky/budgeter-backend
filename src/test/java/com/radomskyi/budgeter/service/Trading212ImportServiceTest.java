package com.radomskyi.budgeter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.opencsv.exceptions.CsvException;
import com.radomskyi.budgeter.domain.entity.investment.Currency;
import com.radomskyi.budgeter.domain.entity.investment.InvestmentTransaction;
import com.radomskyi.budgeter.domain.entity.investment.InvestmentTransactionType;
import com.radomskyi.budgeter.dto.ImportResult;
import com.radomskyi.budgeter.dto.InvestmentTransactionRequest;
import com.radomskyi.budgeter.repository.InvestmentTransactionRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class Trading212ImportServiceTest {

    // Header layout of current Trading212 exports — includes the "Notes" column that shifted
    // all following columns compared to older exports
    private static final String NEW_FORMAT_HEADER =
            "Action,Time,ISIN,Ticker,Name,Notes,ID,No. of shares,Price / share,Currency (Price / share),"
                    + "Exchange rate,Result,Currency (Result),Gross Total,Currency (Gross Total),Withholding tax,"
                    + "Currency (Withholding tax),Currency conversion fee,Currency (Currency conversion fee)";

    private static final String OLD_FORMAT_HEADER =
            "Action,Time,ISIN,Ticker,Name,ID,No. of shares,Price / share,Currency (Price / share),"
                    + "Exchange rate,Result,Currency (Result),Gross Total,Currency (Gross Total),Withholding tax,"
                    + "Currency (Withholding tax),Currency conversion fee,Currency (Currency conversion fee)";

    @Mock
    private InvestmentService investmentService;

    @Mock
    private InvestmentTransactionRepository investmentTransactionRepository;

    @InjectMocks
    private Trading212ImportService importService;

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "test.csv", "text/csv", content.getBytes());
    }

    @Test
    void importCsv_ShouldImportBuyAndSell_WhenFileUsesNewFormat() throws IOException, CsvException {
        String csv = NEW_FORMAT_HEADER + "\n"
                + "Market buy,2025-06-10 07:04:05.631,US0378331005,AAPL,Apple Inc.,,EOF1,10.0,150.25,EUR,1.0,,EUR,1502.50,EUR,,,,\n"
                + "Market sell,2025-06-11 11:41:39.98,US0378331005,AAPL,Apple Inc.,,EOF2,5.0,155.00,EUR,1.0,,EUR,775.00,EUR,,,,";

        when(investmentTransactionRepository.existsByExternalId(anyString())).thenReturn(false);
        when(investmentService.create(any()))
                .thenReturn(InvestmentTransaction.builder().id(1L).build());

        ImportResult result = importService.importCsv(csvFile(csv));

        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getFailedRows()).isZero();

        ArgumentCaptor<InvestmentTransactionRequest> captor =
                ArgumentCaptor.forClass(InvestmentTransactionRequest.class);
        verify(investmentService, times(2)).create(captor.capture());

        InvestmentTransactionRequest buy = captor.getAllValues().get(0);
        assertThat(buy.getTransactionType()).isEqualTo(InvestmentTransactionType.BUY);
        assertThat(buy.getAssetTicker()).isEqualTo("AAPL");
        assertThat(buy.getUnits()).isEqualByComparingTo(new BigDecimal("10.0"));
        assertThat(buy.getPricePerUnit()).isEqualByComparingTo(new BigDecimal("150.25"));
        assertThat(buy.getCurrency()).isEqualTo(Currency.EUR);
        assertThat(buy.getExchangeRate()).isNull();
        assertThat(buy.getExternalId()).isEqualTo("EOF1");
        assertThat(buy.getTransactionDate()).isEqualTo(LocalDateTime.of(2025, 6, 10, 7, 4, 5, 631_000_000));

        InvestmentTransactionRequest sell = captor.getAllValues().get(1);
        assertThat(sell.getTransactionType()).isEqualTo(InvestmentTransactionType.SELL);
        assertThat(sell.getExternalId()).isEqualTo("EOF2");
    }

    @Test
    void importCsv_ShouldImportBuyAndSell_WhenFileUsesOldFormat() throws IOException, CsvException {
        String csv = OLD_FORMAT_HEADER + "\n"
                + "Market buy,2025-06-10 07:04:05.631,US0378331005,AAPL,Apple Inc.,EOF1,10.0,150.25,EUR,1.0,,EUR,1502.50,EUR,,,,";

        when(investmentTransactionRepository.existsByExternalId(anyString())).thenReturn(false);
        when(investmentService.create(any()))
                .thenReturn(InvestmentTransaction.builder().id(1L).build());

        ImportResult result = importService.importCsv(csvFile(csv));

        assertThat(result.getImported()).isEqualTo(1);

        ArgumentCaptor<InvestmentTransactionRequest> captor =
                ArgumentCaptor.forClass(InvestmentTransactionRequest.class);
        verify(investmentService).create(captor.capture());
        assertThat(captor.getValue().getExternalId()).isEqualTo("EOF1");
        assertThat(captor.getValue().getUnits()).isEqualByComparingTo(new BigDecimal("10.0"));
    }

    @Test
    void importCsv_ShouldSkipCashRows_WhenFileContainsNonInvestmentActions() throws IOException, CsvException {
        String csv = NEW_FORMAT_HEADER + "\n"
                + "Interest on cash,2025-07-01 00:15:08,,,,Interest on cash,ID1,,,,,,,0.10,GBP,,,,\n"
                + "Withdrawal,2025-07-01 15:25:01,,,,Sent to Bank,ID2,,,,,,,-800.00,GBP,,,,\n"
                + "Deposit,2025-07-02 10:00:00,,,,Bank Transfer,ID3,,,,,,,500.00,EUR,,,,\n"
                + "Currency conversion,2025-07-03 10:00:00,,,,,ID4,,,,,,,100.00,EUR,,,,";

        ImportResult result = importService.importCsv(csvFile(csv));

        assertThat(result.getImported()).isZero();
        assertThat(result.getSkippedRows()).isEqualTo(4);
        verify(investmentService, never()).create(any());
    }

    @Test
    void importCsv_ShouldSkipDuplicates_WhenExternalIdAlreadyImported() throws IOException, CsvException {
        String csv = NEW_FORMAT_HEADER + "\n"
                + "Market buy,2025-06-10 07:04:05.631,US0378331005,AAPL,Apple Inc.,,EOF1,10.0,150.25,EUR,1.0,,EUR,1502.50,EUR,,,,";

        when(investmentTransactionRepository.existsByExternalId("EOF1")).thenReturn(true);

        ImportResult result = importService.importCsv(csvFile(csv));

        assertThat(result.getImported()).isZero();
        assertThat(result.getSkippedDuplicates()).isEqualTo(1);
        verify(investmentService, never()).create(any());
    }

    @Test
    void importCsv_ShouldDeriveEurExchangeRate_WhenPriceCurrencyIsNotEur() throws IOException, CsvException {
        // 10 shares × 100 USD = 1000 USD bought for 850 EUR → 0.85 EUR per USD
        String csv = NEW_FORMAT_HEADER + "\n"
                + "Market buy,2025-06-10 07:04:05,US0378331005,AAPL,Apple Inc.,,EOF1,10.0,100.0,USD,1.17647059,,EUR,850.00,EUR,,,,";

        when(investmentTransactionRepository.existsByExternalId(anyString())).thenReturn(false);
        when(investmentService.create(any()))
                .thenReturn(InvestmentTransaction.builder().id(1L).build());

        importService.importCsv(csvFile(csv));

        ArgumentCaptor<InvestmentTransactionRequest> captor =
                ArgumentCaptor.forClass(InvestmentTransactionRequest.class);
        verify(investmentService).create(captor.capture());

        InvestmentTransactionRequest request = captor.getValue();
        assertThat(request.getCurrency()).isEqualTo(Currency.USD);
        assertThat(request.getExchangeRate()).isEqualByComparingTo(new BigDecimal("0.85"));
    }

    @Test
    void importCsv_ShouldUseSyntheticExternalId_WhenDividendRowHasNoId() throws IOException, CsvException {
        String csv = NEW_FORMAT_HEADER + "\n"
                + "Dividend (Dividend),2025-06-11 11:42:39,US0378331005,AAPL,Apple Inc.,,,0.0253888,0.816,USD,0.868951,,,0.02,EUR,0.00,USD,,";

        when(investmentTransactionRepository.existsByExternalId(anyString())).thenReturn(false);
        when(investmentService.create(any()))
                .thenReturn(InvestmentTransaction.builder().id(1L).build());

        ImportResult result = importService.importCsv(csvFile(csv));

        assertThat(result.getImported()).isEqualTo(1);

        ArgumentCaptor<InvestmentTransactionRequest> captor =
                ArgumentCaptor.forClass(InvestmentTransactionRequest.class);
        verify(investmentService).create(captor.capture());

        InvestmentTransactionRequest request = captor.getValue();
        assertThat(request.getTransactionType()).isEqualTo(InvestmentTransactionType.DIVIDEND);
        assertThat(request.getExternalId()).isEqualTo("t212|2025-06-11 11:42:39|US0378331005|Dividend (Dividend)|0.02");
    }

    @Test
    void importCsv_ShouldThrowException_WhenFileIsEmpty() {
        assertThatThrownBy(() -> importService.importCsv(csvFile("")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV file is empty");
    }

    @Test
    void importCsv_ShouldThrowException_WhenHeaderIsNotTrading212() {
        String csv = "datetime,category,type\n2025-01-01,CASH,BUY";

        assertThatThrownBy(() -> importService.importCsv(csvFile(csv)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a Trading212 export");
    }

    @Test
    void importCsv_ShouldCountFailedRows_WhenRowIsMalformed() throws IOException, CsvException {
        String csv = NEW_FORMAT_HEADER + "\n"
                + "Market buy,2025-06-10 07:04:05,US0378331005,AAPL,Apple Inc.,,EOF1,,,EUR,1.0,,EUR,1502.50,EUR,,,,\n"
                + "Market buy,2025-06-10 08:04:05,US0378331005,AAPL,Apple Inc.,,EOF2,10.0,150.25,EUR,1.0,,EUR,1502.50,EUR,,,,";

        when(investmentTransactionRepository.existsByExternalId(anyString())).thenReturn(false);
        when(investmentService.create(any()))
                .thenReturn(InvestmentTransaction.builder().id(1L).build());

        ImportResult result = importService.importCsv(csvFile(csv));

        assertThat(result.getFailedRows()).isEqualTo(1); // row without units
        assertThat(result.getImported()).isEqualTo(1);
    }
}
