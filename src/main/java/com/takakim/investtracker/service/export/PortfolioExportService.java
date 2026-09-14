package com.takakim.investtracker.service.export;

import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.domain.PositionStatus;
import com.takakim.investtracker.domain.Transaction;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.PositionRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.ResourceNotFoundException;
import com.takakim.investtracker.service.analytics.AnalyticsEngine;
import com.takakim.investtracker.service.analytics.HoldingExposure;
import com.takakim.investtracker.service.analytics.PortfolioAnalytics;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.takakim.investtracker.api.ApiDtos.CashFlowAnalyticsResponse;
import com.takakim.investtracker.api.ApiDtos.CashFlowPeriodPoint;
import com.takakim.investtracker.service.analytics.CashFlowAnalyticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PortfolioExportService {

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final AnalyticsEngine analyticsEngine;
    private final CashFlowAnalyticsService cashFlowAnalyticsService;

    public PortfolioExportService(
            PortfolioRepository portfolioRepository,
            PositionRepository positionRepository,
            TransactionRepository transactionRepository,
            AnalyticsEngine analyticsEngine,
            CashFlowAnalyticsService cashFlowAnalyticsService) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.analyticsEngine = analyticsEngine;
        this.cashFlowAnalyticsService = cashFlowAnalyticsService;
    }

    public String exportCashFlowsCsv(UUID portfolioId, String period, String groupBy) {
        CashFlowAnalyticsResponse response = cashFlowAnalyticsService.calculateCashFlows(portfolioId, period, groupBy);

        StringBuilder sb = new StringBuilder();
        sb.append("Period,Start Date,End Date,Deposits,Withdrawals,Net Contributions,Internal Income,Cumulative Contributions,Currency\n");

        for (CashFlowPeriodPoint pt : response.periods()) {
            sb.append(escapeCsv(pt.periodLabel())).append(",")
                    .append(DateTimeFormatter.ISO_INSTANT.format(pt.startDate())).append(",")
                    .append(DateTimeFormatter.ISO_INSTANT.format(pt.endDate())).append(",")
                    .append(pt.deposits() != null ? pt.deposits().toPlainString() : "0.00").append(",")
                    .append(pt.withdrawals() != null ? pt.withdrawals().toPlainString() : "0.00").append(",")
                    .append(pt.netContributions() != null ? pt.netContributions().toPlainString() : "0.00").append(",")
                    .append(pt.internalIncome() != null ? pt.internalIncome().toPlainString() : "0.00").append(",")
                    .append(pt.cumulativeNetContributions() != null ? pt.cumulativeNetContributions().toPlainString() : "0.00").append(",")
                    .append(escapeCsv(response.baseCurrency())).append("\n");
        }

        return sb.toString();
    }

    public String exportPositionsCsv(UUID portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));

        PortfolioAnalytics analytics = analyticsEngine.calculate(portfolioId, Instant.now());
        Map<UUID, HoldingExposure> holdingMap = analytics.topHoldings().stream()
                .collect(Collectors.toMap(HoldingExposure::instrumentId, h -> h, (a, b) -> a));

        List<Position> positions = positionRepository
                .findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE);

        StringBuilder sb = new StringBuilder();
        sb.append("Account,Instrument Name,Ticker,ISIN,Asset Class,Quantity,Cost Basis Amount,Cost Basis Currency,Current Price,Market Value (")
                .append(portfolio.getBaseCurrency().code())
                .append("),Unrealized Gain/Loss,Weight %\n");

        for (Position pos : positions) {
            var inst = pos.getInstrument();
            HoldingExposure h = holdingMap.get(inst.getId());

            sb.append(escapeCsv(pos.getAccount().getName())).append(",")
                    .append(escapeCsv(inst.getName())).append(",")
                    .append(escapeCsv(inst.getTicker() != null ? inst.getTicker() : "")).append(",")
                    .append(escapeCsv(inst.getIsin() != null ? inst.getIsin() : "")).append(",")
                    .append(escapeCsv(inst.getAssetClass().name())).append(",")
                    .append(pos.getQuantity().toPlainString()).append(",")
                    .append(pos.getCostBasisAmount() != null ? pos.getCostBasisAmount().toPlainString() : "0.0000").append(",")
                    .append(escapeCsv(pos.getCostBasisCurrency() != null ? pos.getCostBasisCurrency() : inst.getCurrency().code())).append(",")
                    .append(h != null ? h.currentPrice().toPlainString() : "0.0000").append(",")
                    .append(h != null ? h.marketValue().toPlainString() : "0.0000").append(",")
                    .append(h != null ? h.unrealizedGainLoss().toPlainString() : "0.0000").append(",")
                    .append(h != null ? h.weightPercentage().multiply(java.math.BigDecimal.valueOf(100)).toPlainString() : "0").append("\n");
        }

        return sb.toString();
    }

    public String exportTransactionsCsv(UUID portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }

        List<Transaction> transactions = transactionRepository
                .findByAccountPortfolioIdOrderByTradeDateDesc(portfolioId);

        StringBuilder sb = new StringBuilder();
        sb.append("Date,Type,Account,Instrument,Ticker,Quantity,Price,Gross Amount,Fee,Tax,Net Amount,Currency,Notes\n");

        for (Transaction tx : transactions) {
            String instName = tx.getInstrument() != null ? tx.getInstrument().getName() : "";
            String ticker = tx.getInstrument() != null && tx.getInstrument().getTicker() != null ? tx.getInstrument().getTicker() : "";

            sb.append(DateTimeFormatter.ISO_INSTANT.format(tx.getTradeDate())).append(",")
                    .append(escapeCsv(tx.getType().name())).append(",")
                    .append(escapeCsv(tx.getAccount().getName())).append(",")
                    .append(escapeCsv(instName)).append(",")
                    .append(escapeCsv(ticker)).append(",")
                    .append(tx.getQuantity() != null ? tx.getQuantity().toPlainString() : "").append(",")
                    .append(tx.getPrice() != null ? tx.getPrice().toPlainString() : "").append(",")
                    .append(tx.getGrossAmount().toPlainString()).append(",")
                    .append(tx.getFeeAmount() != null ? tx.getFeeAmount().toPlainString() : "0.0000").append(",")
                    .append(tx.getTaxAmount() != null ? tx.getTaxAmount().toPlainString() : "0.0000").append(",")
                    .append(tx.getNetAmount().toPlainString()).append(",")
                    .append(escapeCsv(tx.getCurrency())).append(",")
                    .append(escapeCsv(tx.getNotes() != null ? tx.getNotes() : "")).append("\n");
        }

        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
