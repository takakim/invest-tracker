package com.takakim.investtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "holding_ai_evaluations")
public class HoldingAiEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "model_used", nullable = false, length = 200)
    private String modelUsed;

    @Column(name = "stance", nullable = false, length = 20)
    private String stance;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    @Column(name = "executive_summary", columnDefinition = "TEXT")
    private String executiveSummary;

    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected HoldingAiEvaluation() {}

    public HoldingAiEvaluation(UUID portfolioId, UUID instrumentId, String provider,
                                String modelUsed, String stance, int riskScore,
                                String riskLevel, String executiveSummary,
                                String resultJson, Instant evaluatedAt) {
        this.portfolioId = portfolioId;
        this.instrumentId = instrumentId;
        this.provider = provider;
        this.modelUsed = modelUsed;
        this.stance = stance;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.executiveSummary = executiveSummary;
        this.resultJson = resultJson;
        this.evaluatedAt = evaluatedAt;
    }

    public UUID getId() { return id; }
    public UUID getPortfolioId() { return portfolioId; }
    public UUID getInstrumentId() { return instrumentId; }
    public String getProvider() { return provider; }
    public String getModelUsed() { return modelUsed; }
    public String getStance() { return stance; }
    public int getRiskScore() { return riskScore; }
    public String getRiskLevel() { return riskLevel; }
    public String getExecutiveSummary() { return executiveSummary; }
    public String getResultJson() { return resultJson; }
    public Instant getEvaluatedAt() { return evaluatedAt; }

    public void setProvider(String provider) { this.provider = provider; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    public void setStance(String stance) { this.stance = stance; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public void setExecutiveSummary(String executiveSummary) { this.executiveSummary = executiveSummary; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
