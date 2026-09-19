package com.takakim.investtracker.service.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.takakim.investtracker.api.ApiDtos;
import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AccountStatus;
import com.takakim.investtracker.domain.HoldingAiEvaluation;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.PortfolioAiEvaluation;
import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.HoldingAiEvaluationRepository;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.PortfolioAiEvaluationRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.service.PositionService;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.analytics.HoldingExposure;
import com.takakim.investtracker.service.analytics.PortfolioAnalytics;
import com.takakim.investtracker.service.ai.dto.AiConfigRequest;
import com.takakim.investtracker.service.ai.dto.AiRiskLevel;
import com.takakim.investtracker.service.ai.dto.AiStance;
import com.takakim.investtracker.service.ai.dto.AiStatusDto;
import com.takakim.investtracker.service.ai.dto.HoldingAiEvaluationDto;
import com.takakim.investtracker.service.ai.dto.HoldingFinancialMetricsDto;
import com.takakim.investtracker.service.ai.dto.PortfolioAiEvaluationDto;
import com.takakim.investtracker.service.analytics.AnalyticsEngine;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceDtos;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(readOnly = true)
public class AiEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AiEvaluationService.class);

    private final AiGatewayFactory gatewayFactory;
    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;
    private final PositionService positionService;
    private final AnalyticsEngine analyticsEngine;
    private final YahooFinanceGateway yahooFinanceGateway;
    private final ObjectMapper objectMapper;
    private final PortfolioAiEvaluationRepository portfolioAiEvaluationRepository;
    private final HoldingAiEvaluationRepository holdingAiEvaluationRepository;
    private final TransactionTemplate transactionTemplate;

    public AiEvaluationService(
            AiGatewayFactory gatewayFactory,
            PortfolioRepository portfolioRepository,
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository,
            PositionService positionService,
            AnalyticsEngine analyticsEngine,
            @Autowired(required = false) YahooFinanceGateway yahooFinanceGateway,
            ObjectMapper objectMapper,
            PortfolioAiEvaluationRepository portfolioAiEvaluationRepository,
            HoldingAiEvaluationRepository holdingAiEvaluationRepository
    ) {
        this(gatewayFactory, portfolioRepository, accountRepository, instrumentRepository, positionService,
                analyticsEngine, yahooFinanceGateway, objectMapper, portfolioAiEvaluationRepository,
                holdingAiEvaluationRepository, null);
    }

    @Autowired
    public AiEvaluationService(
            AiGatewayFactory gatewayFactory,
            PortfolioRepository portfolioRepository,
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository,
            PositionService positionService,
            AnalyticsEngine analyticsEngine,
            @Autowired(required = false) YahooFinanceGateway yahooFinanceGateway,
            ObjectMapper objectMapper,
            PortfolioAiEvaluationRepository portfolioAiEvaluationRepository,
            HoldingAiEvaluationRepository holdingAiEvaluationRepository,
            @Autowired(required = false) PlatformTransactionManager transactionManager
    ) {
        this.gatewayFactory = gatewayFactory;
        this.portfolioRepository = portfolioRepository;
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
        this.positionService = positionService;
        this.analyticsEngine = analyticsEngine;
        this.yahooFinanceGateway = yahooFinanceGateway;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.portfolioAiEvaluationRepository = portfolioAiEvaluationRepository;
        this.holdingAiEvaluationRepository = holdingAiEvaluationRepository;
        if (transactionManager != null) {
            this.transactionTemplate = new TransactionTemplate(transactionManager);
            this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        } else {
            this.transactionTemplate = null;
        }
    }

    private void executeInTransaction(Runnable action) {
        if (transactionTemplate != null) {
            transactionTemplate.executeWithoutResult(status -> action.run());
        } else {
            action.run();
        }
    }

    public AiStatusDto getStatus() {
        AiStatusDto raw = gatewayFactory.getActiveGateway().checkStatus();
        return new AiStatusDto(
                raw.enabled(),
                raw.connected(),
                raw.provider(),
                raw.baseUrl(),
                raw.configuredModel(),
                raw.availableModels(),
                raw.errorMessage(),
                raw.timeoutSeconds(),
                gatewayFactory.getAvailableProviders()
        );
    }

    @Transactional
    public AiStatusDto updateConfig(AiConfigRequest request) {
        if (request != null) {
            if (request.timeoutSeconds() != null) {
                gatewayFactory.updateTimeout(request.timeoutSeconds());
            }
            if (request.provider() != null && !request.provider().isBlank()) {
                gatewayFactory.updateProvider(request.provider());
            }
            if (request.model() != null && !request.model().isBlank()) {
                gatewayFactory.updateModel(request.model());
            }
        }
        return getStatus();
    }


    /**
     * Returns the latest persisted portfolio AI evaluation for the given portfolio,
     * or empty if no evaluation has been run yet.
     */
    public Optional<PortfolioAiEvaluationDto> getLatestPortfolioEvaluation(UUID portfolioId) {
        // Verify portfolio exists
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
        return portfolioAiEvaluationRepository.findByPortfolioId(portfolioId)
                .flatMap(this::deserializePortfolioEvaluation)
                .map(dto -> enrichWithLatestHoldingEvaluations(portfolioId, dto));
    }

    private PortfolioAiEvaluationDto enrichWithLatestHoldingEvaluations(UUID portfolioId, PortfolioAiEvaluationDto dto) {
        List<HoldingAiEvaluationDto> latestHoldings = getLatestHoldingEvaluations(portfolioId);
        if (latestHoldings.isEmpty()) {
            return dto;
        }
        return new PortfolioAiEvaluationDto(
                dto.portfolioId(),
                dto.portfolioName(),
                dto.baseCurrency(),
                dto.overallRiskScore(),
                dto.overallRiskLevel(),
                dto.executiveSummary(),
                dto.diversificationAssessment(),
                dto.concentrationRisks(),
                dto.taxAndLocationOptimization(),
                dto.topRecommendations(),
                dto.macroStressScenarios(),
                latestHoldings,
                dto.modelUsed(),
                dto.evaluatedAt()
        );
    }

    /**
     * Returns all persisted holding AI evaluations for the given portfolio.
     */
    public List<HoldingAiEvaluationDto> getLatestHoldingEvaluations(UUID portfolioId) {
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
        return holdingAiEvaluationRepository.findByPortfolioId(portfolioId).stream()
                .map(this::deserializeHoldingEvaluation)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    /**
     * Returns the latest persisted holding AI evaluation for a specific instrument in the portfolio.
     */
    public Optional<HoldingAiEvaluationDto> getLatestHoldingEvaluation(UUID portfolioId, UUID instrumentId) {
        portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
        return holdingAiEvaluationRepository.findByPortfolioIdAndInstrumentId(portfolioId, instrumentId)
                .flatMap(this::deserializeHoldingEvaluation);
    }

    private Optional<PortfolioAiEvaluationDto> deserializePortfolioEvaluation(PortfolioAiEvaluation entity) {
        try {
            return Optional.of(objectMapper.readValue(entity.getResultJson(), PortfolioAiEvaluationDto.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize portfolio AI evaluation {}: {}", entity.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<HoldingAiEvaluationDto> deserializeHoldingEvaluation(HoldingAiEvaluation entity) {
        try {
            return Optional.of(objectMapper.readValue(entity.getResultJson(), HoldingAiEvaluationDto.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize holding AI evaluation {}: {}", entity.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public HoldingAiEvaluationDto evaluateHolding(UUID portfolioId, UUID instrumentId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + instrumentId));

        List<ApiDtos.PositionPerformanceResponse> positions = positionService.listPortfolioPositionsPerformance(portfolioId, false);
        List<ApiDtos.PositionPerformanceResponse> holdingPositions = positions.stream()
                .filter(p -> p.instrumentId().equals(instrumentId))
                .toList();

        if (holdingPositions.isEmpty()) {
            throw new ResourceNotFoundException("No active holding found for instrument " + instrument.getTicker() + " in portfolio " + portfolioId);
        }

        // Aggregate holding quantities, values, and cost basis
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalUnrealizedGain = BigDecimal.ZERO;
        BigDecimal latestPrice = BigDecimal.ZERO;
        BigDecimal latestNativePrice = null;
        String nativeCurrency = null;
        List<String> accountWrappers = new ArrayList<>();

        for (ApiDtos.PositionPerformanceResponse p : holdingPositions) {
            totalQty = totalQty.add(p.currentQuantity());
            totalCostBasis = totalCostBasis.add(p.currentCostBasis());
            totalMarketValue = totalMarketValue.add(p.currentMarketValue());
            totalUnrealizedGain = totalUnrealizedGain.add(p.unrealizedGainLoss());
            if (p.currentPrice() != null && p.currentPrice().compareTo(BigDecimal.ZERO) > 0) {
                latestPrice = p.currentPrice();
            }
            if (p.nativePrice() != null && p.nativePrice().compareTo(BigDecimal.ZERO) > 0) {
                latestNativePrice = p.nativePrice();
            }
            if (p.nativeCurrency() != null && !p.nativeCurrency().isBlank()) {
                nativeCurrency = p.nativeCurrency();
            }
            if (p.accountName() != null) {
                accountWrappers.add(p.accountName());
            }
        }

        BigDecimal avgCost = (totalQty.compareTo(BigDecimal.ZERO) > 0)
                ? totalCostBasis.divide(totalQty, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Double unrealizedGainPct = (totalCostBasis.compareTo(BigDecimal.ZERO) > 0)
                ? totalUnrealizedGain.divide(totalCostBasis, 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                : 0.0;

        // Portfolio weight
        PortfolioAnalytics analytics = analyticsEngine.calculate(portfolioId, Instant.now());
        BigDecimal totalPortfolioVal = analytics.totalCurrentValue();
        Double weightPct = (totalPortfolioVal != null && totalPortfolioVal.compareTo(BigDecimal.ZERO) > 0)
                ? totalMarketValue.divide(totalPortfolioVal, 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                : 0.0;

        // ── Yahoo Finance: 52-week range (chart) + valuation multiples (quoteSummary) ──────
        Double fiftyTwoWeekHigh = null;
        Double fiftyTwoWeekLow = null;
        Double yahooTrailingPe = null;
        Double yahooForwardPe = null;
        Double yahooPegRatio = null;
        Double yahooPriceToBook = null;
        Double yahooDividendYield = null;
        Double yahooDebtToEquity = null;
        Double yahooReturnOnEquity = null;
        Double yahooMarketCap = null;
        if (yahooFinanceGateway != null && instrument.getTicker() != null && !instrument.getTicker().isBlank()) {
            try {
                // 52-week high/low from 1y chart
                Optional<YahooFinanceDtos.ChartEntry> chartOpt = yahooFinanceGateway.fetchChart(instrument.getTicker(), "1d", "1y");
                if (chartOpt.isPresent()) {
                    YahooFinanceDtos.ChartEntry entry = chartOpt.get();
                    if (entry.indicators() != null && entry.indicators().quote() != null && !entry.indicators().quote().isEmpty()) {
                        YahooFinanceDtos.QuoteIndicator qi = entry.indicators().quote().get(0);
                        if (qi.high() != null) {
                            fiftyTwoWeekHigh = qi.high().stream().filter(java.util.Objects::nonNull).mapToDouble(BigDecimal::doubleValue).max().orElse(0.0);
                            if (fiftyTwoWeekHigh == 0.0) fiftyTwoWeekHigh = null;
                        }
                        if (qi.low() != null) {
                            fiftyTwoWeekLow = qi.low().stream().filter(java.util.Objects::nonNull).mapToDouble(BigDecimal::doubleValue).min().orElse(0.0);
                            if (fiftyTwoWeekLow == 0.0) fiftyTwoWeekLow = null;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Could not fetch Yahoo chart data for {}: {}", instrument.getTicker(), e.getMessage());
            }
            try {
                // Valuation multiples from /v7/finance/quote (no crumb required)
                Optional<YahooFinanceDtos.QuoteResult> quoteOpt = yahooFinanceGateway.fetchQuote(instrument.getTicker());
                if (quoteOpt.isPresent()) {
                    YahooFinanceDtos.QuoteResult q = quoteOpt.get();
                    yahooTrailingPe      = q.trailingPE();
                    yahooForwardPe       = q.forwardPE();
                    yahooPriceToBook     = q.priceToBook();
                    yahooDividendYield   = q.trailingAnnualDividendYield();
                    yahooMarketCap       = q.marketCap();
                    // /v7/quote doesn't include PEG, debtToEquity, or ROE — those fall back to AI
                    // Prefer chart-derived 52w range; use quote fields as fallback
                    if (fiftyTwoWeekHigh == null && q.fiftyTwoWeekHigh() != null && q.fiftyTwoWeekHigh() > 0)
                        fiftyTwoWeekHigh = q.fiftyTwoWeekHigh();
                    if (fiftyTwoWeekLow == null && q.fiftyTwoWeekLow() != null && q.fiftyTwoWeekLow() > 0)
                        fiftyTwoWeekLow = q.fiftyTwoWeekLow();
                    log.debug("Yahoo /v7/quote for {}: PE={} fwdPE={} PB={} DY={} 52wH={} 52wL={} cap={}",
                            instrument.getTicker(), yahooTrailingPe, yahooForwardPe, yahooPriceToBook,
                            yahooDividendYield, fiftyTwoWeekHigh, fiftyTwoWeekLow, yahooMarketCap);
                }
            } catch (Exception e) {
                log.debug("Could not fetch Yahoo quote for {}: {}", instrument.getTicker(), e.getMessage());
            }

        }

        // Build prompt — fundamentalMetrics in JSON schema retained for AI-estimated fields (expenseRatio for ETFs)
        String systemPrompt = """
                You are a senior portfolio risk manager and financial analyst.
                Evaluate the provided investment holding using strict financial reasoning, fundamental health metrics, balance sheet solvency, valuation multiples, and portfolio context.
                Assess the risk of continuing to hold this asset versus selling/trimming it now.
                Real-time valuation metrics are provided where available in the holding data — use them in your analysis.
                You must output strictly valid JSON matching this schema:
                {
                  "stance": "STRONG_BUY" | "ACCUMULATE" | "HOLD" | "TRIM" | "SELL",
                  "riskScore": 1 to 10,
                  "riskLevel": "LOW" | "MODERATE" | "HIGH" | "VERY_HIGH",
                  "executiveSummary": "2-3 sentence executive rationale",
                  "strengths": ["string", "string", ...],
                  "risks": ["string", "string", ...],
                  "holdingVsSellingTradeoff": "explicit comparison of the risks of holding vs selling now",
                  "fundamentalMetrics": {
                    "peRatio": number or null,
                    "forwardPe": number or null,
                    "pegRatio": number or null,
                    "priceToBook": number or null,
                    "dividendYield": number or null,
                    "debtToEquity": number or null,
                    "returnOnEquity": number or null,
                    "expenseRatio": number or null
                  }
                }
                Do not include markdown, thinking blocks, or conversational text outside of the JSON object.
                """;

        // Build real-time metrics context string for the prompt (grounded facts from Yahoo)
        String metricsContext = buildMetricsContext(
                yahooTrailingPe, yahooForwardPe, yahooPegRatio, yahooPriceToBook,
                yahooDividendYield, yahooDebtToEquity, yahooReturnOnEquity,
                fiftyTwoWeekLow, fiftyTwoWeekHigh, yahooMarketCap);

        String nativePriceInfo = "";
        if (latestNativePrice != null && nativeCurrency != null
                && !portfolio.getBaseCurrency().code().equalsIgnoreCase(nativeCurrency)) {
            nativePriceInfo = String.format(" (Native: %s %s)", latestNativePrice.toPlainString(), nativeCurrency);
        }

        String userPrompt = String.format("""
                Holding Analysis Request:
                - Asset: %s (%s)
                - ISIN: %s
                - Asset Class: %s
                - Native Currency: %s
                - Portfolio Reporting Currency: %s
                - Quantity Held: %s
                - Average Buy Price / Cost Basis: %s %s
                - Current Market Price: %s %s%s
                - Current Market Value: %s %s
                - Unrealized Gain/Loss: %s %s (%.2f%%)
                - Portfolio Weight: %.2f%%
                - Account Wrappers: %s
                - 52-Week Range: %s to %s
                %s

                Please evaluate the financial records, current valuation, balance sheet risk, and whether the investor should HOLD, ACCUMULATE, TRIM, or SELL.
                """,
                instrument.getName(),
                instrument.getTicker(),
                instrument.getIsin() != null ? instrument.getIsin() : "N/A",
                instrument.getAssetClass().name(),
                instrument.getCurrency().code(),
                portfolio.getBaseCurrency().code(),
                totalQty.toPlainString(),
                avgCost.toPlainString(), portfolio.getBaseCurrency().code(),
                latestPrice.toPlainString(), portfolio.getBaseCurrency().code(), nativePriceInfo,
                totalMarketValue.toPlainString(), portfolio.getBaseCurrency().code(),
                totalUnrealizedGain.toPlainString(), portfolio.getBaseCurrency().code(), unrealizedGainPct,
                weightPct,
                String.join(", ", accountWrappers),
                fiftyTwoWeekLow != null ? String.format("%.2f %s", fiftyTwoWeekLow, instrument.getCurrency().code()) : "N/A",
                fiftyTwoWeekHigh != null ? String.format("%.2f %s", fiftyTwoWeekHigh, instrument.getCurrency().code()) : "N/A",
                metricsContext
        );

        AiGateway gateway = gatewayFactory.getActiveGateway();
        String rawResponse = gateway.generateChatCompletion(systemPrompt, userPrompt);
        HoldingAiEvaluationDto result = parseHoldingResponse(rawResponse, instrument, totalQty, latestPrice, avgCost,
                totalUnrealizedGain, unrealizedGainPct, weightPct, fiftyTwoWeekHigh, fiftyTwoWeekLow,
                yahooTrailingPe, yahooForwardPe, yahooPegRatio, yahooPriceToBook,
                yahooDividendYield, yahooDebtToEquity, yahooReturnOnEquity, yahooMarketCap,
                portfolio.getBaseCurrency().code(), gateway.getProviderName());
        persistHoldingEvaluation(portfolioId, instrumentId, gateway.getProviderName(), result);
        return result;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PortfolioAiEvaluationDto evaluatePortfolio(UUID portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));

        PortfolioAnalytics analytics = analyticsEngine.calculate(portfolioId, Instant.now());
        List<ApiDtos.PositionPerformanceResponse> positions = positionService.listPortfolioPositionsPerformance(portfolioId, false);
        List<Account> accounts = accountRepository.findAllByPortfolioIdAndStatusOrderByNameAsc(portfolioId, AccountStatus.ACTIVE);

        // Sort holdings by market value descending
        List<HoldingExposure> topHoldings = analytics.topHoldings().stream()
                .sorted(Comparator.comparing(HoldingExposure::marketValue).reversed())
                .limit(5)
                .toList();

        StringBuilder holdingsSummary = new StringBuilder();
        for (HoldingExposure h : topHoldings) {
            holdingsSummary.append(String.format("- %s (%s): %s %s, weight: %.2f%%, gain/loss: %s\n",
                    h.instrumentName(), h.ticker(), h.marketValue(), portfolio.getBaseCurrency().code(),
                    h.weightPercentage().doubleValue(),
                    h.unrealizedGainLoss()));
        }

        StringBuilder accountsSummary = new StringBuilder();
        for (Account a : accounts) {
            accountsSummary.append(String.format("- %s (%s, Tax Treatment: %s, Currency: %s)\n",
                    a.getName(), a.getBrokerName(), a.getTaxTreatment(), a.getAccountCurrency()));
        }

        String systemPrompt = """
                You are a senior chief investment officer and portfolio strategist.
                Evaluate the entire multi-currency investment portfolio for concentration risk, asset allocation, currency exposure, tax location efficiency (ISA vs SIPP vs Taxable GIA), and stress vulnerabilities.
                You must output strictly valid JSON matching this schema:
                {
                  "overallRiskScore": 1 to 10,
                  "overallRiskLevel": "LOW" | "MODERATE" | "HIGH" | "VERY_HIGH",
                  "executiveSummary": "Concise 2-3 sentence assessment of portfolio health and structure",
                  "diversificationAssessment": "Detailed analysis of asset class and currency diversification",
                  "concentrationRisks": ["string", "string", ...],
                  "taxAndLocationOptimization": ["string", "string", ...],
                  "topRecommendations": ["string", "string", ...],
                  "macroStressScenarios": ["string", "string", ...],
                  "topHoldingEvaluations": [
                    {
                      "symbol": "ticker symbol matching top holdings list",
                      "stance": "HOLD" | "ACCUMULATE" | "TRIM" | "SELL",
                      "riskScore": 1 to 10,
                      "riskLevel": "LOW" | "MODERATE" | "HIGH" | "VERY_HIGH",
                      "executiveSummary": "1-2 sentence holding recommendation"
                    }
                  ]
                }
                Do not include markdown or conversational text outside of the JSON object.
                """;

        String userPrompt = String.format("""
                Portfolio Analysis Request:
                - Portfolio Name: %s
                - Base Currency: %s
                - Total Portfolio Value: %s %s
                - Total Cash Balance: %s %s (Cash Ratio: %.2f%%)
                - Net Capital Invested: %s %s
                - Total Unrealized Gain/Loss: %s %s

                Top Holdings by Allocation:
                %s

                Account & Custodian Wrappers:
                %s

                Please provide an audit-grade risk evaluation, diversification diagnosis, tax wrapper optimization suggestions, and macro stress test scenarios.
                """,
                portfolio.getName(),
                portfolio.getBaseCurrency().code(),
                analytics.totalCurrentValue(), portfolio.getBaseCurrency().code(),
                analytics.totalCashValue(), portfolio.getBaseCurrency().code(),
                (analytics.totalCurrentValue().compareTo(BigDecimal.ZERO) > 0)
                        ? analytics.totalCashValue().divide(analytics.totalCurrentValue(), 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                        : 0.0,
                analytics.totalCostBasis(), portfolio.getBaseCurrency().code(),
                analytics.totalUnrealizedGainLoss(), portfolio.getBaseCurrency().code(),
                holdingsSummary.length() > 0 ? holdingsSummary.toString() : "No active holdings",
                accountsSummary.length() > 0 ? accountsSummary.toString() : "No registered accounts"
        );

        AiGateway gateway = gatewayFactory.getActiveGateway();
        String rawResponse = gateway.generateChatCompletion(systemPrompt, userPrompt);
        PortfolioAiEvaluationDto result = parsePortfolioResponse(rawResponse, portfolio, gateway.getProviderName(), analytics.topHoldings());
        persistPortfolioEvaluation(portfolioId, gateway.getProviderName(), result);
        return result;
    }

    public void persistPortfolioEvaluation(UUID portfolioId, String providerName, PortfolioAiEvaluationDto dto) {
        if (dto == null) {
            return;
        }
        executeInTransaction(() -> {
            try {
                String json = objectMapper.writeValueAsString(dto);
                Instant evaluatedAt = dto.evaluatedAt() != null ? dto.evaluatedAt() : Instant.now();
                int riskScore = dto.overallRiskScore();
                String riskLevel = dto.overallRiskLevel() != null ? dto.overallRiskLevel().name() : AiRiskLevel.MODERATE.name();
                String summary = dto.executiveSummary() != null ? dto.executiveSummary() : "Portfolio evaluation";
                String model = dto.modelUsed() != null ? dto.modelUsed() : providerName;

                Optional<PortfolioAiEvaluation> existing = portfolioAiEvaluationRepository.findByPortfolioId(portfolioId);
                PortfolioAiEvaluation entity;
                if (existing.isPresent()) {
                    entity = existing.get();
                    entity.setProvider(providerName);
                    entity.setModelUsed(model);
                    entity.setOverallRiskScore(riskScore);
                    entity.setOverallRiskLevel(riskLevel);
                    entity.setExecutiveSummary(summary);
                    entity.setResultJson(json);
                    entity.setEvaluatedAt(evaluatedAt);
                } else {
                    entity = new PortfolioAiEvaluation(
                            portfolioId, providerName, model,
                            riskScore, riskLevel, summary, json, evaluatedAt);
                }
                portfolioAiEvaluationRepository.saveAndFlush(entity);
                log.info("Persisted portfolio AI evaluation for portfolio {} with provider {}", portfolioId, providerName);
            } catch (Exception e) {
                log.error("Failed to persist portfolio AI evaluation for {}: {}", portfolioId, e.getMessage(), e);
            }
        });
    }

    public void persistHoldingEvaluation(UUID portfolioId, UUID instrumentId, String providerName, HoldingAiEvaluationDto dto) {
        if (dto == null) {
            return;
        }
        executeInTransaction(() -> {
            try {
                String json = objectMapper.writeValueAsString(dto);
                Instant evaluatedAt = dto.evaluatedAt() != null ? dto.evaluatedAt() : Instant.now();
                int riskScore = dto.riskScore();
                String riskLevel = dto.riskLevel() != null ? dto.riskLevel().name() : AiRiskLevel.MODERATE.name();
                String stance = dto.stance() != null ? dto.stance().name() : AiStance.HOLD.name();
                String summary = dto.executiveSummary() != null ? dto.executiveSummary() : "Holding evaluation";
                String model = dto.modelUsed() != null ? dto.modelUsed() : providerName;

                Optional<HoldingAiEvaluation> existing = holdingAiEvaluationRepository
                        .findByPortfolioIdAndInstrumentId(portfolioId, instrumentId);
                HoldingAiEvaluation entity;
                if (existing.isPresent()) {
                    entity = existing.get();
                    entity.setProvider(providerName);
                    entity.setModelUsed(model);
                    entity.setStance(stance);
                    entity.setRiskScore(riskScore);
                    entity.setRiskLevel(riskLevel);
                    entity.setExecutiveSummary(summary);
                    entity.setResultJson(json);
                    entity.setEvaluatedAt(evaluatedAt);
                } else {
                    entity = new HoldingAiEvaluation(
                            portfolioId, instrumentId, providerName,
                            model, stance, riskScore, riskLevel, summary, json, evaluatedAt);
                }
                holdingAiEvaluationRepository.saveAndFlush(entity);
                log.info("Persisted holding AI evaluation for portfolio {} / instrument {} with provider {}", portfolioId, instrumentId, providerName);
            } catch (Exception e) {
                log.error("Failed to persist holding AI evaluation for {}/{}: {}", portfolioId, instrumentId, e.getMessage(), e);
            }
        });
    }

    private HoldingAiEvaluationDto parseHoldingResponse(
            String rawResponse,
            Instrument instrument,
            BigDecimal qty,
            BigDecimal price,
            BigDecimal avgCost,
            BigDecimal unrealizedGain,
            Double unrealizedGainPct,
            Double weightPct,
            Double fiftyTwoWeekHigh,
            Double fiftyTwoWeekLow,
            Double yahooTrailingPe,
            Double yahooForwardPe,
            Double yahooPegRatio,
            Double yahooPriceToBook,
            Double yahooDividendYield,
            Double yahooDebtToEquity,
            Double yahooReturnOnEquity,
            Double yahooMarketCap,
            String baseCurrency,
            String providerName
    ) {
        String cleanJson = extractJson(rawResponse);
        if (cleanJson.isBlank() || cleanJson.equals("{}")) {
            return fallbackHoldingEvaluation(instrument, qty, price, avgCost, unrealizedGain, unrealizedGainPct, weightPct,
                    fiftyTwoWeekHigh, fiftyTwoWeekLow,
                    yahooTrailingPe, yahooForwardPe, yahooPegRatio, yahooPriceToBook,
                    yahooDividendYield, yahooDebtToEquity, yahooReturnOnEquity, yahooMarketCap,
                    baseCurrency, rawResponse, providerName);
        }
        try {
            JsonNode root = objectMapper.readTree(cleanJson);
            AiStance stance = parseStance(root.has("stance") && !root.get("stance").isNull() ? root.get("stance").asText() : null);
            int riskScore = root.path("riskScore").asInt(5);
            if (riskScore < 1) riskScore = 1;
            if (riskScore > 10) riskScore = 10;
            String riskLevelStr = root.has("riskLevel") && !root.get("riskLevel").isNull() ? root.get("riskLevel").asText() : null;
            AiRiskLevel riskLevel = parseRiskLevel(riskLevelStr, riskScore);
            String summary = root.path("executiveSummary").asText("Automated financial evaluation completed for " + instrument.getTicker());
            List<String> strengths = extractStringList(root.get("strengths"));
            List<String> risks = extractStringList(root.get("risks"));
            String tradeoff = root.path("holdingVsSellingTradeoff").asText("Hold vs sell trade-off analysis based on valuation and balance sheet quality.");

            JsonNode metricsNode = root.get("fundamentalMetrics");
            HoldingFinancialMetricsDto metrics = parseMetrics(metricsNode, instrument,
                    fiftyTwoWeekHigh, fiftyTwoWeekLow,
                    yahooTrailingPe, yahooForwardPe, yahooPegRatio, yahooPriceToBook,
                    yahooDividendYield, yahooDebtToEquity, yahooReturnOnEquity, yahooMarketCap,
                    baseCurrency);

            return new HoldingAiEvaluationDto(
                    instrument.getId(),
                    instrument.getTicker(),
                    instrument.getName(),
                    instrument.getAssetClass().name(),
                    qty,
                    price,
                    avgCost,
                    unrealizedGain,
                    unrealizedGainPct,
                    weightPct,
                    stance,
                    riskScore,
                    riskLevel,
                    summary,
                    strengths,
                    risks,
                    tradeoff,
                    metrics,
                    providerName,
                    Instant.now()
            );
        } catch (Exception e) {
            log.warn("Failed to parse JSON response from AI provider: {}. Falling back to default holding evaluation.", e.getMessage());
            return fallbackHoldingEvaluation(instrument, qty, price, avgCost, unrealizedGain, unrealizedGainPct, weightPct,
                    fiftyTwoWeekHigh, fiftyTwoWeekLow,
                    yahooTrailingPe, yahooForwardPe, yahooPegRatio, yahooPriceToBook,
                    yahooDividendYield, yahooDebtToEquity, yahooReturnOnEquity, yahooMarketCap,
                    baseCurrency, rawResponse, providerName);
        }
    }

    private PortfolioAiEvaluationDto parsePortfolioResponse(String rawResponse, Portfolio portfolio, String providerName) {
        return parsePortfolioResponse(rawResponse, portfolio, providerName, List.of());
    }

    private PortfolioAiEvaluationDto parsePortfolioResponse(
            String rawResponse,
            Portfolio portfolio,
            String providerName,
            List<HoldingExposure> topHoldings
    ) {
        String cleanJson = extractJson(rawResponse);
        try {
            JsonNode root = objectMapper.readTree(cleanJson);
            int riskScore = root.path("overallRiskScore").asInt(5);
            if (riskScore < 1) riskScore = 1;
            if (riskScore > 10) riskScore = 10;
            String riskLevelStr = root.has("overallRiskLevel") && !root.get("overallRiskLevel").isNull() ? root.get("overallRiskLevel").asText() : null;
            AiRiskLevel riskLevel = parseRiskLevel(riskLevelStr, riskScore);
            String summary = root.path("executiveSummary").asText("Portfolio evaluated by AI risk model.");
            String diversification = root.path("diversificationAssessment").asText("Portfolio analyzed across asset classes and currencies.");
            List<String> concentration = extractStringList(root.get("concentrationRisks"));
            List<String> taxOpt = extractStringList(root.get("taxAndLocationOptimization"));
            List<String> recommendations = extractStringList(root.get("topRecommendations"));
            List<String> macroScenarios = extractStringList(root.get("macroStressScenarios"));

            List<HoldingAiEvaluationDto> holdingEvaluations = parseTopHoldingEvaluations(root.get("topHoldingEvaluations"), portfolio, providerName, topHoldings);
            if (holdingEvaluations.isEmpty()) {
                holdingEvaluations = getLatestHoldingEvaluations(portfolio.getId());
            }

            return new PortfolioAiEvaluationDto(
                    portfolio.getId(),
                    portfolio.getName(),
                    portfolio.getBaseCurrency().code(),
                    riskScore,
                    riskLevel,
                    summary,
                    diversification,
                    concentration,
                    taxOpt,
                    recommendations,
                    macroScenarios,
                    holdingEvaluations,
                    providerName,
                    Instant.now()
            );
        } catch (Exception e) {
            log.warn("Failed to parse JSON portfolio response from AI provider: {}. Falling back to default.", e.getMessage());
            List<HoldingAiEvaluationDto> fallbackHoldings = getLatestHoldingEvaluations(portfolio.getId());
            return new PortfolioAiEvaluationDto(
                    portfolio.getId(),
                    portfolio.getName(),
                    portfolio.getBaseCurrency().code(),
                    5,
                    AiRiskLevel.MODERATE,
                    "Portfolio evaluated with baseline risk assessment.",
                    "Portfolio structure reviewed across registered accounts.",
                    List.of("Monitor single-holding concentration against benchmark"),
                    List.of("Ensure ISA/SIPP contribution limits are utilized before taxable accounts"),
                    List.of("Maintain adequate cash buffer for rebalancing opportunities"),
                    List.of("Interest rate sensitivity and equity market volatility"),
                    fallbackHoldings,
                    providerName,
                    Instant.now()
            );
        }
    }

    private List<HoldingAiEvaluationDto> parseTopHoldingEvaluations(
            JsonNode topHoldingsNode,
            Portfolio portfolio,
            String providerName,
            List<HoldingExposure> topHoldings
    ) {
        List<HoldingAiEvaluationDto> list = new ArrayList<>();
        if (topHoldingsNode == null || !topHoldingsNode.isArray() || topHoldings == null || topHoldings.isEmpty()) {
            return list;
        }
        for (JsonNode item : topHoldingsNode) {
            String sym = item.path("symbol").asText(null);
            if (sym == null || sym.isBlank()) continue;
            HoldingExposure matched = topHoldings.stream()
                    .filter(h -> (h.ticker() != null && sym.equalsIgnoreCase(h.ticker()))
                            || (h.instrumentName() != null && sym.equalsIgnoreCase(h.instrumentName())))
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                AiStance stance = parseStance(item.path("stance").asText(null));
                int rScore = item.path("riskScore").asInt(5);
                if (rScore < 1) rScore = 1;
                if (rScore > 10) rScore = 10;
                AiRiskLevel rLevel = parseRiskLevel(item.path("riskLevel").asText(null), rScore);
                String execSummary = item.path("executiveSummary").asText("Automated assessment for " + matched.ticker());
                List<String> strengths = extractStringList(item.get("strengths"));
                List<String> risks = extractStringList(item.get("risks"));
                String tradeoff = item.path("holdingVsSellingTradeoff").asText("Hold vs sell trade-off based on portfolio allocation.");

                BigDecimal avgCost = (matched.costBasis() != null && matched.costBasis().compareTo(BigDecimal.ZERO) > 0
                        && matched.quantity() != null && matched.quantity().compareTo(BigDecimal.ZERO) > 0)
                        ? matched.costBasis().divide(matched.quantity(), 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                Double unGainPct = (matched.costBasis() != null && matched.costBasis().compareTo(BigDecimal.ZERO) > 0
                        && matched.unrealizedGainLoss() != null)
                        ? matched.unrealizedGainLoss().divide(matched.costBasis(), 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                        : 0.0;

                Double weight = matched.weightPercentage() != null ? matched.weightPercentage().doubleValue() : 0.0;

                HoldingAiEvaluationDto hDto = new HoldingAiEvaluationDto(
                        matched.instrumentId(),
                        matched.ticker(),
                        matched.instrumentName(),
                        matched.assetClass().name(),
                        matched.quantity(),
                        matched.currentPrice(),
                        avgCost,
                        matched.unrealizedGainLoss() != null ? matched.unrealizedGainLoss() : BigDecimal.ZERO,
                        unGainPct,
                        weight,
                        stance,
                        rScore,
                        rLevel,
                        execSummary,
                        strengths,
                        risks,
                        tradeoff,
                        new HoldingFinancialMetricsDto(null, null, null, null, null, null, null, null, null, null, null, matched.assetClass().name(), portfolio.getBaseCurrency().code()),
                        providerName,
                        Instant.now()
                );
                persistHoldingEvaluation(portfolio.getId(), matched.instrumentId(), providerName, hDto);
                list.add(hDto);
            }
        }
        return list;
    }

    /**
     * Builds a {@link HoldingFinancialMetricsDto} by preferring Yahoo Finance-sourced values
     * over any values returned by the AI model. AI-provided values are used only as a fallback
     * when Yahoo returned {@code null} for a given field (e.g. niche tickers, ETF-specific metrics).
     * The {@code expenseRatio} is always sourced from the AI since Yahoo doesn’t expose it.
     */
    private HoldingFinancialMetricsDto parseMetrics(
            JsonNode node,
            Instrument instrument,
            Double high52, Double low52,
            Double yahooTrailingPe, Double yahooForwardPe,
            Double yahooPegRatio, Double yahooPriceToBook,
            Double yahooDividendYield, Double yahooDebtToEquity,
            Double yahooReturnOnEquity, Double yahooMarketCap,
            String curr) {

        // Extract AI-provided values (used only when Yahoo has no data for the field)
        Double aiPe = null, aiFwdPe = null, aiPeg = null, aiPb = null;
        Double aiDiv = null, aiDe = null, aiRoe = null, aiExp = null;
        if (node != null && !node.isNull()) {
            aiPe  = node.has("peRatio")        && !node.get("peRatio").isNull()        ? node.get("peRatio").asDouble()        : null;
            aiFwdPe = node.has("forwardPe")    && !node.get("forwardPe").isNull()      ? node.get("forwardPe").asDouble()      : null;
            aiPeg = node.has("pegRatio")        && !node.get("pegRatio").isNull()       ? node.get("pegRatio").asDouble()       : null;
            aiPb  = node.has("priceToBook")    && !node.get("priceToBook").isNull()    ? node.get("priceToBook").asDouble()    : null;
            aiDiv = node.has("dividendYield")   && !node.get("dividendYield").isNull()  ? node.get("dividendYield").asDouble()  : null;
            aiDe  = node.has("debtToEquity")   && !node.get("debtToEquity").isNull()   ? node.get("debtToEquity").asDouble()   : null;
            aiRoe = node.has("returnOnEquity")  && !node.get("returnOnEquity").isNull() ? node.get("returnOnEquity").asDouble() : null;
            aiExp = node.has("expenseRatio")    && !node.get("expenseRatio").isNull()   ? node.get("expenseRatio").asDouble()   : null;
        }

        // Yahoo values take precedence; AI values fill in gaps
        Double pe     = yahooTrailingPe    != null ? yahooTrailingPe    : aiPe;
        Double fwdPe  = yahooForwardPe     != null ? yahooForwardPe     : aiFwdPe;
        Double peg    = yahooPegRatio      != null ? yahooPegRatio      : aiPeg;
        Double pb     = yahooPriceToBook   != null ? yahooPriceToBook   : aiPb;
        Double div    = yahooDividendYield  != null ? yahooDividendYield  : aiDiv;
        Double de     = yahooDebtToEquity  != null ? yahooDebtToEquity  : aiDe;
        Double roe    = yahooReturnOnEquity != null ? yahooReturnOnEquity : aiRoe;
        // expenseRatio is always AI-sourced (Yahoo doesn’t expose it)
        Double exp    = aiExp;

        return new HoldingFinancialMetricsDto(pe, fwdPe, peg, pb, div, de, roe, high52, low52, yahooMarketCap, exp, instrument.getAssetClass().name(), curr);
    }

    private HoldingAiEvaluationDto fallbackHoldingEvaluation(
            Instrument instrument,
            BigDecimal qty,
            BigDecimal price,
            BigDecimal avgCost,
            BigDecimal unrealizedGain,
            Double unrealizedGainPct,
            Double weightPct,
            Double high52,
            Double low52,
            Double yahooTrailingPe,
            Double yahooForwardPe,
            Double yahooPegRatio,
            Double yahooPriceToBook,
            Double yahooDividendYield,
            Double yahooDebtToEquity,
            Double yahooReturnOnEquity,
            Double yahooMarketCap,
            String baseCurrency,
            String rawText,
            String providerName
    ) {
        return new HoldingAiEvaluationDto(
                instrument.getId(),
                instrument.getTicker(),
                instrument.getName(),
                instrument.getAssetClass().name(),
                qty,
                price,
                avgCost,
                unrealizedGain,
                unrealizedGainPct,
                weightPct,
                AiStance.HOLD,
                5,
                AiRiskLevel.MODERATE,
                sanitizeSummary(rawText, instrument),
                List.of("Established position within portfolio structure"),
                List.of("Subject to standard market and sector volatility"),
                "Evaluate position weight against target rebalancing plan before selling.",
                new HoldingFinancialMetricsDto(
                        yahooTrailingPe, yahooForwardPe, yahooPegRatio, yahooPriceToBook,
                        yahooDividendYield, yahooDebtToEquity, yahooReturnOnEquity,
                        high52, low52, yahooMarketCap, null,
                        instrument.getAssetClass().name(), baseCurrency),
                providerName,
                Instant.now()
        );
    }

    private String sanitizeSummary(String text, Instrument instrument) {
        if (text == null || text.isBlank()) {
            return "Position reviewed based on cost basis and current market price.";
        }
        String cleaned = text.trim();
        String lower = cleaned.toLowerCase();
        if (lower.startsWith("thinking process:")
                || lower.startsWith("thinking:")
                || lower.startsWith("<think>")
                || lower.contains("analyze the request")
                || cleaned.startsWith("{")
                || cleaned.startsWith("```")
                || cleaned.length() > 500) {
            return "Position in " + instrument.getName() + " (" + instrument.getTicker() + ") reviewed based on cost basis and current market valuation.";
        }
        return cleaned;
    }

    /**
     * Builds a formatted string of real-time valuation metrics to inject into the AI prompt.
     * Only non-null values are included so the prompt stays concise.
     */
    private String buildMetricsContext(
            Double trailingPe, Double forwardPe, Double pegRatio, Double priceToBook,
            Double dividendYield, Double debtToEquity, Double returnOnEquity,
            Double low52, Double high52, Double marketCap) {

        StringBuilder sb = new StringBuilder();
        if (trailingPe != null)   sb.append(String.format("- P/E (TTM): %.2f%n",        trailingPe));
        if (forwardPe != null)    sb.append(String.format("- Forward P/E: %.2f%n",       forwardPe));
        if (pegRatio != null)     sb.append(String.format("- PEG Ratio: %.2f%n",          pegRatio));
        if (priceToBook != null)  sb.append(String.format("- Price / Book: %.2f%n",       priceToBook));
        if (dividendYield != null) sb.append(String.format("- Dividend Yield: %.2f%%%n",  dividendYield * 100));
        if (debtToEquity != null) sb.append(String.format("- Debt / Equity: %.2f%n",      debtToEquity));
        if (returnOnEquity != null) sb.append(String.format("- ROE: %.2f%%%n",            returnOnEquity * 100));
        if (marketCap != null)    sb.append(String.format("- Market Cap: %.0f%n",         marketCap));
        if (sb.isEmpty()) return "";
        return "\nReal-Time Financial Metrics (sourced from market data):\n" + sb;
    }

    public String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return "{}";
        }
        String cleaned = text.trim();

        // Strip <think>...</think> tags if emitted by reasoning models
        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>", "").trim();

        // If reasoning output starts with Thinking Process: or Thinking:, advance to code fence or JSON object
        if (cleaned.startsWith("Thinking Process:") || cleaned.startsWith("Thinking:")) {
            int fence = cleaned.indexOf("```");
            int brace = cleaned.indexOf('{');
            if (fence >= 0) {
                cleaned = cleaned.substring(fence).trim();
            } else if (brace >= 0) {
                cleaned = cleaned.substring(brace).trim();
            }
        }

        int codeFenceStart = cleaned.indexOf("```json");
        if (codeFenceStart < 0) {
            codeFenceStart = cleaned.indexOf("```");
        }
        if (codeFenceStart >= 0) {
            int lineBreak = cleaned.indexOf('\n', codeFenceStart);
            int start = (lineBreak >= 0) ? lineBreak + 1 : codeFenceStart + 3;
            int codeFenceEnd = cleaned.indexOf("```", start);
            if (codeFenceEnd > start) {
                String candidate = cleaned.substring(start, codeFenceEnd).trim();
                if (candidate.startsWith("{") && candidate.endsWith("}")) {
                    return candidate;
                }
            }
        }
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return cleaned.substring(firstBrace, lastBrace + 1).trim();
        }
        return cleaned;
    }

    private AiStance parseStance(String stanceStr) {
        if (stanceStr == null) return AiStance.HOLD;
        try {
            return AiStance.valueOf(stanceStr.trim().toUpperCase());
        } catch (Exception e) {
            return AiStance.HOLD;
        }
    }

    private AiRiskLevel parseRiskLevel(String riskStr, int riskScore) {
        if (riskStr != null) {
            try {
                return AiRiskLevel.valueOf(riskStr.trim().toUpperCase());
            } catch (Exception ignored) {}
        }
        if (riskScore <= 3) return AiRiskLevel.LOW;
        if (riskScore <= 6) return AiRiskLevel.MODERATE;
        if (riskScore <= 8) return AiRiskLevel.HIGH;
        return AiRiskLevel.VERY_HIGH;
    }

    private List<String> extractStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                list.add(item.asText().trim());
            }
        }
        return list;
    }
}
