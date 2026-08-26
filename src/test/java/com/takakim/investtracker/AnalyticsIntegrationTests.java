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
class AnalyticsIntegrationTests {

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
    @DisplayName("Analytics and CSV Export lifecycle: creates portfolio, transactions, quotes, fetches analytics & downloads CSVs")
    void analyticsAndExportLifecycle() throws Exception {
        // 1. Create Portfolio
        String portJson = """
            {"name":"Analytics Wealth","baseCurrency":"GBP","costBasisMethod":"FIFO","returnMethod":"TWR"}
            """;
        String portResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(portJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(portResp, "$.id");

        // 2. Create Account
        String accJson = """
            {"name":"Main Trading Account","brokerName":"Interactive Brokers","accountCurrency":"GBP"}
            """;
        String accResp = mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/accounts", portfolioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accountId = JsonPath.read(accResp, "$.id");

        // 3. Create Instrument (AAPL)
        String instJson = """
            {"name":"Apple Inc","assetClass":"STOCK","ticker":"AAPL","currency":"USD"}
            """;
        String instResp = mockMvc.perform(post("/api/v1/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String instrumentId = JsonPath.read(instResp, "$.id");

        // 4. Deposit 2000 GBP cash
        String depJson = """
            {"type":"DEPOSIT","tradeDate":"2026-08-20T10:00:00Z","grossAmount":2000.00,"currency":"GBP"}
            """;
        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/transactions", portfolioId, accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depJson))
                .andExpect(status().isCreated());

        // 5. Buy 10 AAPL @ 150 USD
        String buyJson = String.format("""
            {"type":"BUY","instrumentId":"%s","tradeDate":"2026-08-20T10:30:00Z","quantity":10,"price":150.00,"grossAmount":1500.00,"currency":"USD"}
            """, instrumentId);
        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/transactions", portfolioId, accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyJson))
                .andExpect(status().isCreated());

        // 6. Test Analytics Endpoint
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/analytics", portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId", is(portfolioId)))
                .andExpect(jsonPath("$.baseCurrency", is("GBP")))
                .andExpect(jsonPath("$.totalCurrentValue", notNullValue()))
                .andExpect(jsonPath("$.totalCostBasis", notNullValue()))
                .andExpect(jsonPath("$.byAssetClass", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.byCurrency", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.topHoldings", hasSize(1)))
                .andExpect(jsonPath("$.topHoldings[0].ticker", is("AAPL")));

        // 7. Test Export Positions CSV Endpoint
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/export/positions.csv", portfolioId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"positions-")))
                .andExpect(content().string(containsString("Account,Instrument Name,Ticker,ISIN,Asset Class")))
                .andExpect(content().string(containsString("Apple Inc")))
                .andExpect(content().string(containsString("AAPL")));

        // 8. Test Export Transactions CSV Endpoint
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/export/transactions.csv", portfolioId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"transactions-")))
                .andExpect(content().string(containsString("Date,Type,Account,Instrument,Ticker")))
                .andExpect(content().string(containsString("DEPOSIT")))
                .andExpect(content().string(containsString("BUY")));
    }
}
