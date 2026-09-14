package com.takakim.investtracker.service.export;

import com.takakim.investtracker.api.ApiDtos.CashFlowAnalyticsResponse;
import com.takakim.investtracker.api.ApiDtos.ItemizedDisposalDto;
import com.takakim.investtracker.api.ApiDtos.ItemizedDividendDto;
import com.takakim.investtracker.api.ApiDtos.TaxLossHarvestOpportunityDto;
import com.takakim.investtracker.api.ApiDtos.TaxReportResponse;
import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AccountStatus;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.TaxRegime;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.analytics.AllocationItem;
import com.takakim.investtracker.service.analytics.AnalyticsEngine;
import com.takakim.investtracker.service.analytics.CashFlowAnalyticsService;
import com.takakim.investtracker.service.analytics.HoldingExposure;
import com.takakim.investtracker.service.analytics.PortfolioAnalytics;
import com.takakim.investtracker.service.performance.PerformanceEngine;
import com.takakim.investtracker.service.performance.PerformanceResult;
import com.takakim.investtracker.service.tax.TaxAllowanceService;
import com.takakim.investtracker.service.tax.TaxYearPeriod;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExecutiveSummaryPdfService {

    private static final Logger log = LoggerFactory.getLogger(ExecutiveSummaryPdfService.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final AnalyticsEngine analyticsEngine;
    private final PerformanceEngine performanceEngine;
    private final CashFlowAnalyticsService cashFlowAnalyticsService;
    private final TaxAllowanceService taxAllowanceService;

    public ExecutiveSummaryPdfService(
            PortfolioRepository portfolioRepository,
            AccountRepository accountRepository,
            AnalyticsEngine analyticsEngine,
            PerformanceEngine performanceEngine,
            CashFlowAnalyticsService cashFlowAnalyticsService,
            TaxAllowanceService taxAllowanceService
    ) {
        this.portfolioRepository = portfolioRepository;
        this.accountRepository = accountRepository;
        this.analyticsEngine = analyticsEngine;
        this.performanceEngine = performanceEngine;
        this.cashFlowAnalyticsService = cashFlowAnalyticsService;
        this.taxAllowanceService = taxAllowanceService;
    }

    public byte[] generateExecutiveSummaryPdf(UUID portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));

        String baseCurrency = portfolio.getBaseCurrency().code();
        Instant now = Instant.now();

        // 1. Gather analytics and performance data
        PortfolioAnalytics analytics = analyticsEngine.calculate(portfolioId, now);
        PerformanceResult performance = performanceEngine.calculate(portfolioId);
        CashFlowAnalyticsResponse cashFlow = cashFlowAnalyticsService.calculateCashFlows(portfolioId, "ALL", "MONTH");
        List<Account> accounts = accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE);

        // 2. Build PDF Document
        try (PDDocument document = new PDDocument()) {
            PdfReportWriter writer = new PdfReportWriter(document, "EXECUTIVE SUMMARY REPORT", portfolio.getName());

            // --- Header Block ---
            writer.writeHeader(portfolio.getName(), "Comprehensive Portfolio Valuation & Performance Audit",
                    "Base Currency: " + baseCurrency + " • Accounting: " + portfolio.getCostBasisMethod() + " / " + portfolio.getReturnMethod());

            // --- Section: Key Metrics Overview ---
            writer.writeSectionTitle("Portfolio Performance & Wealth Metrics");

            BigDecimal netInvested = performance.totalNetDeposits();
            BigDecimal totalValue = analytics.totalCurrentValue();
            BigDecimal totalGain = analytics.totalUnrealizedGainLoss().add(performance.totalRealizedGainLoss());
            BigDecimal twrPct = performance.twrReturn() != null ? performance.twrReturn().multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
            BigDecimal annTwrPct = performance.twrAnnualized() != null ? performance.twrAnnualized().multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;

            writer.writeKpiCards(List.of(
                    new KpiCard("Total Portfolio Value", formatMoney(totalValue, baseCurrency), "Cash: " + formatMoney(analytics.totalCashValue(), baseCurrency)),
                    new KpiCard("Net Capital Invested", formatMoney(netInvested, baseCurrency), "Contributions - Withdrawals"),
                    new KpiCard("Total Cumulative Return", formatMoney(totalGain, baseCurrency), formatPercent(twrPct) + " TWR (" + formatPercent(annTwrPct) + " Ann.)")
            ));

            writer.writeKpiCards(List.of(
                    new KpiCard("Unrealized Gain / Loss", formatMoney(analytics.totalUnrealizedGainLoss(), baseCurrency), formatPercent(analytics.totalUnrealizedReturnPercentage())),
                    new KpiCard("Realized Capital Gain", formatMoney(performance.totalRealizedGainLoss(), baseCurrency), "Closed lots across accounts"),
                    new KpiCard("Dividends Collected", formatMoney(performance.totalDividendIncome(), baseCurrency), "Gross received distributions")
            ));

            if (cashFlow != null && cashFlow.summary() != null) {
                var cfs = cashFlow.summary();
                writer.writeKpiCards(List.of(
                        new KpiCard("Capital vs. Market Split",
                                String.format("%.1f%% / %.1f%%", cfs.capitalContributionsPercentage(), cfs.marketGrowthPercentage()),
                                "Invested Capital vs. Growth"),
                        new KpiCard("Avg. Monthly Contribution", formatMoney(cfs.avgMonthlyContribution(), baseCurrency),
                                cfs.activeContributionMonths() + " active contribution months"),
                        new KpiCard("Net Internal Income", formatMoney(cfs.totalDividends().add(cfs.totalInterest()).subtract(cfs.totalFees()), baseCurrency),
                                "Dividends + Interest - Fees")
                ));
            }

            // --- Section: Asset Allocation & Currency Exposure ---
            writer.writeSectionTitle("Asset Allocation & Currency Distribution");
            writer.writeAllocationTwoColumn(analytics.byAssetClass(), analytics.byCurrency(), baseCurrency);

            // --- Section: Holdings Ledger ---
            writer.writeSectionTitle("Active Holdings Matrix (" + analytics.topHoldings().size() + " Positions)");
            List<String[]> holdingsRows = new ArrayList<>();
            for (HoldingExposure h : analytics.topHoldings()) {
                String ticker = h.ticker() != null ? h.ticker() : "—";
                String priceStr = formatMoney(h.currentPrice(), baseCurrency);
                String valueStr = formatMoney(h.marketValue(), baseCurrency);
                String gainStr = formatMoney(h.unrealizedGainLoss(), baseCurrency);
                String weightStr = String.format("%.2f%%", h.weightPercentage().multiply(BigDecimal.valueOf(100)));

                holdingsRows.add(new String[]{
                        sanitizeText(h.instrumentName()),
                        ticker,
                        h.assetClass().name(),
                        h.quantity().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        priceStr,
                        valueStr,
                        gainStr,
                        weightStr
                });
            }
            writer.writeTable(
                    new String[]{"Instrument", "Ticker", "Class", "Quantity", "Price", "Market Value", "Gain/Loss", "Weight"},
                    new float[]{135, 45, 45, 45, 55, 65, 65, 40},
                    new int[]{0, 0, 0, 1, 1, 1, 1, 1},
                    holdingsRows
            );

            // --- Section: Cash Balances by Account ---
            writer.writeSectionTitle("Custodian & Broker Cash Accounts");
            List<String[]> accountRows = new ArrayList<>();
            java.util.Map<UUID, Account> accountMap = accounts.stream()
                    .collect(java.util.stream.Collectors.toMap(Account::getId, a -> a));

            if (cashFlow != null && cashFlow.accountBreakdown() != null && !cashFlow.accountBreakdown().isEmpty()) {
                for (var accBreakdown : cashFlow.accountBreakdown()) {
                    Account acc = accountMap.get(accBreakdown.accountId());
                    String taxTreatment = acc != null ? acc.getTaxTreatment().name() : "TAXABLE";
                    String nativeBal = formatMoney(accBreakdown.currentCashBalance(), accBreakdown.accountCurrency());
                    accountRows.add(new String[]{
                            sanitizeText(accBreakdown.accountName()),
                            sanitizeText(accBreakdown.brokerName()),
                            accBreakdown.accountCurrency(),
                            taxTreatment,
                            nativeBal
                    });
                }
            } else {
                for (Account acc : accounts) {
                    accountRows.add(new String[]{
                            sanitizeText(acc.getName()),
                            sanitizeText(acc.getBrokerName()),
                            acc.getAccountCurrency().code(),
                            acc.getTaxTreatment().name(),
                            formatMoney(BigDecimal.ZERO, acc.getAccountCurrency().code())
                    });
                }
            }
            writer.writeTable(
                    new String[]{"Account Name", "Broker / Custodian", "Currency", "Tax Treatment", "Cash Balance"},
                    new float[]{150, 120, 55, 90, 80},
                    new int[]{0, 0, 0, 0, 1},
                    accountRows
            );

            // --- Footer / Sign-off ---
            writer.finish();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            log.info("Generated Executive Summary PDF for portfolio {} ({} bytes)", portfolioId, baos.size());
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate Executive Summary PDF for portfolio {}", portfolioId, e);
            throw new IllegalStateException("Error generating Executive Summary PDF: " + e.getMessage(), e);
        }
    }

    public byte[] generateTaxReportPdf(UUID portfolioId, String taxYear, TaxRegime regime) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));

        TaxRegime safeRegime = regime != null ? regime : TaxRegime.UK_HMRC;
        TaxReportResponse taxReport = taxAllowanceService.generateTaxReport(portfolioId, taxYear, safeRegime);
        String baseCurrency = portfolio.getBaseCurrency().code();

        try (PDDocument document = new PDDocument()) {
            PdfReportWriter writer = new PdfReportWriter(document, "TAX YEAR AUDIT STATEMENT", portfolio.getName());

            // --- Header Block ---
            writer.writeHeader(portfolio.getName(), "Capital Gains Tax & Dividend Allowance Audit Statement",
                    "Tax Year: " + taxReport.taxYear() + " (" + safeRegime + ") • Period: "
                            + DATE_FORMATTER.format(taxReport.periodStart()) + " to " + DATE_FORMATTER.format(taxReport.periodEnd())
                            + " • Base Currency: " + baseCurrency);

            // --- Section: CGT Allowance Summary ---
            writer.writeSectionTitle("Capital Gains Tax (CGT) Allowance Utilization");
            var cgt = taxReport.capitalGains();
            writer.writeKpiCards(List.of(
                    new KpiCard("Total Disposal Proceeds", formatMoney(cgt.totalDisposalProceeds(), baseCurrency), cgt.totalDisposalsCount() + " realized disposals"),
                    new KpiCard("Total Allowable Cost", formatMoney(cgt.totalDisposalCostBasis(), baseCurrency), "Acquisition costs & fees"),
                    new KpiCard("Net Realized Gain / Loss", formatMoney(cgt.netRealizedGainLoss(), baseCurrency),
                            "Gains: " + formatMoney(cgt.grossRealizedGains(), baseCurrency) + " • Losses: " + formatMoney(cgt.grossRealizedLosses(), baseCurrency))
            ));
            writer.writeKpiCards(List.of(
                    new KpiCard("Annual CGT Exemption", formatMoney(cgt.annualExemptAmount(), baseCurrency),
                            "Used: " + formatMoney(cgt.allowanceUsed(), baseCurrency) + " • Left: " + formatMoney(cgt.allowanceRemaining(), baseCurrency)),
                    new KpiCard("Loss Carryforward Applied", formatMoney(cgt.lossCarryforwardApplied(), baseCurrency), "Offset from prior tax years"),
                    new KpiCard("Net Taxable Capital Gain", formatMoney(cgt.taxableCapitalGain(), baseCurrency),
                            "Est. Tax: " + formatMoney(cgt.estimatedTaxBasicRate(), baseCurrency) + " (Basic) / " + formatMoney(cgt.estimatedTaxHigherRate(), baseCurrency) + " (Higher)")
            ));

            // --- Section: Dividend Allowance Summary ---
            writer.writeSectionTitle("Dividend Income & Statutory Exemption");
            var div = taxReport.dividendIncome();
            writer.writeKpiCards(List.of(
                    new KpiCard("Gross Dividends Received", formatMoney(div.totalGrossDividends(), baseCurrency), div.totalDividendsCount() + " payment events"),
                    new KpiCard("Dividend Allowance", formatMoney(div.annualDividendAllowance(), baseCurrency),
                            "Used: " + formatMoney(div.allowanceUsed(), baseCurrency) + " • Remaining: " + formatMoney(div.allowanceRemaining(), baseCurrency)),
                    new KpiCard("Taxable Dividend Income", formatMoney(div.taxableDividendIncome(), baseCurrency),
                            "Est. Tax: " + formatMoney(div.estimatedTaxBasicRate(), baseCurrency) + " (Basic) / " + formatMoney(div.estimatedTaxHigherRate(), baseCurrency) + " (Higher)")
            ));

            // --- Section: Tax-Sheltered Growth Shield ---
            var sheltered = taxReport.shelteredSummary();
            if (sheltered != null && (sheltered.shelteredRealizedGains().compareTo(BigDecimal.ZERO) > 0
                    || sheltered.shelteredGrossDividends().compareTo(BigDecimal.ZERO) > 0)) {
                writer.writeSectionTitle("Tax-Sheltered Shield Summary (ISA / SIPP Accounts)");
                writer.writeKpiCards(List.of(
                        new KpiCard("Protected Realized Gains", formatMoney(sheltered.shelteredRealizedGains(), baseCurrency), "100% exempt from Capital Gains Tax"),
                        new KpiCard("Protected Dividends", formatMoney(sheltered.shelteredGrossDividends(), baseCurrency), "100% exempt from Dividend Tax"),
                        new KpiCard("Total Tax Saved", formatMoney(sheltered.totalEstimatedTaxSaved(), baseCurrency),
                                "CGT Saved: " + formatMoney(sheltered.estimatedCapitalGainsTaxSaved(), baseCurrency) + " • Div: " + formatMoney(sheltered.estimatedDividendTaxSaved(), baseCurrency))
                ));
            }

            // --- Section: Itemized Taxable Disposals ---
            writer.writeSectionTitle("Itemized Realized Disposals (" + taxReport.disposals().size() + " Transactions)");
            if (taxReport.disposals().isEmpty()) {
                writer.writeParagraph("No realized disposals in taxable accounts occurred during this tax year period.");
            } else {
                List<String[]> disposalRows = new ArrayList<>();
                for (ItemizedDisposalDto d : taxReport.disposals()) {
                    String date = DATE_FORMATTER.format(d.disposalDate());
                    String gainStr = (d.realizedGainLossBase().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
                            + formatMoney(d.realizedGainLossBase(), baseCurrency);
                    disposalRows.add(new String[]{
                            date,
                            sanitizeText(d.accountName()),
                            sanitizeText(d.instrumentName()),
                            d.ticker() != null ? d.ticker() : "—",
                            d.quantity().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                            formatMoney(d.proceedsBase(), baseCurrency),
                            formatMoney(d.costBasisBase(), baseCurrency),
                            gainStr
                    });
                }
                writer.writeTable(
                        new String[]{"Date", "Account", "Instrument", "Ticker", "Qty", "Proceeds", "Cost Basis", "Gain / Loss"},
                        new float[]{55, 85, 115, 45, 40, 50, 50, 55},
                        new int[]{0, 0, 0, 0, 1, 1, 1, 1},
                        disposalRows
                );
            }

            // --- Section: Itemized Taxable Dividends ---
            writer.writeSectionTitle("Itemized Dividend Distributions (" + taxReport.dividends().size() + " Payments)");
            if (taxReport.dividends().isEmpty()) {
                writer.writeParagraph("No dividend distributions in taxable accounts were received during this tax year period.");
            } else {
                List<String[]> divRows = new ArrayList<>();
                for (ItemizedDividendDto d : taxReport.dividends()) {
                    String date = DATE_FORMATTER.format(d.paymentDate());
                    divRows.add(new String[]{
                            date,
                            sanitizeText(d.accountName()),
                            sanitizeText(d.instrumentName()),
                            d.ticker() != null ? d.ticker() : "—",
                            formatMoney(d.grossAmountBase(), baseCurrency),
                            formatMoney(d.withholdingTaxBase(), baseCurrency),
                            formatMoney(d.netAmountBase(), baseCurrency)
                    });
                }
                writer.writeTable(
                        new String[]{"Date", "Account", "Instrument", "Ticker", "Gross", "Withholding Tax", "Net Amount"},
                        new float[]{65, 100, 130, 45, 55, 55, 45},
                        new int[]{0, 0, 0, 0, 1, 1, 1},
                        divRows
                );
            }

            // --- Section: Tax Loss Harvesting Candidates ---
            if (!taxReport.lossHarvestOpportunities().isEmpty()) {
                writer.writeSectionTitle("Tax-Loss Harvesting Opportunities (" + taxReport.lossHarvestOpportunities().size() + " Positions)");
                List<String[]> lossRows = new ArrayList<>();
                for (TaxLossHarvestOpportunityDto opp : taxReport.lossHarvestOpportunities()) {
                    lossRows.add(new String[]{
                            sanitizeText(opp.accountName()),
                            sanitizeText(opp.instrumentName()),
                            opp.ticker() != null ? opp.ticker() : "—",
                            opp.quantity().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                            formatMoney(opp.currentMarketValueBase(), baseCurrency),
                            formatMoney(opp.totalCostBasisBase(), baseCurrency),
                            "-" + formatMoney(opp.unrealizedLossBase(), baseCurrency)
                    });
                }
                writer.writeTable(
                        new String[]{"Account", "Instrument", "Ticker", "Quantity", "Market Value", "Cost Basis", "Unrealized Loss"},
                        new float[]{100, 130, 45, 50, 60, 60, 50},
                        new int[]{0, 0, 0, 1, 1, 1, 1},
                        lossRows
                );
            }

            writer.finish();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            log.info("Generated Tax Report PDF for portfolio {} tax year {} ({} bytes)", portfolioId, taxYear, baos.size());
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate Tax Report PDF for portfolio {}", portfolioId, e);
            throw new IllegalStateException("Error generating Tax Report PDF: " + e.getMessage(), e);
        }
    }

    public static String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) return currency + " 0.00";
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        return currency + " " + String.format("%,.2f", scaled.doubleValue());
    }

    public static String formatPercent(BigDecimal percent) {
        if (percent == null) return "0.00%";
        BigDecimal scaled = percent.setScale(2, RoundingMode.HALF_UP);
        return (scaled.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + String.format("%.2f%%", scaled.doubleValue());
    }

    public static String sanitizeText(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            // Keep ASCII printable characters only for standard PDF fonts
            if (c >= 32 && c <= 126) {
                sb.append(c);
            } else if (c == '\t' || c == '\n' || c == '\r') {
                sb.append(' ');
            } else {
                sb.append('?');
            }
        }
        return sb.toString().trim();
    }

    public record KpiCard(String label, String mainValue, String subValue) {}

    /**
     * Helper utility managing coordinate drawing, automatic page wrapping,
     * header bars, table layouts, and footers across multi-page PDF documents.
     */
    public static class PdfReportWriter {
        private final PDDocument document;
        private final String reportType;
        private final String portfolioName;

        private final PDFont fontRegular;
        private final PDFont fontBold;
        private final PDFont fontOblique;

        private PDPage currentPage;
        private PDPageContentStream cs;
        private float y;
        private int pageNumber = 0;

        private static final float PAGE_WIDTH = 595.27f; // A4
        private static final float PAGE_HEIGHT = 841.89f; // A4
        private static final float MARGIN_LEFT = 40f;
        private static final float MARGIN_RIGHT = 40f;
        private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT; // 515.27f
        private static final float FOOTER_Y = 30f;
        private static final float MIN_Y = 55f;

        public PdfReportWriter(PDDocument document, String reportType, String portfolioName) {
            this.document = document;
            this.reportType = reportType;
            this.portfolioName = portfolioName;

            this.fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            this.fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            this.fontOblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            newPage();
        }

        private void newPage() {
            try {
                if (cs != null) {
                    drawFooter();
                    cs.close();
                }
                currentPage = new PDPage(PDRectangle.A4);
                document.addPage(currentPage);
                cs = new PDPageContentStream(document, currentPage);
                pageNumber++;
                y = PAGE_HEIGHT - 45f;

                if (pageNumber > 1) {
                    drawRunningHeader();
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to initialize PDF page", e);
            }
        }

        private void ensureSpace(float requiredHeight) {
            if (y - requiredHeight < MIN_Y) {
                newPage();
            }
        }

        private void setNonStrokingRgb(int r, int g, int b) throws IOException {
            cs.setNonStrokingColor(r / 255f, g / 255f, b / 255f);
        }

        private void setStrokingRgb(int r, int g, int b) throws IOException {
            cs.setStrokingColor(r / 255f, g / 255f, b / 255f);
        }

        private void drawRunningHeader() throws IOException {
            cs.setFont(fontRegular, 8);
            setNonStrokingRgb(100, 116, 139);
            cs.beginText();
            cs.newLineAtOffset(MARGIN_LEFT, PAGE_HEIGHT - 30);
            cs.showText("INVEST-TRACKER • " + reportType + " • " + sanitizeText(portfolioName));
            cs.endText();

            // Running header line
            setStrokingRgb(226, 232, 240);
            cs.setLineWidth(0.5f);
            cs.moveTo(MARGIN_LEFT, PAGE_HEIGHT - 35);
            cs.lineTo(MARGIN_LEFT + CONTENT_WIDTH, PAGE_HEIGHT - 35);
            cs.stroke();

            y = PAGE_HEIGHT - 55f;
        }

        private void drawFooter() throws IOException {
            setStrokingRgb(226, 232, 240);
            cs.setLineWidth(0.5f);
            cs.moveTo(MARGIN_LEFT, FOOTER_Y + 12);
            cs.lineTo(MARGIN_LEFT + CONTENT_WIDTH, FOOTER_Y + 12);
            cs.stroke();

            cs.setFont(fontRegular, 8);
            setNonStrokingRgb(100, 116, 139);
            cs.beginText();
            cs.newLineAtOffset(MARGIN_LEFT, FOOTER_Y);
            cs.showText("Generated by Invest-Tracker • Confidential Financial Record • " + DATE_TIME_FORMATTER.format(Instant.now()));
            cs.endText();

            String pageText = "Page " + pageNumber;
            float pageTextWidth = fontRegular.getStringWidth(pageText) / 1000f * 8f;
            cs.beginText();
            cs.newLineAtOffset(MARGIN_LEFT + CONTENT_WIDTH - pageTextWidth, FOOTER_Y);
            cs.showText(pageText);
            cs.endText();
        }

        public void writeHeader(String title, String subtitle, String metadata) throws IOException {
            // Navy top banner bar
            setNonStrokingRgb(15, 23, 42); // slate-900
            cs.addRect(MARGIN_LEFT, y - 6, CONTENT_WIDTH, 4);
            cs.fill();
            y -= 18;

            cs.setFont(fontBold, 18);
            setNonStrokingRgb(15, 23, 42);
            cs.beginText();
            cs.newLineAtOffset(MARGIN_LEFT, y);
            cs.showText(sanitizeText(title));
            cs.endText();
            y -= 16;

            cs.setFont(fontRegular, 11);
            setNonStrokingRgb(71, 85, 105);
            cs.beginText();
            cs.newLineAtOffset(MARGIN_LEFT, y);
            cs.showText(sanitizeText(subtitle));
            cs.endText();
            y -= 14;

            cs.setFont(fontOblique, 9);
            setNonStrokingRgb(100, 116, 139);
            cs.beginText();
            cs.newLineAtOffset(MARGIN_LEFT, y);
            cs.showText(sanitizeText(metadata));
            cs.endText();
            y -= 15;

            // Divider rule
            setStrokingRgb(226, 232, 240);
            cs.setLineWidth(1f);
            cs.moveTo(MARGIN_LEFT, y);
            cs.lineTo(MARGIN_LEFT + CONTENT_WIDTH, y);
            cs.stroke();
            y -= 18;
        }

        public void writeSectionTitle(String title) throws IOException {
            ensureSpace(35);
            y -= 6;
            cs.setFont(fontBold, 12);
            setNonStrokingRgb(15, 23, 42);
            cs.beginText();
            cs.newLineAtOffset(MARGIN_LEFT, y);
            cs.showText(sanitizeText(title));
            cs.endText();
            y -= 8;

            setStrokingRgb(30, 64, 175); // Blue underline
            cs.setLineWidth(1.5f);
            cs.moveTo(MARGIN_LEFT, y);
            cs.lineTo(MARGIN_LEFT + 40, y);
            cs.stroke();
            y -= 12;
        }

        public void writeParagraph(String text) throws IOException {
            ensureSpace(20);
            cs.setFont(fontRegular, 9);
            setNonStrokingRgb(71, 85, 105);
            cs.beginText();
            cs.newLineAtOffset(MARGIN_LEFT, y);
            cs.showText(sanitizeText(text));
            cs.endText();
            y -= 14;
        }

        public void writeKpiCards(List<KpiCard> cards) throws IOException {
            if (cards == null || cards.isEmpty()) return;
            ensureSpace(55);

            int n = cards.size();
            float gap = 8f;
            float cardWidth = (CONTENT_WIDTH - (gap * (n - 1))) / n;
            float cardHeight = 44f;

            for (int i = 0; i < n; i++) {
                KpiCard card = cards.get(i);
                float x = MARGIN_LEFT + (i * (cardWidth + gap));

                // Card background
                setNonStrokingRgb(248, 250, 252);
                cs.addRect(x, y - cardHeight, cardWidth, cardHeight);
                cs.fill();

                // Card border
                setStrokingRgb(226, 232, 240);
                cs.setLineWidth(0.8f);
                cs.addRect(x, y - cardHeight, cardWidth, cardHeight);
                cs.stroke();

                // Card Label
                cs.setFont(fontRegular, 7.5f);
                setNonStrokingRgb(100, 116, 139);
                cs.beginText();
                cs.newLineAtOffset(x + 8, y - 12);
                cs.showText(sanitizeText(card.label));
                cs.endText();

                // Card Main Value
                cs.setFont(fontBold, 11.5f);
                setNonStrokingRgb(15, 23, 42);
                cs.beginText();
                cs.newLineAtOffset(x + 8, y - 26);
                cs.showText(sanitizeText(card.mainValue));
                cs.endText();

                // Card Sub Value
                if (card.subValue != null && !card.subValue.isBlank()) {
                    cs.setFont(fontRegular, 7f);
                    setNonStrokingRgb(71, 85, 105);
                    cs.beginText();
                    cs.newLineAtOffset(x + 8, y - 37);
                    cs.showText(sanitizeText(card.subValue));
                    cs.endText();
                }
            }

            y -= (cardHeight + 10);
        }

        public void writeAllocationTwoColumn(
                List<AllocationItem> assetClasses,
                List<AllocationItem> currencies,
                String baseCurrency
        ) throws IOException {
            ensureSpace(80);

            float colWidth = (CONTENT_WIDTH - 15) / 2;
            float col1X = MARGIN_LEFT;
            float col2X = MARGIN_LEFT + colWidth + 15;
            float startY = y;

            // Left Col: Asset Class Table
            y = startY;
            writeMiniTable(col1X, colWidth, "Asset Class", assetClasses, baseCurrency);
            float endY1 = y;

            // Right Col: Currency Exposure Table
            y = startY;
            writeMiniTable(col2X, colWidth, "Currency Exposure", currencies, baseCurrency);
            float endY2 = y;

            y = Math.min(endY1, endY2) - 8;
        }

        private void writeMiniTable(float x, float width, String title, List<AllocationItem> items, String baseCurrency) throws IOException {
            cs.setFont(fontBold, 9);
            setNonStrokingRgb(15, 23, 42);
            cs.beginText();
            cs.newLineAtOffset(x, y);
            cs.showText(title);
            cs.endText();
            y -= 12;

            // Header row
            setNonStrokingRgb(241, 245, 249);
            cs.addRect(x, y - 12, width, 14);
            cs.fill();

            cs.setFont(fontBold, 7.5f);
            setNonStrokingRgb(71, 85, 105);
            cs.beginText();
            cs.newLineAtOffset(x + 4, y - 9);
            cs.showText("Category");
            cs.endText();

            cs.beginText();
            cs.newLineAtOffset(x + width - 75, y - 9);
            cs.showText("Value (" + baseCurrency + ")");
            cs.endText();

            cs.beginText();
            cs.newLineAtOffset(x + width - 30, y - 9);
            cs.showText("Share");
            cs.endText();
            y -= 14;

            if (items != null) {
                for (AllocationItem item : items) {
                    setStrokingRgb(241, 245, 249);
                    cs.setLineWidth(0.5f);
                    cs.moveTo(x, y - 10);
                    cs.lineTo(x + width, y - 10);
                    cs.stroke();

                    cs.setFont(fontRegular, 7.5f);
                    setNonStrokingRgb(15, 23, 42);
                    cs.beginText();
                    cs.newLineAtOffset(x + 4, y - 7);
                    cs.showText(sanitizeText(item.category()));
                    cs.endText();

                    cs.beginText();
                    cs.newLineAtOffset(x + width - 75, y - 7);
                    cs.showText(formatMoney(item.marketValue(), ""));
                    cs.endText();

                    cs.setFont(fontBold, 7.5f);
                    cs.beginText();
                    cs.newLineAtOffset(x + width - 30, y - 7);
                    cs.showText(String.format("%.1f%%", item.percentage()));
                    cs.endText();
                    y -= 12;
                }
            }
        }

        public void writeTable(
                String[] headers,
                float[] colWidths,
                int[] alignments, // 0 = left, 1 = right
                List<String[]> rows
        ) throws IOException {
            if (headers == null || colWidths == null || rows == null) return;
            float rowHeight = 14f;
            float headerHeight = 16f;

            ensureSpace(headerHeight + (rowHeight * Math.min(3, rows.size())));

            // Header Background
            setNonStrokingRgb(241, 245, 249);
            cs.addRect(MARGIN_LEFT, y - headerHeight, CONTENT_WIDTH, headerHeight);
            cs.fill();

            // Header Text
            cs.setFont(fontBold, 8);
            setNonStrokingRgb(30, 41, 59);

            float curX = MARGIN_LEFT;
            for (int i = 0; i < headers.length; i++) {
                float w = colWidths[i];
                String h = headers[i];
                cs.beginText();
                if (alignments[i] == 1) {
                    float tw = fontBold.getStringWidth(h) / 1000f * 8f;
                    cs.newLineAtOffset(curX + w - tw - 4, y - 11);
                } else {
                    cs.newLineAtOffset(curX + 4, y - 11);
                }
                cs.showText(h);
                cs.endText();
                curX += w;
            }
            y -= headerHeight;

            // Rows
            boolean alternate = false;
            for (String[] row : rows) {
                ensureSpace(rowHeight);

                if (alternate) {
                    setNonStrokingRgb(248, 250, 252);
                    cs.addRect(MARGIN_LEFT, y - rowHeight, CONTENT_WIDTH, rowHeight);
                    cs.fill();
                }

                // Row bottom border
                setStrokingRgb(241, 245, 249);
                cs.setLineWidth(0.5f);
                cs.moveTo(MARGIN_LEFT, y - rowHeight);
                cs.lineTo(MARGIN_LEFT + CONTENT_WIDTH, y - rowHeight);
                cs.stroke();

                cs.setFont(fontRegular, 7.5f);
                setNonStrokingRgb(15, 23, 42);

                curX = MARGIN_LEFT;
                for (int i = 0; i < row.length && i < headers.length; i++) {
                    float w = colWidths[i];
                    String val = row[i] != null ? row[i] : "";

                    // Highlight positive/negative gain values
                    if (val.startsWith("+")) {
                        setNonStrokingRgb(22, 101, 52); // green
                    } else if (val.startsWith("-")) {
                        setNonStrokingRgb(153, 27, 27); // red
                    } else {
                        setNonStrokingRgb(15, 23, 42);
                    }

                    cs.beginText();
                    if (alignments[i] == 1) {
                        float tw = fontRegular.getStringWidth(val) / 1000f * 7.5f;
                        cs.newLineAtOffset(curX + w - tw - 4, y - 10);
                    } else {
                        cs.newLineAtOffset(curX + 4, y - 10);
                    }
                    cs.showText(sanitizeText(val));
                    cs.endText();
                    curX += w;
                }

                y -= rowHeight;
                alternate = !alternate;
            }

            y -= 8;
        }

        public void finish() throws IOException {
            if (cs != null) {
                drawFooter();
                cs.close();
            }
        }
    }
}
