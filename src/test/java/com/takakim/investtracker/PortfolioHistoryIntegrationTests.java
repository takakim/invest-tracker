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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PortfolioHistoryIntegrationTests {

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
    @DisplayName("GET /api/v1/portfolios/{id}/history returns timeline valuation points and performance summary")
    void getPortfolioHistoryTimelineAndSummary() throws Exception {
        // 1. Create Portfolio
        String portJson = """
            {"name":"Historical Growth Portfolio","baseCurrency":"GBP","costBasisMethod":"FIFO","returnMethod":"TWR"}
            """;
        String portResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(portJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(portResp, "$.id");

        // 2. Create Account
        String accJson = """
            {"name":"ISA Account","brokerName":"Interactive Investor","accountCurrency":"GBP"}
            """;
        String accResp = mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/accounts", portfolioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accountId = JsonPath.read(accResp, "$.id");

        // 3. Deposit Cash
        String depJson = """
            {"type":"DEPOSIT","tradeDate":"2025-01-15T10:00:00Z","grossAmount":10000.00,"currency":"GBP"}
            """;
        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/transactions", portfolioId, accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depJson))
                .andExpect(status().isCreated());

        // 4. Create Instrument
        String instJson = """
            {"name":"Vanguard S&P 500 ETF","assetClass":"ETF","ticker":"VUSA","isin":"IE00B3XXRP09","currency":"GBP"}
            """;
        String instResp = mockMvc.perform(post("/api/v1/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String instrumentId = JsonPath.read(instResp, "$.id");

        // 5. Query history for 1Y
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/history", portfolioId)
                        .param("period", "1Y")
                        .param("interval", "MONTHLY")
                        .param("benchmarkId", instrumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId", is(portfolioId)))
                .andExpect(jsonPath("$.portfolioName", is("Historical Growth Portfolio")))
                .andExpect(jsonPath("$.baseCurrency", is("GBP")))
                .andExpect(jsonPath("$.period", is("1Y")))
                .andExpect(jsonPath("$.interval", is("MONTHLY")))
                .andExpect(jsonPath("$.dataPoints", not(empty())))
                .andExpect(jsonPath("$.summary.startingValue", notNullValue()))
                .andExpect(jsonPath("$.summary.endingValue", notNullValue()));

        // 6. Test POST /api/v1/portfolios/{id}/history/backfill
        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/history/backfill", portfolioId)
                        .param("range", "1Y"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId", is(portfolioId)))
                .andExpect(jsonPath("$.instrumentsProcessed", notNullValue()))
                .andExpect(jsonPath("$.totalObservationsSynced", notNullValue()))
                .andExpect(jsonPath("$.details", notNullValue()));
    }
}
