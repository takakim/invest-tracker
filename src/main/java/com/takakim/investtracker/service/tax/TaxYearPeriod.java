package com.takakim.investtracker.service.tax;

import com.takakim.investtracker.domain.TaxRegime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record TaxYearPeriod(
        String label,
        TaxRegime regime,
        Instant startInstant,
        Instant endInstant,
        BigDecimal defaultCgtAllowance,
        BigDecimal defaultDividendAllowance,
        BigDecimal defaultCgtBasicRate,
        BigDecimal defaultCgtHigherRate,
        BigDecimal defaultDividendBasicRate,
        BigDecimal defaultDividendHigherRate,
        BigDecimal defaultDividendAdditionalRate
) {
    private static final Pattern UK_TAX_YEAR_PATTERN = Pattern.compile("^(\\d{4})[/-](\\d{2}|\\d{4})$");
    private static final Pattern CALENDAR_YEAR_PATTERN = Pattern.compile("^(\\d{4})$");

    public static TaxYearPeriod of(String taxYearLabel, TaxRegime regime) {
        TaxRegime safeRegime = regime != null ? regime : TaxRegime.UK_HMRC;
        String safeLabel = (taxYearLabel != null && !taxYearLabel.isBlank())
                ? taxYearLabel.trim()
                : (safeRegime == TaxRegime.UK_HMRC ? currentUkTaxYearLabel() : currentCalendarYearLabel());

        if (safeRegime == TaxRegime.UK_HMRC) {
            Matcher m = UK_TAX_YEAR_PATTERN.matcher(safeLabel);
            if (m.matches()) {
                int startYear = Integer.parseInt(m.group(1));
                int endYear = m.group(2).length() == 2
                        ? (startYear / 100) * 100 + Integer.parseInt(m.group(2))
                        : Integer.parseInt(m.group(2));

                if (endYear == startYear + 1) {
                    LocalDate startDate = LocalDate.of(startYear, 4, 6);
                    LocalDate endDate = LocalDate.of(endYear, 4, 5);

                    Instant startInstant = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
                    Instant endInstant = endDate.atTime(23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC);

                    String canonicalLabel = String.format("%04d/%02d", startYear, endYear % 100);

                    BigDecimal cgtAllowance;
                    BigDecimal dividendAllowance;

                    if (startYear < 2023) {
                        cgtAllowance = new BigDecimal("12300.0000");
                        dividendAllowance = new BigDecimal("2000.0000");
                    } else if (startYear == 2023) {
                        cgtAllowance = new BigDecimal("6000.0000");
                        dividendAllowance = new BigDecimal("1000.0000");
                    } else { // 2024, 2025, 2026+
                        cgtAllowance = new BigDecimal("3000.0000");
                        dividendAllowance = new BigDecimal("500.0000");
                    }

                    // UK CGT standard brackets: 18% basic / 24% higher
                    BigDecimal cgtBasic = new BigDecimal("0.1800");
                    BigDecimal cgtHigher = new BigDecimal("0.2400");

                    // UK Dividend income tax rates: 8.75% basic / 33.75% higher / 39.35% additional
                    BigDecimal divBasic = new BigDecimal("0.0875");
                    BigDecimal divHigher = new BigDecimal("0.3375");
                    BigDecimal divAdd = new BigDecimal("0.3935");

                    return new TaxYearPeriod(
                            canonicalLabel,
                            TaxRegime.UK_HMRC,
                            startInstant,
                            endInstant,
                            cgtAllowance,
                            dividendAllowance,
                            cgtBasic,
                            cgtHigher,
                            divBasic,
                            divHigher,
                            divAdd
                    );
                }
            }
        }

        // Fallback or CALENDAR_YEAR
        Matcher mCal = CALENDAR_YEAR_PATTERN.matcher(safeLabel);
        int year = mCal.matches() ? Integer.parseInt(mCal.group(1)) : LocalDate.now(ZoneOffset.UTC).getYear();

        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);
        Instant startInstant = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endInstant = endDate.atTime(23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC);

        BigDecimal cgtBasic = safeRegime == TaxRegime.CALENDAR_YEAR ? new BigDecimal("0.1500") : new BigDecimal("0.1800");
        BigDecimal cgtHigher = safeRegime == TaxRegime.CALENDAR_YEAR ? new BigDecimal("0.2000") : new BigDecimal("0.2400");
        BigDecimal divAllowance = safeRegime == TaxRegime.CALENDAR_YEAR ? new BigDecimal("1000.0000") : new BigDecimal("500.0000");

        return new TaxYearPeriod(
                String.valueOf(year),
                safeRegime,
                startInstant,
                endInstant,
                new BigDecimal("3000.0000"),
                divAllowance,
                cgtBasic,
                cgtHigher,
                new BigDecimal("0.0875"),
                new BigDecimal("0.3375"),
                new BigDecimal("0.3935")
        );
    }

    public static String currentUkTaxYearLabel() {
        return resolveTaxYearForInstant(Instant.now(), TaxRegime.UK_HMRC);
    }

    public static String currentCalendarYearLabel() {
        return resolveTaxYearForInstant(Instant.now(), TaxRegime.CALENDAR_YEAR);
    }

    public static String resolveTaxYearForInstant(Instant instant, TaxRegime regime) {
        Objects.requireNonNull(instant, "instant must not be null");
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();

        if (regime == TaxRegime.CALENDAR_YEAR) {
            return String.valueOf(date.getYear());
        }

        int startYear = (date.getMonthValue() > 4 || (date.getMonthValue() == 4 && date.getDayOfMonth() >= 6))
                ? date.getYear()
                : date.getYear() - 1;
        return String.format("%04d/%02d", startYear, (startYear + 1) % 100);
    }

    public boolean contains(Instant instant) {
        if (instant == null) return false;
        return !instant.isBefore(startInstant) && !instant.isAfter(endInstant);
    }

    public Instant periodStart() {
        return startInstant;
    }

    public Instant periodEnd() {
        return endInstant;
    }

    public int startYear() {
        return startInstant.atZone(ZoneOffset.UTC).getYear();
    }

    public int endYear() {
        return endInstant.atZone(ZoneOffset.UTC).getYear();
    }

    public BigDecimal cgtBasicRatePercentage() {
        return defaultCgtBasicRate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_EVEN);
    }

    public BigDecimal cgtHigherRatePercentage() {
        return defaultCgtHigherRate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_EVEN);
    }

    public BigDecimal dividendBasicRatePercentage() {
        return defaultDividendBasicRate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_EVEN);
    }

    public BigDecimal dividendHigherRatePercentage() {
        return defaultDividendHigherRate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_EVEN);
    }

    public BigDecimal dividendAdditionalRatePercentage() {
        return defaultDividendAdditionalRate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_EVEN);
    }
}
