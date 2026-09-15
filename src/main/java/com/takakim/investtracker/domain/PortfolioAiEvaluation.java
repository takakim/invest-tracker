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
@Table(name = "portfolio_ai_evaluations")
public class PortfolioAiEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "model_used", nullable = false, length = 200)
    private String modelUsed;

    @Column(name = "overall_risk_score", nullable = false)
    private int overallRiskScore;

    @Column(name = "overall_risk_level", nullable = false, length = 20)
    private String overallRiskLevel;

    @Column(name = "executive_summary", columnDefinition = "TEXT")
    private String executiveSummary;

    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected PortfolioAiEvaluation() {}

    public PortfolioAiEvaluation(UUID portfolioId, String provider, String modelUsed,
                                  int overallRiskScore, String overallRiskLevel,
                                  String executiveSummary, String resultJson, Instant evaluatedAt) {
        this.portfolioId = portfolioId;
        this.provider = provider;
        this.modelUsed = modelUsed;
        this.overallRiskScore = overallRiskScore;
        this.overallRiskLevel = overallRiskLevel;
        this.executiveSummary = executiveSummary;
        this.resultJson = resultJson;
        this.evaluatedAt = evaluatedAt;
    }

    public UUID getId() { return id; }
    public UUID getPortfolioId() { return portfolioId; }
    public String getProvider() { return provider; }
    public String getModelUsed() { return modelUsed; }
    public int getOverallRiskScore() { return overallRiskScore; }
    public String getOverallRiskLevel() { return overallRiskLevel; }
    public String getExecutiveSummary() { return executiveSummary; }
    public String getResultJson() { return resultJson; }
    public Instant getEvaluatedAt() { return evaluatedAt; }

    public void setProvider(String provider) { this.provider = provider; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    public void setOverallRiskScore(int overallRiskScore) { this.overallRiskScore = overallRiskScore; }
    public void setOverallRiskLevel(String overallRiskLevel) { this.overallRiskLevel = overallRiskLevel; }
    public void setExecutiveSummary(String executiveSummary) { this.executiveSummary = executiveSummary; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
