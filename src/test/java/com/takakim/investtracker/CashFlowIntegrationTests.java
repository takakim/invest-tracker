package com.takakim.investtracker;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CashFlowIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    @DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/portfolios/{id}/cash-flows returns periodic breakdowns and summary")
    void getPortfolioCashFlowsAndExport() throws Exception {
        // 1. Create Portfolio
        String portJson = """
            {"name":"Cash Flow Portfolio","baseCurrency":"GBP","costBasisMethod":"FIFO","returnMethod":"TWR"}
            """;
        String portResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(portJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(portResp, "$.id");

        // 2. Create Account
        String accJson = """
            {"name":"ISA Savings","brokerName":"Vanguard","accountCurrency":"GBP"}
            """;
        String accResp = mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/accounts", portfolioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accountId = JsonPath.read(accResp, "$.id");

        // 3. Deposit Cash (Jan 2026)
        String dep1Json = """
            {"type":"DEPOSIT","tradeDate":"2026-01-15T10:00:00Z","grossAmount":5000.00,"currency":"GBP"}
            """;
        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/transactions", portfolioId, accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dep1Json))
                .andExpect(status().isCreated());

        // 4. Deposit Cash (Feb 2026)
        String dep2Json = """
            {"type":"DEPOSIT","tradeDate":"2026-02-15T10:00:00Z","grossAmount":3000.00,"currency":"GBP"}
            """;
        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/transactions", portfolioId, accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dep2Json))
                .andExpect(status().isCreated());

        // 5. Withdrawal Cash (Feb 2026)
        String withJson = """
            {"type":"WITHDRAWAL","tradeDate":"2026-02-20T10:00:00Z","grossAmount":1000.00,"currency":"GBP"}
            """;
        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/transactions", portfolioId, accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withJson))
                .andExpect(status().isCreated());

        // 6. Query cash flows endpoint
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/cash-flows", portfolioId)
                        .param("period", "ALL")
                        .param("groupBy", "MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId", is(portfolioId)))
                .andExpect(jsonPath("$.portfolioName", is("Cash Flow Portfolio")))
                .andExpect(jsonPath("$.baseCurrency", is("GBP")))
                .andExpect(jsonPath("$.summary.totalDeposits", is(8000.00)))
                .andExpect(jsonPath("$.summary.totalWithdrawals", is(1000.00)))
                .andExpect(jsonPath("$.summary.netContributions", is(7000.00)))
                .andExpect(jsonPath("$.summary.activeContributionMonths", is(2)))
                .andExpect(jsonPath("$.summary.avgMonthlyContribution", is(3500.00)))
                .andExpect(jsonPath("$.summary.cumulativeContributions", is(7000.00)))
                .andExpect(jsonPath("$.periods", not(empty())))
                .andExpect(jsonPath("$.accountBreakdown", hasSize(1)))
                .andExpect(jsonPath("$.accountBreakdown[0].accountName", is("ISA Savings")))
                .andExpect(jsonPath("$.accountBreakdown[0].netContributions", is(7000.00)));

        // 7. Test CSV export endpoint
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/export/cash-flows.csv", portfolioId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("cash-flows-" + portfolioId + ".csv")))
                .andExpect(content().string(containsString("Period,Start Date,End Date,Deposits,Withdrawals,Net Contributions,Internal Income,Cumulative Contributions,Currency")))
                .andExpect(content().string(containsString("GBP")));
    }
}
