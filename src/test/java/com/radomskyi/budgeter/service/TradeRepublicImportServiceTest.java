package com.radomskyi.budgeter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.opencsv.exceptions.CsvException;
import com.radomskyi.budgeter.domain.entity.investment.AssetType;
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
class TradeRepublicImportServiceTest {

    private static final String HEADER =
            "datetime,date,account_type,category,type,asset_class,name,symbol,shares,price,amount,fee,tax,currency,"
                    + "original_amount,original_currency,fx_rate,description,transaction_id,counterparty_name,"
                    + "counterparty_iban,payment_reference,mcc_code";

    @Mock
    private InvestmentService investmentService;

    @Mock
    private InvestmentTransactionRepository investmentTransactionRepository;

    @InjectMocks
    private TradeRepublicImportService importService;

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "Transaktionsexport.csv", "text/csv", content.getBytes());
    }

    @Test
    void importCsv_ShouldImportStockBuy_WhenRowIsTradingCategory() throws IOException, CsvException {
        String csv = HEADER + "\n"
                + "2025-03-07T20:12:59.302Z,2025-03-07,DEFAULT,TRADING,BUY,STOCK,Rheinmetall,DE0007030009,"
                + "0.0900090000,1111.000000,-100.00,-1.00,,EUR,,,,Ausführung Kauf/Verkauf,tx-1,,,,";

        when(investmentTransactionRepository.existsByExternalId(anyString())).thenReturn(false);
        when(investmentService.create(any()))
                .thenReturn(InvestmentTransaction.builder().id(1L).build());

        ImportResult result = importService.importCsv(csvFile(csv));

        assertThat(result.getImported()).isEqualTo(1);

        ArgumentCaptor<InvestmentTransactionRequest> captor =
                ArgumentCaptor.forClass(InvestmentTransactionRequest.class);
        verify(investmentService).create(captor.capture());

        InvestmentTransactionRequest request = captor.getValue();
        assertThat(request.getTransactionType()).isEqualTo(InvestmentTransactionType.BUY);
        assertThat(request.getAssetName()).isEqualTo("Rheinmetall");
        assertThat(request.getAssetIsin()).isEqualTo("DE0007030009"); // ISIN recognized in symbol column
        assertThat(request.getAssetTicker()).isNull();
        assertThat(request.getAssetType()).isEqualTo(AssetType.STOCK);
        assertThat(request.getUnits()).isEqualByComparingTo(new BigDecimal("0.0900090000"));
        assertThat(request.getPricePerUnit()).isEqualByComparingTo(new BigDecimal("1111.000000"));
        assertThat(request.getFees()).isEqualByComparingTo(new BigDecimal("1.00")); // absolute value
        assertThat(request.getCurrency()).isEqualTo(Currency.EUR);
        assertThat(request.getExchangeRate()).isNull();
        assertThat(request.getExternalId()).isEqualTo("tx-1");
        assertThat(request.getBrokerage()).isEqualTo("TradeRepublic");
        assertThat(request.getTransactionDate()).isEqualTo(LocalDateTime.of(2025, 3, 7, 20, 12, 59, 302_000_000));
    }

    @Test
    void importCsv_ShouldImportCryptoBuy_WhenAssetClassIsCrypto() throws IOException, CsvException {
        String csv = HEADER + "\n"
                + "2021-06-09T22:19:02.159Z,2021-06-10,DEFAULT,TRADING,BUY,CRYPTO,Bitcoin,BTC,"
                + "0.0015000000,30581.840000,-45.87,-1.00,,EUR,,,,Ausführung Kauf/Verkauf,tx-2,,,,";

        when(investmentTransactionRepository.existsByExternalId(anyString())).thenReturn(false);
        when(investmentService.create(any()))
                .thenReturn(InvestmentTransaction.builder().id(1L).build());

        importService.importCsv(csvFile(csv));

        ArgumentCaptor<InvestmentTransactionRequest> captor =
                ArgumentCaptor.forClass(InvestmentTransactionRequest.class);
        verify(investmentService).create(captor.capture());

        InvestmentTransactionRequest request = captor.getValue();
        assertThat(request.getAssetTicker()).isEqualTo("BTC"); // crypto symbol is not an ISIN
        assertThat(request.getAssetIsin()).isNull();
        assertThat(request.getAssetType()).isEqualTo(AssetType.CRYPTO);
    }

    @Test
    void importCsv_ShouldAddTaxToFees_WhenSellRowHasTax() throws IOException, CsvException {
        String csv = HEADER + "\n"
                + "2025-05-01T10:00:00.000Z,2025-05-01,DEFAULT,TRADING,SELL,FUND,MSCI World,IE00BK1PV551,"
                + "2.0000000000,90.000000,180.00,-1.00,-3.50,EUR,,,,Verkauf,tx-3,,,,";

        when(investmentTransactionRepository.existsByExternalId(anyString())).thenReturn(false);
        when(investmentService.create(any()))
                .thenReturn(InvestmentTransaction.builder().id(1L).build());

        importService.importCsv(csvFile(csv));

        ArgumentCaptor<InvestmentTransactionRequest> captor =
                ArgumentCaptor.forClass(InvestmentTransactionRequest.class);
        verify(investmentService).create(captor.capture());

        InvestmentTransactionRequest request = captor.getValue();
        assertThat(request.getTransactionType()).isEqualTo(InvestmentTransactionType.SELL);
        assertThat(request.getAssetType()).isEqualTo(AssetType.INDEX_ETF);
        assertThat(request.getFees()).isEqualByComparingTo(new BigDecimal("4.50")); // |fee| + |tax|
    }

    @Test
    void importCsv_ShouldSkipNonTradingRows_WhenFileContainsCashAndCorporateActions() throws IOException, CsvException {
        String csv = HEADER + "\n"
                + "2021-06-08T09:10:30.904632Z,2021-06-08,DEFAULT,CASH,CUSTOMER_INBOUND,,Maksym Radomskyi,,,,"
                + "100.000000,,,EUR,,,,einzahlung,tx-4,,,,\n"
                + "2025-06-01T10:00:00.000Z,2025-06-01,DEFAULT,CASH,DIVIDEND,STOCK,Apple,US0378331005,,,"
                + "0.50,,,EUR,,,,dividende,tx-5,,,,\n"
                + "2025-07-30T06:25:21.431Z,2025-07-30,DEFAULT,CORPORATE_ACTION,BONUS_ISSUE,STOCK,BYD,CNE100000296,"
                + "0.2578800000,,,,,,,,,BONUS_ISSUE,tx-6,,,,\n"
                + "2025-06-02T10:00:00.000Z,2025-06-02,DEFAULT,CASH,INTEREST_PAYMENT,,,,,,1.23,,,EUR,,,,zinsen,tx-7,,,,";

        ImportResult result = importService.importCsv(csvFile(csv));

        assertThat(result.getImported()).isZero();
        assertThat(result.getSkippedRows()).isEqualTo(4);
        verify(investmentService, never()).create(any());
    }

    @Test
    void importCsv_ShouldSkipDuplicates_WhenTransactionIdAlreadyImported() throws IOException, CsvException {
        String csv = HEADER + "\n"
                + "2025-03-07T20:12:59.302Z,2025-03-07,DEFAULT,TRADING,BUY,STOCK,Rheinmetall,DE0007030009,"
                + "0.0900090000,1111.000000,-100.00,-1.00,,EUR,,,,Kauf,tx-1,,,,";

        when(investmentTransactionRepository.existsByExternalId("tx-1")).thenReturn(true);

        ImportResult result = importService.importCsv(csvFile(csv));

        assertThat(result.getImported()).isZero();
        assertThat(result.getSkippedDuplicates()).isEqualTo(1);
        verify(investmentService, never()).create(any());
    }

    @Test
    void importCsv_ShouldThrowException_WhenFileIsEmpty() {
        assertThatThrownBy(() -> importService.importCsv(csvFile("")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV file is empty");
    }

    @Test
    void importCsv_ShouldThrowException_WhenHeaderIsNotTradeRepublic() {
        String csv = "Action,Time,ISIN\nMarket buy,2025-01-01,US123";

        assertThatThrownBy(() -> importService.importCsv(csvFile(csv)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a Trade Republic export");
    }
}
