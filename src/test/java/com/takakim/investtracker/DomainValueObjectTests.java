package com.takakim.investtracker;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.FxRate;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.Quantity;
import com.takakim.investtracker.domain.ReturnMethod;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainValueObjectTests {
    @Test
    void currencyNormalizesAndRejectsInvalidCodes() {
        assertEquals("GBP", new Currency("gbp").code());
        assertThrows(IllegalArgumentException.class, () -> new Currency("GB"));
    }

    @Test
    void moneySupportsSameCurrencyArithmeticAndRejectsMismatch() {
        Money ten = new Money(new BigDecimal("10"), new Currency("GBP"));
        Money five = new Money(new BigDecimal("5"), new Currency("GBP"));
        assertEquals(new BigDecimal("15"), ten.add(five).amount());
        assertEquals(new BigDecimal("5"), ten.subtract(five).amount());
        assertEquals(new BigDecimal("20"), ten.multiply(new BigDecimal("2")).amount());
        assertThrows(IllegalArgumentException.class, () -> ten.add(new Money(BigDecimal.ONE, new Currency("USD"))));
    }

    @Test
    void quantityPriceAndFxRateValidateValues() {
        assertEquals(new BigDecimal("1.25"), new Quantity(new BigDecimal("1.25")).value());
        assertThrows(IllegalArgumentException.class, () -> new Quantity(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> new com.takakim.investtracker.domain.Price(new BigDecimal("-1"), new Currency("GBP")));
        assertEquals(BigDecimal.ONE, new FxRate(new Currency("GBP"), new Currency("GBP"), BigDecimal.ONE).rate());
        assertThrows(IllegalArgumentException.class, () -> new FxRate(new Currency("GBP"), new Currency("USD"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new FxRate(new Currency("GBP"), new Currency("GBP"), new BigDecimal("2")));
    }

    @Test
    void portfolioAndAccountEnforceLifecycleRules() {
        Portfolio portfolio = new Portfolio("Test", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "Broker", "Broker Ltd", new Currency("GBP"));
        account.update("Broker 2", "Broker Ltd 2", new Currency("USD"));
        account.archive();
        assertThrows(IllegalStateException.class, () -> account.update("x", "y", new Currency("GBP")));
        portfolio.update("Test 2", new Currency("EUR"), CostBasisMethod.LIFO, ReturnMethod.TWR);
        portfolio.archive();
        assertThrows(IllegalStateException.class, () -> portfolio.update("x", new Currency("GBP"), CostBasisMethod.AVERAGE_COST, ReturnMethod.MWR));
    }

    @Test
    void instrumentNormalizesOptionalIdentifiers() {
        Instrument instrument = new Instrument("Acme", AssetClass.STOCK, " ACME ", " GB00ACME1234 ", " LSE ", new Currency("gbp"));
        assertEquals("ACME", instrument.getTicker());
        assertEquals("GB00ACME1234", instrument.getIsin());
        assertEquals("LSE", instrument.getExchange());
        instrument.update("Acme plc", AssetClass.ETF, null, null, null, new Currency("USD"));
        assertEquals(AssetClass.ETF, instrument.getAssetClass());
    }
}
