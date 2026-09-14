package com.takakim.investtracker.service.tax;

import com.takakim.investtracker.api.ApiDtos.*;
import com.takakim.investtracker.domain.*;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.repository.*;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.currency.FxRateService;
import com.takakim.investtracker.service.market.MarketDataService;
import com.takakim.investtracker.service.market.PriceQuote;
import com.takakim.investtracker.service.position.LotDisposal;
import com.takakim.investtracker.service.position.PositionCalculationResult;
import com.takakim.investtracker.service.position.PositionEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaxAllowanceService {

    private static final Logger log = LoggerFactory.getLogger(TaxAllowanceService.class);
    private static final int MONEY_SCALE = 4;
    private static final int QUANTITY_SCALE = 8;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final PortfolioTaxSettingsRepository taxSettingsRepository;
    private final PositionEngine positionEngine;
    private final FxRateService fxRateService;
    private final MarketDataService marketDataService;

    public TaxAllowanceService(
            PortfolioRepository portfolioRepository,
            AccountRepository accountRepository,
            PositionRepository positionRepository,
            TransactionRepository transactionRepository,
            PortfolioTaxSettingsRepository taxSettingsRepository,
            PositionEngine positionEngine,
            FxRateService fxRateService,
            MarketDataService marketDataService
    ) {
        this.portfolioRepository = portfolioRepository;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.taxSettingsRepository = taxSettingsRepository;
        this.positionEngine = positionEngine;
        this.fxRateService = fxRateService;
        this.marketDataService = marketDataService;
    }

    @Transactional(readOnly = true)
    public TaxReportResponse generateTaxReport(UUID portfolioId, String taxYear, TaxRegime regime) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));

        TaxRegime safeRegime = regime != null ? regime : TaxRegime.UK_HMRC;
        TaxYearPeriod period = TaxYearPeriod.of(taxYear, safeRegime);
        String baseCurrency = portfolio.getBaseCurrency().code();

        // Load custom tax settings if configured
        Optional<PortfolioTaxSettings> settingsOpt = taxSettingsRepository.findByPortfolioIdAndTaxYear(portfolioId, period.label());

        BigDecimal cgtAllowance = settingsOpt.map(PortfolioTaxSettings::getCgtAllowance)
                .filter(Objects::nonNull)
                .orElse(period.defaultCgtAllowance())
                .setScale(MONEY_SCALE, ROUNDING);

        BigDecimal dividendAllowance = settingsOpt.map(PortfolioTaxSettings::getDividendAllowance)
                .filter(Objects::nonNull)
                .orElse(period.defaultDividendAllowance())
                .setScale(MONEY_SCALE, ROUNDING);

        BigDecimal lossCarryforward = settingsOpt.map(PortfolioTaxSettings::getLossCarryforward)
                .filter(Objects::nonNull)
                .orElse(BigDecimal.ZERO)
                .setScale(MONEY_SCALE, ROUNDING);

        List<Account> accounts = accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE);

        List<ItemizedDisposalDto> taxableDisposals = new ArrayList<>();
        List<ItemizedDividendDto> taxableDividends = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        BigDecimal totalDisposalProceeds = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal totalDisposalCostBasis = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal grossRealizedGains = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal grossRealizedLosses = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);

        BigDecimal shelteredRealizedGains = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal shelteredRealizedLosses = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal shelteredGrossDividends = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);

        BigDecimal totalGrossDividends = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal totalWithholdingTax = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal netDividendsReceived = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);

        // 1. Process Disposals and Realized Capital Gains across accounts
        for (Account acc : accounts) {
            boolean isSheltered = acc.isTaxExempt();
            List<Transaction> accountTxs = transactionRepository.findByAccountIdOrderByTradeDateDesc(acc.getId());

            // Group by instrument to run PositionEngine
            Map<UUID, List<Transaction>> txsByInstrument = accountTxs.stream()
                    .filter(t -> t.getInstrument() != null)
                    .collect(Collectors.groupingBy(t -> t.getInstrument().getId()));

            for (Map.Entry<UUID, List<Transaction>> entry : txsByInstrument.entrySet()) {
                List<Transaction> instTxs = entry.getValue().stream()
                        .sorted(Comparator.comparing(Transaction::getTradeDate).thenComparing(Transaction::getId))
                        .toList();

                Instrument inst = instTxs.get(0).getInstrument();
                PositionCalculationResult calc = positionEngine.calculate(acc, inst, instTxs, portfolio.getCostBasisMethod());

                for (LotDisposal disposal : calc.disposals()) {
                    if (!period.contains(disposal.disposalDate())) {
                        continue;
                    }

                    String nativeCurrency = disposal.currency();

                    BigDecimal proceedsBase = convertCurrency(disposal.proceeds(), nativeCurrency, baseCurrency, disposal.disposalDate(), warnings);
                    BigDecimal costBasisBase = convertCurrency(disposal.disposedCostBasis(), nativeCurrency, baseCurrency, disposal.disposalDate(), warnings);
                    BigDecimal gainLossBase = convertCurrency(disposal.realizedGainLoss(), nativeCurrency, baseCurrency, disposal.disposalDate(), warnings);

                    if (isSheltered) {
                        if (gainLossBase.compareTo(BigDecimal.ZERO) >= 0) {
                            shelteredRealizedGains = shelteredRealizedGains.add(gainLossBase);
                        } else {
                            shelteredRealizedLosses = shelteredRealizedLosses.add(gainLossBase.abs());
                        }
                    } else {
                        taxableDisposals.add(new ItemizedDisposalDto(
                                disposal.disposalTransactionId(),
                                acc.getId(),
                                acc.getName(),
                                acc.getTaxTreatment(),
                                inst.getId(),
                                inst.getName(),
                                inst.getTicker(),
                                disposal.disposalDate(),
                                disposal.disposedQuantity().setScale(QUANTITY_SCALE, ROUNDING),
                                disposal.proceeds().setScale(MONEY_SCALE, ROUNDING),
                                disposal.disposedCostBasis().setScale(MONEY_SCALE, ROUNDING),
                                nativeCurrency,
                                proceedsBase,
                                costBasisBase,
                                gainLossBase
                        ));

                        totalDisposalProceeds = totalDisposalProceeds.add(proceedsBase);
                        totalDisposalCostBasis = totalDisposalCostBasis.add(costBasisBase);

                        if (gainLossBase.compareTo(BigDecimal.ZERO) >= 0) {
                            grossRealizedGains = grossRealizedGains.add(gainLossBase);
                        } else {
                            grossRealizedLosses = grossRealizedLosses.add(gainLossBase.abs());
                        }
                    }
                }
            }

            // 2. Process Dividends in period
            List<Transaction> divTxs = transactionRepository.findByAccountIdAndTypeOrderByTradeDateDesc(acc.getId(), TransactionType.DIVIDEND);
            for (Transaction divTx : divTxs) {
                Instant payDate = divTx.getTradeDate();
                if (!period.contains(payDate)) {
                    continue;
                }

                Instrument inst = divTx.getInstrument();
                String nativeCurrency = divTx.getCurrency() != null ? divTx.getCurrency() : baseCurrency;
                BigDecimal grossNative = divTx.getGrossAmount() != null ? divTx.getGrossAmount() : (divTx.getNetAmount() != null ? divTx.getNetAmount() : BigDecimal.ZERO);
                BigDecimal taxNative = divTx.getTaxAmount() != null ? divTx.getTaxAmount() : BigDecimal.ZERO;
                BigDecimal netNative = divTx.getNetAmount() != null ? divTx.getNetAmount() : grossNative.subtract(taxNative);

                BigDecimal grossBase = convertCurrency(grossNative, nativeCurrency, baseCurrency, payDate, warnings);
                BigDecimal taxBase = convertCurrency(taxNative, nativeCurrency, baseCurrency, payDate, warnings);
                BigDecimal netBase = convertCurrency(netNative, nativeCurrency, baseCurrency, payDate, warnings);

                if (isSheltered) {
                    shelteredGrossDividends = shelteredGrossDividends.add(grossBase);
                } else {
                    taxableDividends.add(new ItemizedDividendDto(
                            divTx.getId(),
                            acc.getId(),
                            acc.getName(),
                            acc.getTaxTreatment(),
                            inst != null ? inst.getId() : null,
                            inst != null ? inst.getName() : "Cash Dividend",
                            inst != null ? inst.getTicker() : null,
                            payDate,
                            grossNative.setScale(MONEY_SCALE, ROUNDING),
                            taxNative.setScale(MONEY_SCALE, ROUNDING),
                            nativeCurrency,
                            grossBase,
                            taxBase,
                            netBase
                    ));

                    totalGrossDividends = totalGrossDividends.add(grossBase);
                    totalWithholdingTax = totalWithholdingTax.add(taxBase);
                    netDividendsReceived = netDividendsReceived.add(netBase);
                }
            }
        }

        // Sort itemized records chronologically descending
        taxableDisposals.sort(Comparator.comparing(ItemizedDisposalDto::disposalDate).reversed());
        taxableDividends.sort(Comparator.comparing(ItemizedDividendDto::paymentDate).reversed());

        // 3. Capital Gains Calculations
        BigDecimal netRealizedGainLoss = grossRealizedGains.subtract(grossRealizedLosses).setScale(MONEY_SCALE, ROUNDING);

        BigDecimal lossCarryforwardApplied = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal netTaxableGainBeforeAllowance = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);

        if (netRealizedGainLoss.compareTo(BigDecimal.ZERO) > 0) {
            if (lossCarryforward.compareTo(BigDecimal.ZERO) > 0) {
                lossCarryforwardApplied = lossCarryforward.min(netRealizedGainLoss).setScale(MONEY_SCALE, ROUNDING);
            }
            netTaxableGainBeforeAllowance = netRealizedGainLoss.subtract(lossCarryforwardApplied).setScale(MONEY_SCALE, ROUNDING);
        }

        BigDecimal allowanceUsed = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal allowanceRemaining = cgtAllowance.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal taxableCapitalGain = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);

        if (netTaxableGainBeforeAllowance.compareTo(BigDecimal.ZERO) > 0) {
            allowanceUsed = netTaxableGainBeforeAllowance.min(cgtAllowance).setScale(MONEY_SCALE, ROUNDING);
            allowanceRemaining = cgtAllowance.subtract(allowanceUsed).max(BigDecimal.ZERO).setScale(MONEY_SCALE, ROUNDING);
            taxableCapitalGain = netTaxableGainBeforeAllowance.subtract(allowanceUsed).max(BigDecimal.ZERO).setScale(MONEY_SCALE, ROUNDING);
        }

        BigDecimal cgtBasicRate = period.cgtBasicRatePercentage();
        BigDecimal cgtHigherRate = period.cgtHigherRatePercentage();
        BigDecimal estimatedCgtBasic = taxableCapitalGain.multiply(cgtBasicRate).divide(new BigDecimal("100"), MONEY_SCALE, ROUNDING);
        BigDecimal estimatedCgtHigher = taxableCapitalGain.multiply(cgtHigherRate).divide(new BigDecimal("100"), MONEY_SCALE, ROUNDING);

        CapitalGainsTaxSummaryDto cgtSummary = new CapitalGainsTaxSummaryDto(
                totalDisposalProceeds,
                totalDisposalCostBasis,
                grossRealizedGains,
                grossRealizedLosses,
                netRealizedGainLoss,
                lossCarryforwardApplied,
                netTaxableGainBeforeAllowance,
                cgtAllowance,
                allowanceUsed,
                allowanceRemaining,
                taxableCapitalGain,
                estimatedCgtBasic,
                estimatedCgtHigher,
                cgtBasicRate,
                cgtHigherRate,
                taxableDisposals.size()
        );

        // 4. Dividend Tax Calculations
        BigDecimal divAllowanceUsed = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal divAllowanceRemaining = dividendAllowance.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal taxableDividendIncome = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);

        if (totalGrossDividends.compareTo(BigDecimal.ZERO) > 0) {
            divAllowanceUsed = totalGrossDividends.min(dividendAllowance).setScale(MONEY_SCALE, ROUNDING);
            divAllowanceRemaining = dividendAllowance.subtract(divAllowanceUsed).max(BigDecimal.ZERO).setScale(MONEY_SCALE, ROUNDING);
            taxableDividendIncome = totalGrossDividends.subtract(divAllowanceUsed).max(BigDecimal.ZERO).setScale(MONEY_SCALE, ROUNDING);
        }

        BigDecimal divBasicRate = period.dividendBasicRatePercentage();
        BigDecimal divHigherRate = period.dividendHigherRatePercentage();
        BigDecimal divAdditionalRate = period.dividendAdditionalRatePercentage();

        BigDecimal estimatedDivBasic = taxableDividendIncome.multiply(divBasicRate).divide(new BigDecimal("100"), MONEY_SCALE, ROUNDING);
        BigDecimal estimatedDivHigher = taxableDividendIncome.multiply(divHigherRate).divide(new BigDecimal("100"), MONEY_SCALE, ROUNDING);
        BigDecimal estimatedDivAdditional = taxableDividendIncome.multiply(divAdditionalRate).divide(new BigDecimal("100"), MONEY_SCALE, ROUNDING);

        DividendTaxSummaryDto dividendSummary = new DividendTaxSummaryDto(
                totalGrossDividends,
                totalWithholdingTax,
                netDividendsReceived,
                dividendAllowance,
                divAllowanceUsed,
                divAllowanceRemaining,
                taxableDividendIncome,
                estimatedDivBasic,
                estimatedDivHigher,
                estimatedDivAdditional,
                divBasicRate,
                divHigherRate,
                divAdditionalRate,
                taxableDividends.size()
        );

        // 5. Tax Sheltered Savings Calculations
        BigDecimal shelteredNetGains = shelteredRealizedGains.subtract(shelteredRealizedLosses).max(BigDecimal.ZERO).setScale(MONEY_SCALE, ROUNDING);
        BigDecimal estimatedCgtSaved = shelteredNetGains.multiply(new BigDecimal("20.00")).divide(new BigDecimal("100"), MONEY_SCALE, ROUNDING);
        BigDecimal estimatedDivSaved = shelteredGrossDividends.multiply(divBasicRate).divide(new BigDecimal("100"), MONEY_SCALE, ROUNDING);
        BigDecimal totalTaxSaved = estimatedCgtSaved.add(estimatedDivSaved).setScale(MONEY_SCALE, ROUNDING);

        TaxShelteredSummaryDto shelteredSummary = new TaxShelteredSummaryDto(
                shelteredRealizedGains,
                shelteredRealizedLosses,
                shelteredGrossDividends,
                estimatedCgtSaved,
                estimatedDivSaved,
                totalTaxSaved
        );

        // 6. Tax-Loss Harvesting Candidates
        List<TaxLossHarvestOpportunityDto> harvestCandidates = identifyLossHarvestOpportunities(accounts, baseCurrency, warnings);

        return new TaxReportResponse(
                portfolioId,
                portfolio.getName(),
                baseCurrency,
                period.label(),
                safeRegime,
                period.periodStart(),
                period.periodEnd(),
                cgtSummary,
                dividendSummary,
                shelteredSummary,
                harvestCandidates,
                taxableDisposals,
                taxableDividends,
                warnings
        );
    }

    @Transactional(readOnly = true)
    public AvailableTaxYearsResponse getAvailableTaxYears(UUID portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }

        List<Account> accounts = accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE);
        Set<String> ukYears = new TreeSet<>(Comparator.reverseOrder());
        Set<String> calYears = new TreeSet<>(Comparator.reverseOrder());

        String curUk = TaxYearPeriod.currentUkTaxYearLabel();
        String curCal = TaxYearPeriod.currentCalendarYearLabel();
        ukYears.add(curUk);
        calYears.add(curCal);

        for (Account acc : accounts) {
            List<Transaction> txs = transactionRepository.findByAccountIdOrderByTradeDateDesc(acc.getId());
            for (Transaction tx : txs) {
                Instant date = tx.getTradeDate();
                if (date != null) {
                    ukYears.add(TaxYearPeriod.resolveTaxYearForInstant(date, TaxRegime.UK_HMRC));
                    calYears.add(TaxYearPeriod.resolveTaxYearForInstant(date, TaxRegime.CALENDAR_YEAR));
                }
            }
        }

        return new AvailableTaxYearsResponse(
                new ArrayList<>(ukYears),
                new ArrayList<>(calYears),
                curUk,
                curCal
        );
    }

    public TaxSettingsResponse saveTaxSettings(UUID portfolioId, TaxSettingsRequest request) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));

        String taxYear = request.taxYear() != null && !request.taxYear().isBlank()
                ? request.taxYear().trim()
                : TaxYearPeriod.currentUkTaxYearLabel();

        PortfolioTaxSettings settings = taxSettingsRepository.findByPortfolioIdAndTaxYear(portfolioId, taxYear)
                .orElseGet(() -> new PortfolioTaxSettings(
                        portfolio,
                        taxYear,
                        request.taxRegime(),
                        request.cgtAllowance(),
                        request.dividendAllowance(),
                        request.lossCarryforward(),
                        request.notes()
                ));

        if (settings.getId() != null && taxSettingsRepository.existsById(settings.getId())) {
            settings.update(
                    request.taxRegime(),
                    request.cgtAllowance(),
                    request.dividendAllowance(),
                    request.lossCarryforward(),
                    request.notes()
            );
        }

        PortfolioTaxSettings saved = taxSettingsRepository.save(settings);
        log.info("Saved custom tax settings for portfolio {} tax year {}", portfolioId, taxYear);

        return new TaxSettingsResponse(
                saved.getId(),
                portfolioId,
                saved.getTaxYear(),
                saved.getTaxRegime(),
                saved.getCgtAllowance(),
                saved.getDividendAllowance(),
                saved.getLossCarryforward(),
                saved.getNotes(),
                saved.getUpdatedAt()
        );
    }

    private List<TaxLossHarvestOpportunityDto> identifyLossHarvestOpportunities(
            List<Account> accounts, String baseCurrency, List<String> warnings
    ) {
        List<TaxLossHarvestOpportunityDto> candidates = new ArrayList<>();

        for (Account acc : accounts) {
            if (acc.isTaxExempt()) {
                continue; // Harvesting only applies to taxable accounts
            }

            List<Position> activePositions = positionRepository.findByAccountId(acc.getId()).stream()
                    .filter(p -> p.getStatus() == PositionStatus.ACTIVE && p.getQuantity() != null && p.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                    .toList();

            for (Position pos : activePositions) {
                Instrument inst = pos.getInstrument();
                BigDecimal latestPrice = null;
                String priceCurrency = inst.getCurrency().code();

                try {
                    if (marketDataService != null) {
                        PriceQuote quote = marketDataService.getLatestPrice(inst.getId(), Instant.now());
                        if (quote != null && quote.price() != null) {
                            latestPrice = quote.price();
                            if (quote.currency() != null) {
                                priceCurrency = quote.currency();
                            }
                        }
                    }
                } catch (Exception ignored) {
                }

                if (latestPrice == null) {
                    continue;
                }

                BigDecimal qty = pos.getQuantity();
                BigDecimal marketValueNative = qty.multiply(latestPrice).setScale(MONEY_SCALE, ROUNDING);
                BigDecimal marketValueBase = convertCurrency(marketValueNative, priceCurrency, baseCurrency, Instant.now(), warnings);

                BigDecimal costBasisNative = pos.getCostBasisAmount() != null ? pos.getCostBasisAmount() : BigDecimal.ZERO;
                String costCurrency = pos.getCostBasisCurrency() != null ? pos.getCostBasisCurrency() : priceCurrency;
                BigDecimal costBasisBase = convertCurrency(costBasisNative, costCurrency, baseCurrency, Instant.now(), warnings);

                if (marketValueBase.compareTo(costBasisBase) < 0) {
                    BigDecimal unrealizedLoss = costBasisBase.subtract(marketValueBase).setScale(MONEY_SCALE, ROUNDING);
                    candidates.add(new TaxLossHarvestOpportunityDto(
                            acc.getId(),
                            acc.getName(),
                            inst.getId(),
                            inst.getName(),
                            inst.getTicker(),
                            qty.setScale(QUANTITY_SCALE, ROUNDING),
                            latestPrice.setScale(MONEY_SCALE, ROUNDING),
                            priceCurrency,
                            marketValueBase,
                            costBasisBase,
                            unrealizedLoss
                    ));
                }
            }
        }

        // Sort by largest unrealized loss first
        candidates.sort(Comparator.comparing(TaxLossHarvestOpportunityDto::unrealizedLossBase).reversed());
        return candidates;
    }

    private BigDecimal convertCurrency(BigDecimal amount, String fromCurrency, String toCurrency, Instant date, List<String> warnings) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        }
        if (fromCurrency == null || toCurrency == null || fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount.setScale(MONEY_SCALE, ROUNDING);
        }

        try {
            Money money = new Money(amount, new Currency(fromCurrency));
            Currency target = new Currency(toCurrency);
            return fxRateService.convert(money, target, date).amount().setScale(MONEY_SCALE, ROUNDING);
        } catch (Exception ex) {
            String warnMsg = String.format("Missing FX rate %s/%s on %s. Using 1.0 parity.", fromCurrency, toCurrency, date);
            if (!warnings.contains(warnMsg)) {
                warnings.add(warnMsg);
            }
            return amount.setScale(MONEY_SCALE, ROUNDING);
        }
    }
}
