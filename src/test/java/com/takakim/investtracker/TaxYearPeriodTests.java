package com.takakim.investtracker;

import com.takakim.investtracker.domain.TaxRegime;
import com.takakim.investtracker.service.tax.TaxYearPeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TaxYearPeriodTests {

    @Test
    @DisplayName("UK HMRC tax year parsing valid format 2024/25")
    void testUkHmrcValidTaxYear() {
        TaxYearPeriod period = TaxYearPeriod.of("2024/25", TaxRegime.UK_HMRC);
        assertEquals("2024/25", period.label());
        assertEquals(TaxRegime.UK_HMRC, period.regime());
        assertEquals(2024, period.startYear());
        assertEquals(2025, period.endYear());

        Instant expectedStart = LocalDate.of(2024, 4, 6).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant expectedEnd = LocalDate.of(2025, 4, 5).atTime(23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC);

        assertEquals(expectedStart, period.periodStart());
        assertEquals(expectedEnd, period.periodEnd());
        assertEquals(new BigDecimal("3000.0000"), period.defaultCgtAllowance());
        assertEquals(new BigDecimal("500.0000"), period.defaultDividendAllowance());
        assertEquals(new BigDecimal("18.00"), period.cgtBasicRatePercentage());
        assertEquals(new BigDecimal("24.00"), period.cgtHigherRatePercentage());
        assertEquals(new BigDecimal("8.75"), period.dividendBasicRatePercentage());
        assertEquals(new BigDecimal("33.75"), period.dividendHigherRatePercentage());
        assertEquals(new BigDecimal("39.35"), period.dividendAdditionalRatePercentage());
    }

    @Test
    @DisplayName("Historical UK HMRC allowances: 2023/24 and 2022/23")
    void testUkHmrcHistoricalAllowances() {
        TaxYearPeriod period2324 = TaxYearPeriod.of("2023/24", TaxRegime.UK_HMRC);
        assertEquals(new BigDecimal("6000.0000"), period2324.defaultCgtAllowance());
        assertEquals(new BigDecimal("1000.0000"), period2324.defaultDividendAllowance());

        TaxYearPeriod period2223 = TaxYearPeriod.of("2022/23", TaxRegime.UK_HMRC);
        assertEquals(new BigDecimal("12300.0000"), period2223.defaultCgtAllowance());
        assertEquals(new BigDecimal("2000.0000"), period2223.defaultDividendAllowance());
    }

    @Test
    @DisplayName("Calendar year parsing 2025")
    void testCalendarYearParsing() {
        TaxYearPeriod period = TaxYearPeriod.of("2025", TaxRegime.CALENDAR_YEAR);
        assertEquals("2025", period.label());
        assertEquals(TaxRegime.CALENDAR_YEAR, period.regime());
        assertEquals(2025, period.startYear());
        assertEquals(2025, period.endYear());

        Instant expectedStart = LocalDate.of(2025, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant expectedEnd = LocalDate.of(2025, 12, 31).atTime(23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC);

        assertEquals(expectedStart, period.periodStart());
        assertEquals(expectedEnd, period.periodEnd());
        assertEquals(new BigDecimal("3000.0000"), period.defaultCgtAllowance());
        assertEquals(new BigDecimal("1000.0000"), period.defaultDividendAllowance());
        assertEquals(new BigDecimal("15.00"), period.cgtBasicRatePercentage());
        assertEquals(new BigDecimal("20.00"), period.cgtHigherRatePercentage());
    }

    @Test
    @DisplayName("UK HMRC tax year parsing with 4-digit end year format 2024-2025 and 2024/2025")
    void testUkHmrcFourDigitEndYear() {
        TaxYearPeriod periodHyphen = TaxYearPeriod.of("2024-2025", TaxRegime.UK_HMRC);
        assertEquals("2024/25", periodHyphen.label());
        assertEquals(2024, periodHyphen.startYear());
        assertEquals(2025, periodHyphen.endYear());

        TaxYearPeriod periodSlash = TaxYearPeriod.of("2024/2025", TaxRegime.UK_HMRC);
        assertEquals("2024/25", periodSlash.label());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "invalid", "2024/99", "2024-2030"})
    @DisplayName("Invalid tax year labels fallback gracefully")
    void testInvalidTaxYearFallback(String invalidLabel) {
        TaxYearPeriod periodUk = TaxYearPeriod.of(invalidLabel, TaxRegime.UK_HMRC);
        assertNotNull(periodUk);
        assertEquals(TaxRegime.UK_HMRC, periodUk.regime());

        TaxYearPeriod periodCal = TaxYearPeriod.of(invalidLabel, TaxRegime.CALENDAR_YEAR);
        assertNotNull(periodCal);
        assertEquals(TaxRegime.CALENDAR_YEAR, periodCal.regime());
    }

    @Test
    @DisplayName("Null regime defaults to UK HMRC")
    void testNullRegimeDefaults() {
        TaxYearPeriod period = TaxYearPeriod.of(null, null);
        assertNotNull(period);
        assertEquals(TaxRegime.UK_HMRC, period.regime());
    }

    @ParameterizedTest
    @CsvSource({
            "2024-01-15T12:00:00Z, 2023/24",
            "2024-04-05T23:59:59Z, 2023/24",
            "2024-04-06T00:00:00Z, 2024/25",
            "2024-04-07T12:00:00Z, 2024/25",
            "2024-05-10T12:00:00Z, 2024/25",
            "2024-12-31T23:59:59Z, 2024/25",
            "2025-04-05T12:00:00Z, 2024/25",
            "2025-04-06T00:00:00Z, 2025/26"
    })
    @DisplayName("Resolve UK HMRC tax year from Instant across boundary dates")
    void testResolveUkTaxYear(String instantIso, String expectedYear) {
        Instant instant = Instant.parse(instantIso);
        String resolved = TaxYearPeriod.resolveTaxYearForInstant(instant, TaxRegime.UK_HMRC);
        assertEquals(expectedYear, resolved);

        TaxYearPeriod period = TaxYearPeriod.of(expectedYear, TaxRegime.UK_HMRC);
        assertTrue(period.contains(instant));
    }

    @Test
    @DisplayName("Resolve Calendar tax year from Instant")
    void testResolveCalendarTaxYear() {
        Instant instant = Instant.parse("2024-08-15T10:30:00Z");
        String resolved = TaxYearPeriod.resolveTaxYearForInstant(instant, TaxRegime.CALENDAR_YEAR);
        assertEquals("2024", resolved);

        TaxYearPeriod period = TaxYearPeriod.of("2024", TaxRegime.CALENDAR_YEAR);
        assertTrue(period.contains(instant));
        assertFalse(period.contains(Instant.parse("2025-01-01T00:00:00Z")));
        assertFalse(period.contains(Instant.parse("2023-12-31T23:59:59Z")));
    }

    @Test
    @DisplayName("contains returns false for null instant")
    void testContainsNull() {
        TaxYearPeriod period = TaxYearPeriod.of("2024/25", TaxRegime.UK_HMRC);
        assertFalse(period.contains(null));
    }

    @Test
    @DisplayName("Current year helpers return non-empty strings")
    void testCurrentYearHelpers() {
        assertNotNull(TaxYearPeriod.currentUkTaxYearLabel());
        assertTrue(TaxYearPeriod.currentUkTaxYearLabel().contains("/"));
        assertNotNull(TaxYearPeriod.currentCalendarYearLabel());
        assertEquals(4, TaxYearPeriod.currentCalendarYearLabel().length());
    }
}
