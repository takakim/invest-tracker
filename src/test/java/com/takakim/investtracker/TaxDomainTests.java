package com.takakim.investtracker;

import com.takakim.investtracker.domain.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaxDomainTests {

    @Test
    @DisplayName("PortfolioTaxSettings entity constructor, update, and getters")
    void testPortfolioTaxSettingsDomain() {
        Portfolio portfolio = new Portfolio("Portfolio", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.TWR);
        PortfolioTaxSettings settings = new PortfolioTaxSettings(
                portfolio,
                "2024/25",
                TaxRegime.UK_HMRC,
                new BigDecimal("3000.00"),
                new BigDecimal("500.00"),
                new BigDecimal("1000.00"),
                "Initial note"
        );

        assertNotNull(settings.getId());
        assertEquals(portfolio, settings.getPortfolio());
        assertEquals("2024/25", settings.getTaxYear());
        assertEquals(TaxRegime.UK_HMRC, settings.getTaxRegime());
        assertEquals(new BigDecimal("3000.00"), settings.getCgtAllowance());
        assertEquals(new BigDecimal("500.00"), settings.getDividendAllowance());
        assertEquals(new BigDecimal("1000.00"), settings.getLossCarryforward());
        assertEquals("Initial note", settings.getNotes());
        assertNotNull(settings.getCreatedAt());
        assertNotNull(settings.getUpdatedAt());

        settings.update(
                TaxRegime.CALENDAR_YEAR,
                new BigDecimal("4000.00"),
                new BigDecimal("800.00"),
                null,
                "Updated note"
        );

        assertEquals(TaxRegime.CALENDAR_YEAR, settings.getTaxRegime());
        assertEquals(new BigDecimal("4000.00"), settings.getCgtAllowance());
        assertEquals(new BigDecimal("800.00"), settings.getDividendAllowance());
        assertEquals(BigDecimal.ZERO, settings.getLossCarryforward());
        assertEquals("Updated note", settings.getNotes());

        // Test update with null taxRegime (should preserve existing regime)
        settings.update(null, new BigDecimal("5000.00"), null, new BigDecimal("200.00"), null);
        assertEquals(TaxRegime.CALENDAR_YEAR, settings.getTaxRegime());
        assertEquals(new BigDecimal("5000.00"), settings.getCgtAllowance());
        assertNull(settings.getDividendAllowance());
        assertEquals(new BigDecimal("200.00"), settings.getLossCarryforward());

        // Test constructor with null regime and null loss carryforward
        PortfolioTaxSettings nullRegimeSettings = new PortfolioTaxSettings(
                portfolio, "2024/25", null, null, null, null, null
        );
        assertEquals(TaxRegime.UK_HMRC, nullRegimeSettings.getTaxRegime());
        assertEquals(BigDecimal.ZERO, nullRegimeSettings.getLossCarryforward());
        assertNull(nullRegimeSettings.getCgtAllowance());
        assertNull(nullRegimeSettings.getDividendAllowance());
        assertNull(nullRegimeSettings.getNotes());
    }

    @Test
    @DisplayName("Account tax treatment methods and isTaxExempt logic")
    void testAccountTaxTreatment() {
        Portfolio portfolio = new Portfolio("Portfolio", new Currency("GBP"), CostBasisMethod.FIFO, ReturnMethod.TWR);

        Account taxable = new Account(portfolio, "GIA", "Broker", new Currency("GBP"), AccountTaxTreatment.TAXABLE);
        assertFalse(taxable.isTaxExempt());
        assertEquals(AccountTaxTreatment.TAXABLE, taxable.getTaxTreatment());

        Account exempt = new Account(portfolio, "ISA", "Broker", new Currency("GBP"), AccountTaxTreatment.TAX_EXEMPT);
        assertTrue(exempt.isTaxExempt());
        assertEquals(AccountTaxTreatment.TAX_EXEMPT, exempt.getTaxTreatment());

        Account deferred = new Account(portfolio, "SIPP", "Broker", new Currency("GBP"), AccountTaxTreatment.TAX_DEFERRED);
        assertTrue(deferred.isTaxExempt());
        assertEquals(AccountTaxTreatment.TAX_DEFERRED, deferred.getTaxTreatment());

        Account defaultAcc = new Account(portfolio, "Default", "Broker", new Currency("GBP"));
        assertEquals(AccountTaxTreatment.TAXABLE, defaultAcc.getTaxTreatment());
        assertFalse(defaultAcc.isTaxExempt());

        defaultAcc.update("Default Updated", "Broker", new Currency("GBP"), AccountTaxTreatment.TAX_EXEMPT);
        assertTrue(defaultAcc.isTaxExempt());
        assertEquals(AccountTaxTreatment.TAX_EXEMPT, defaultAcc.getTaxTreatment());

        defaultAcc.setTaxTreatment(null);
        assertEquals(AccountTaxTreatment.TAXABLE, defaultAcc.getTaxTreatment());
        assertFalse(defaultAcc.isTaxExempt());
    }
}
