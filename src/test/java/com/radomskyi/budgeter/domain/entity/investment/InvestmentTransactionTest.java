package com.radomskyi.budgeter.domain.entity.investment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InvestmentTransactionTest {

    private Asset asset;
    private Investment investment;
    private InvestmentTransaction buyTransaction;
    private InvestmentTransaction sellTransaction;

    @BeforeEach
    void setUp() {
        asset = Asset.builder()
                .ticker("AAPL")
                .name("Apple Inc.")
                .isin("US0378331005")
                .assetType(AssetType.STOCK)
                .investmentStyle(InvestmentStyle.GROWTH)
                .build();

        investment = Investment.createNew(asset, Currency.USD, "TestBroker");

        buyTransaction = InvestmentTransaction.builder()
                .transactionType(InvestmentTransactionType.BUY)
                .investment(investment)
                .units(new BigDecimal("10.0"))
                .pricePerUnit(new BigDecimal("150.00"))
                .fees(new BigDecimal("1.50"))
                .currency(Currency.USD)
                .exchangeRate(new BigDecimal("0.85"))
                .amount(new BigDecimal("1276.50")) // (10 * 150 * 0.85) + 1.50 (fees added after conversion)
                .name("Apple Inc. AAPL")
                .description("Test buy transaction")
                .build();

        sellTransaction = InvestmentTransaction.builder()
                .transactionType(InvestmentTransactionType.SELL)
                .investment(investment)
                .units(new BigDecimal("5.0"))
                .pricePerUnit(new BigDecimal("160.00"))
                .fees(new BigDecimal("2.00"))
                .currency(Currency.EUR)
                .exchangeRate(null) // EUR, no conversion needed
                .amount(new BigDecimal("798.00")) // (5 * 160) - 2
                .name("Apple Inc. AAPL")
                .description("Test sell transaction")
                .realizedGainLoss(new BigDecimal("50.00"))
                .build();
    }

    @Test
    void testBuyTransactionCreation() {
        assertThat(buyTransaction.getTransactionType()).isEqualTo(InvestmentTransactionType.BUY);
        assertThat(buyTransaction.getAsset()).isEqualTo(asset);
        assertThat(buyTransaction.getUnits()).isEqualTo(new BigDecimal("10.0"));
        assertThat(buyTransaction.getPricePerUnit()).isEqualTo(new BigDecimal("150.00"));
        assertThat(buyTransaction.getFees()).isEqualTo(new BigDecimal("1.50"));
        assertThat(buyTransaction.getCurrency()).isEqualTo(Currency.USD);
        assertThat(buyTransaction.getExchangeRate()).isEqualTo(new BigDecimal("0.85"));
        assertThat(buyTransaction.getAmount()).isEqualTo(new BigDecimal("1276.50"));
        assertThat(buyTransaction.getName()).isEqualTo("Apple Inc. AAPL");
        assertThat(buyTransaction.getDescription()).isEqualTo("Test buy transaction");
    }

    @Test
    void testSellTransactionCreation() {
        assertThat(sellTransaction.getTransactionType()).isEqualTo(InvestmentTransactionType.SELL);
        assertThat(sellTransaction.getAsset()).isEqualTo(asset);
        assertThat(sellTransaction.getUnits()).isEqualTo(new BigDecimal("5.0"));
        assertThat(sellTransaction.getPricePerUnit()).isEqualTo(new BigDecimal("160.00"));
        assertThat(sellTransaction.getFees()).isEqualTo(new BigDecimal("2.00"));
        assertThat(sellTransaction.getCurrency()).isEqualTo(Currency.EUR);
        assertThat(sellTransaction.getExchangeRate()).isNull();
        assertThat(sellTransaction.getAmount()).isEqualTo(new BigDecimal("798.00"));
        assertThat(sellTransaction.getRealizedGainLoss()).isEqualTo(new BigDecimal("50.00"));
    }

    @Test
    void testTransactionExtendsTransaction() {
        // Verify that InvestmentTransaction extends Transaction and has the inherited fields
        assertThat(buyTransaction.getId()).isNull(); // Not set in builder
        assertThat(buyTransaction.getCreatedAt()).isNull(); // Not set in builder
        assertThat(buyTransaction.getUpdatedAt()).isNull(); // Not set in builder
    }

    @Test
    void testTransactionWithEurNoExchangeRate() {
        InvestmentTransaction eurTransaction = InvestmentTransaction.builder()
                .transactionType(InvestmentTransactionType.BUY)
                .investment(investment)
                .units(new BigDecimal("5.0"))
                .pricePerUnit(new BigDecimal("100.00"))
                .fees(new BigDecimal("1.00"))
                .currency(Currency.EUR)
                .exchangeRate(null)
                .amount(new BigDecimal("499.00")) // 5 * 100 - 1
                .build();

        assertThat(eurTransaction.getCurrency()).isEqualTo(Currency.EUR);
        assertThat(eurTransaction.getExchangeRate()).isNull();
        assertThat(eurTransaction.getAmount()).isEqualTo(new BigDecimal("499.00"));
    }

    @Test
    void testTransactionWithoutFees() {
        InvestmentTransaction noFeesTransaction = InvestmentTransaction.builder()
                .transactionType(InvestmentTransactionType.BUY)
                .investment(investment)
                .units(new BigDecimal("2.0"))
                .pricePerUnit(new BigDecimal("200.00"))
                .currency(Currency.EUR)
                .amount(new BigDecimal("400.00")) // 2 * 200
                .build();

        assertThat(noFeesTransaction.getFees()).isNull();
        assertThat(noFeesTransaction.getAmount()).isEqualTo(new BigDecimal("400.00"));
    }

    @Test
    void testTransactionTypeEnum() {
        assertThat(InvestmentTransactionType.values()).hasSize(3);
        assertThat(InvestmentTransactionType.valueOf("BUY")).isEqualTo(InvestmentTransactionType.BUY);
        assertThat(InvestmentTransactionType.valueOf("SELL")).isEqualTo(InvestmentTransactionType.SELL);
        assertThat(InvestmentTransactionType.valueOf("DIVIDEND")).isEqualTo(InvestmentTransactionType.DIVIDEND);
    }

    @Test
    void testCurrencyEnum() {
        assertThat(Currency.values()).hasSize(6);
        assertThat(Currency.valueOf("USD")).isEqualTo(Currency.USD);
        assertThat(Currency.valueOf("EUR")).isEqualTo(Currency.EUR);
        assertThat(Currency.valueOf("GBP")).isEqualTo(Currency.GBP);
        assertThat(Currency.valueOf("GBX")).isEqualTo(Currency.GBX);
        assertThat(Currency.valueOf("CHF")).isEqualTo(Currency.CHF);
        assertThat(Currency.valueOf("CAD")).isEqualTo(Currency.CAD);
    }

    @Test
    void testFractionalUnits() {
        InvestmentTransaction fractionalTransaction = InvestmentTransaction.builder()
                .transactionType(InvestmentTransactionType.BUY)
                .investment(investment)
                .units(new BigDecimal("0.5"))
                .pricePerUnit(new BigDecimal("300.00"))
                .currency(Currency.EUR)
                .amount(new BigDecimal("150.00"))
                .build();

        assertThat(fractionalTransaction.getUnits()).isEqualTo(new BigDecimal("0.5"));
        assertThat(fractionalTransaction.getPricePerUnit()).isEqualTo(new BigDecimal("300.00"));
        assertThat(fractionalTransaction.getAmount()).isEqualTo(new BigDecimal("150.00"));
    }

    @Test
    void testEqualsAndHashCode() {
        InvestmentTransaction transaction1 = InvestmentTransaction.builder()
                .transactionType(InvestmentTransactionType.BUY)
                .investment(investment)
                .units(new BigDecimal("10.0"))
                .pricePerUnit(new BigDecimal("150.00"))
                .currency(Currency.USD)
                .amount(new BigDecimal("1500.00"))
                .build();

        InvestmentTransaction transaction2 = InvestmentTransaction.builder()
                .transactionType(InvestmentTransactionType.BUY)
                .investment(investment)
                .units(new BigDecimal("10.0"))
                .pricePerUnit(new BigDecimal("150.00"))
                .currency(Currency.USD)
                .amount(new BigDecimal("1500.00"))
                .build();

        InvestmentTransaction transaction3 = InvestmentTransaction.builder()
                .transactionType(InvestmentTransactionType.SELL)
                .investment(investment)
                .units(new BigDecimal("5.0"))
                .pricePerUnit(new BigDecimal("160.00"))
                .currency(Currency.EUR)
                .amount(new BigDecimal("800.00"))
                .build();

        // Note: Since these extend Transaction, equals/hashCode is based on the Transaction fields
        // and the specific fields of InvestmentTransaction. Two transactions with same data should be
        // equal
        assertThat(transaction1).isEqualTo(transaction2);
        assertThat(transaction1).isNotEqualTo(transaction3);
    }
}
