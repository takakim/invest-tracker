package com.takakim.investtracker;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AccountStatus;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.FxRate;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.PortfolioStatus;
import com.takakim.investtracker.domain.Price;
import com.takakim.investtracker.domain.Quantity;
import com.takakim.investtracker.domain.ReturnMethod;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainValueObjectTests {
    @Test
    void currencyNormalizesAndRejectsInvalidCodes() {
        assertEquals("GBP", new Currency("gbp").code());
        assertEquals("USD", new Currency("USD").code());
        assertThrows(IllegalArgumentException.class, () -> new Currency(null));
        assertThrows(IllegalArgumentException.class, () -> new Currency(""));
        assertThrows(IllegalArgumentException.class, () -> new Currency("GB"));
        assertThrows(IllegalArgumentException.class, () -> new Currency("GBPD"));
        assertThrows(IllegalArgumentException.class, () -> new Currency("123"));
    }

    @Test
    void moneySupportsSameCurrencyArithmeticAndRejectsMismatch() {
        Currency gbp = new Currency("GBP");
        Money ten = new Money(new BigDecimal("10"), gbp);
        Money five = new Money(new BigDecimal("5"), gbp);
        assertEquals(new BigDecimal("15"), ten.add(five).amount());
        assertEquals(new BigDecimal("5"), ten.subtract(five).amount());
        assertEquals(new BigDecimal("20"), ten.multiply(new BigDecimal("2")).amount());
        assertEquals(gbp, ten.currency());

        assertThrows(NullPointerException.class, () -> new Money(null, gbp));
        assertThrows(NullPointerException.class, () -> new Money(BigDecimal.TEN, null));
        assertThrows(NullPointerException.class, () -> ten.add(null));
        assertThrows(NullPointerException.class, () -> ten.subtract(null));
        assertThrows(IllegalArgumentException.class, () -> ten.add(new Money(BigDecimal.ONE, new Currency("USD"))));
        assertThrows(IllegalArgumentException.class, () -> ten.subtract(new Money(BigDecimal.ONE, new Currency("USD"))));
    }

    @Test
    void quantityValidatesValues() {
        assertEquals(new BigDecimal("1.25"), new Quantity(new BigDecimal("1.25")).value());
        assertEquals(BigDecimal.ZERO, new Quantity(BigDecimal.ZERO).value());
        assertThrows(NullPointerException.class, () -> new Quantity(null));
        assertThrows(IllegalArgumentException.class, () -> new Quantity(new BigDecimal("-1")));
    }

    @Test
    void priceValidatesValues() {
        Currency gbp = new Currency("GBP");
        Price price = new Price(new BigDecimal("100.50"), gbp);
        assertEquals(new BigDecimal("100.50"), price.amount());
        assertEquals(gbp, price.currency());
        assertEquals(BigDecimal.ZERO, new Price(BigDecimal.ZERO, gbp).amount());
        assertThrows(NullPointerException.class, () -> new Price(null, gbp));
        assertThrows(NullPointerException.class, () -> new Price(BigDecimal.TEN, null));
        assertThrows(IllegalArgumentException.class, () -> new Price(new BigDecimal("-1"), gbp));
    }

    @Test
    void fxRateValidatesValues() {
        Currency gbp = new Currency("GBP");
        Currency usd = new Currency("USD");
        assertEquals(BigDecimal.ONE, new FxRate(gbp, gbp, BigDecimal.ONE).rate());
        assertEquals(new BigDecimal("1.30"), new FxRate(gbp, usd, new BigDecimal("1.30")).rate());

        assertThrows(NullPointerException.class, () -> new FxRate(null, usd, BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> new FxRate(gbp, null, BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> new FxRate(gbp, usd, null));
        assertThrows(IllegalArgumentException.class, () -> new FxRate(gbp, usd, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new FxRate(gbp, usd, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> new FxRate(gbp, gbp, new BigDecimal("2")));
        assertThrows(IllegalArgumentException.class, () -> new FxRate(gbp, gbp, new BigDecimal("0.5")));
    }

    @Test
    void portfolioValidatesInputsAndEnforcesLifecycle() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("  Growth Portfolio  ", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        assertEquals("Growth Portfolio", portfolio.getName());
        assertEquals(gbp, portfolio.getBaseCurrency());
        assertEquals(CostBasisMethod.FIFO, portfolio.getCostBasisMethod());
        assertEquals(ReturnMethod.XIRR, portfolio.getReturnMethod());
        assertEquals(PortfolioStatus.ACTIVE, portfolio.getStatus());
        assertNotNull(portfolio.getId());
        assertNotNull(portfolio.getCreatedAt());
        assertNotNull(portfolio.getUpdatedAt());

        portfolio.update(" Dividend Focus ", new Currency("USD"), CostBasisMethod.AVERAGE_COST, ReturnMethod.TWR);
        assertEquals("Dividend Focus", portfolio.getName());
        assertEquals("USD", portfolio.getBaseCurrency().code());
        assertEquals(CostBasisMethod.AVERAGE_COST, portfolio.getCostBasisMethod());
        assertEquals(ReturnMethod.TWR, portfolio.getReturnMethod());

        portfolio.archive();
        assertEquals(PortfolioStatus.ARCHIVED, portfolio.getStatus());
        assertThrows(IllegalStateException.class, () -> portfolio.update("New", gbp, CostBasisMethod.LIFO, ReturnMethod.MWR));

        assertThrows(IllegalArgumentException.class, () -> new Portfolio(null, gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR));
        assertThrows(IllegalArgumentException.class, () -> new Portfolio("", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR));
        assertThrows(IllegalArgumentException.class, () -> new Portfolio("   ", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR));
        assertThrows(IllegalArgumentException.class, () -> new Portfolio("a".repeat(121), gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR));

        Portfolio active = new Portfolio("Active", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        assertThrows(IllegalArgumentException.class, () -> active.update(null, gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR));
        assertThrows(IllegalArgumentException.class, () -> active.update("", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR));
        assertThrows(IllegalArgumentException.class, () -> active.update("   ", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR));
        assertThrows(IllegalArgumentException.class, () -> active.update("a".repeat(121), gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR));
    }

    @Test
    void accountValidatesInputsAndEnforcesLifecycle() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);

        assertThrows(NullPointerException.class, () -> new Account(null, "ISA", "Broker", gbp));
        assertThrows(NullPointerException.class, () -> new Account(portfolio, "ISA", "Broker", null));
        assertThrows(IllegalArgumentException.class, () -> new Account(portfolio, null, "Broker", gbp));
        assertThrows(IllegalArgumentException.class, () -> new Account(portfolio, "", "Broker", gbp));
        assertThrows(IllegalArgumentException.class, () -> new Account(portfolio, "   ", "Broker", gbp));
        assertThrows(IllegalArgumentException.class, () -> new Account(portfolio, "a".repeat(121), "Broker", gbp));
        assertThrows(IllegalArgumentException.class, () -> new Account(portfolio, "ISA", null, gbp));
        assertThrows(IllegalArgumentException.class, () -> new Account(portfolio, "ISA", "", gbp));
        assertThrows(IllegalArgumentException.class, () -> new Account(portfolio, "ISA", "   ", gbp));
        assertThrows(IllegalArgumentException.class, () -> new Account(portfolio, "ISA", "b".repeat(121), gbp));

        Account account = new Account(portfolio, "  ISA Account  ", "  Interactive Investor  ", gbp);
        assertEquals("ISA Account", account.getName());
        assertEquals("Interactive Investor", account.getBrokerName());
        assertEquals(gbp, account.getAccountCurrency());
        assertEquals(portfolio, account.getPortfolio());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertNotNull(account.getId());
        assertNotNull(account.getCreatedAt());
        assertNotNull(account.getUpdatedAt());

        account.update(" SIPP ", " Hargreaves Lansdown ", new Currency("USD"));
        assertEquals("SIPP", account.getName());
        assertEquals("Hargreaves Lansdown", account.getBrokerName());
        assertEquals("USD", account.getAccountCurrency().code());

        assertThrows(NullPointerException.class, () -> account.update("SIPP", "Broker", null));
        assertThrows(IllegalArgumentException.class, () -> account.update(null, "Broker", gbp));
        assertThrows(IllegalArgumentException.class, () -> account.update("", "Broker", gbp));
        assertThrows(IllegalArgumentException.class, () -> account.update("   ", "Broker", gbp));
        assertThrows(IllegalArgumentException.class, () -> account.update("a".repeat(121), "Broker", gbp));
        assertThrows(IllegalArgumentException.class, () -> account.update("SIPP", null, gbp));
        assertThrows(IllegalArgumentException.class, () -> account.update("SIPP", "", gbp));
        assertThrows(IllegalArgumentException.class, () -> account.update("SIPP", "   ", gbp));
        assertThrows(IllegalArgumentException.class, () -> account.update("SIPP", "b".repeat(121), gbp));

        account.archive();
        assertEquals(AccountStatus.ARCHIVED, account.getStatus());
        assertThrows(IllegalStateException.class, () -> account.update("SIPP", "Broker", gbp));
    }

    @Test
    void instrumentValidatesInputsAndNormalizesFields() {
        Currency gbp = new Currency("GBP");

        assertThrows(IllegalArgumentException.class, () -> new Instrument(null, AssetClass.STOCK, "TICK", "ISIN123", "LSE", gbp));
        assertThrows(IllegalArgumentException.class, () -> new Instrument("", AssetClass.STOCK, "TICK", "ISIN123", "LSE", gbp));
        assertThrows(IllegalArgumentException.class, () -> new Instrument("   ", AssetClass.STOCK, "TICK", "ISIN123", "LSE", gbp));
        assertThrows(IllegalArgumentException.class, () -> new Instrument("a".repeat(161), AssetClass.STOCK, "TICK", "ISIN123", "LSE", gbp));
        assertThrows(NullPointerException.class, () -> new Instrument("Acme", null, "TICK", "ISIN123", "LSE", gbp));
        assertThrows(NullPointerException.class, () -> new Instrument("Acme", AssetClass.STOCK, "TICK", "ISIN123", "LSE", null));

        Instrument instrument = new Instrument("  Apple Inc  ", AssetClass.STOCK, " AAPL ", " US0378331005 ", " NASDAQ ", gbp);
        assertEquals("Apple Inc", instrument.getName());
        assertEquals(AssetClass.STOCK, instrument.getAssetClass());
        assertEquals("AAPL", instrument.getTicker());
        assertEquals("US0378331005", instrument.getIsin());
        assertEquals("NASDAQ", instrument.getExchange());
        assertEquals(gbp, instrument.getCurrency());
        assertNotNull(instrument.getId());
        assertNotNull(instrument.getCreatedAt());
        assertNotNull(instrument.getUpdatedAt());

        instrument.update(" Apple Inc Updated ", AssetClass.BOND, "  ", "", null, new Currency("USD"));
        assertEquals("Apple Inc Updated", instrument.getName());
        assertEquals(AssetClass.BOND, instrument.getAssetClass());
        assertNull(instrument.getTicker());
        assertNull(instrument.getIsin());
        assertNull(instrument.getExchange());
        assertEquals("USD", instrument.getCurrency().code());

        assertThrows(IllegalArgumentException.class, () -> instrument.update(null, AssetClass.STOCK, null, null, null, gbp));
        assertThrows(IllegalArgumentException.class, () -> instrument.update("", AssetClass.STOCK, null, null, null, gbp));
        assertThrows(IllegalArgumentException.class, () -> instrument.update("  ", AssetClass.STOCK, null, null, null, gbp));
        assertThrows(IllegalArgumentException.class, () -> instrument.update("a".repeat(161), AssetClass.STOCK, null, null, null, gbp));
        assertThrows(NullPointerException.class, () -> instrument.update("Valid", null, null, null, null, gbp));
        assertThrows(NullPointerException.class, () -> instrument.update("Valid", AssetClass.STOCK, null, null, null, null));
    }
}

