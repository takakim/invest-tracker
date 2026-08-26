package com.takakim.investtracker;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Performance Engine REST API.
 * Uses a real PostgreSQL instance via Testcontainers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PerformanceIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /performance returns 404 for unknown portfolio")
    void performanceUnknownPortfolioIs404() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios/{id}/performance", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TWR portfolio returns performance result with zero returns for empty portfolio")
    void twrPortfolioEmptyReturnsZero() throws Exception {
        // Create TWR portfolio
        String portfolioJson = """
            {"name":"TWR Test","baseCurrency":"USD","costBasisMethod":"FIFO","returnMethod":"TWR"}
            """;
        String pResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON).content(portfolioJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(pResp, "$.id");

        mockMvc.perform(get("/api/v1/portfolios/{id}/performance", portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId", is(portfolioId)))
                .andExpect(jsonPath("$.returnMethod", is("TWR")))
                .andExpect(jsonPath("$.twrReturn", notNullValue()))
                .andExpect(jsonPath("$.valuationBasis", is("COST_BASIS")))
                .andExpect(jsonPath("$.currency", is("USD")));
    }

    @Test
    @DisplayName("MWR portfolio returns performance result with mwrReturn non-null, twrReturn null")
    void mwrPortfolioReturnsMwr() throws Exception {
        String portfolioJson = """
            {"name":"MWR Test","baseCurrency":"GBP","costBasisMethod":"FIFO","returnMethod":"MWR"}
            """;
        String pResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON).content(portfolioJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(pResp, "$.id");

        mockMvc.perform(get("/api/v1/portfolios/{id}/performance", portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnMethod", is("MWR")))
                .andExpect(jsonPath("$.mwrReturn", notNullValue()))
                .andExpect(jsonPath("$.twrReturn").doesNotExist());
    }

    @Test
    @DisplayName("XIRR portfolio returns 200 OK with performance metrics")
    void xirrPortfolioReturns200() throws Exception {
        String portfolioJson = """
            {"name":"XIRR Test","baseCurrency":"EUR","costBasisMethod":"AVERAGE_COST","returnMethod":"XIRR"}
            """;
        String pResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON).content(portfolioJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(pResp, "$.id");

        mockMvc.perform(get("/api/v1/portfolios/{id}/performance", portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnMethod", is("XIRR")));
    }

    @Test
    @DisplayName("Full lifecycle: deposit → BUY → DIVIDEND → performance includes income")
    void fullLifecycleWithIncomeAndPositions() throws Exception {
        // 1. Create portfolio
        String portfolioJson = """
            {"name":"Income Port","baseCurrency":"USD","costBasisMethod":"FIFO","returnMethod":"TWR"}
            """;
        String pResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON).content(portfolioJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(pResp, "$.id");

        // 2. Create account
        String accountJson = """
            {"name":"Brokerage","brokerName":"Test Broker","accountCurrency":"USD"}
            """;
        String aResp = mockMvc.perform(post("/api/v1/portfolios/{id}/accounts", portfolioId)
                        .contentType(MediaType.APPLICATION_JSON).content(accountJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accountId = JsonPath.read(aResp, "$.id");

        // 3. Create instrument
        String instrumentJson = """
            {"name":"Apple Inc","assetClass":"STOCK","ticker":"AAPL","isin":"US0378331005","exchange":"NASDAQ","currency":"USD"}
            """;
        String iResp = mockMvc.perform(post("/api/v1/instruments")
                        .contentType(MediaType.APPLICATION_JSON).content(instrumentJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String instrumentId = JsonPath.read(iResp, "$.id");

        // 4. Record DEPOSIT
        String depositJson = """
            {"type":"DEPOSIT","tradeDate":"2024-01-01T00:00:00Z","grossAmount":5000,"currency":"USD"}
            """;
        mockMvc.perform(post("/api/v1/portfolios/{p}/accounts/{a}/transactions", portfolioId, accountId)
                        .contentType(MediaType.APPLICATION_JSON).content(depositJson))
                .andExpect(status().isCreated());

        // 5. Record BUY
        String buyJson = String.format("""
            {"instrumentId":"%s","type":"BUY","tradeDate":"2024-01-02T00:00:00Z",
             "quantity":10,"price":100,"grossAmount":1000,"currency":"USD"}
            """, instrumentId);
        mockMvc.perform(post("/api/v1/portfolios/{p}/accounts/{a}/transactions", portfolioId, accountId)
                        .contentType(MediaType.APPLICATION_JSON).content(buyJson))
                .andExpect(status().isCreated());

        // 6. Record DIVIDEND
        String divJson = String.format("""
            {"instrumentId":"%s","type":"DIVIDEND","tradeDate":"2024-03-01T00:00:00Z",
             "grossAmount":25,"taxAmount":3.75,"currency":"USD"}
            """, instrumentId);
        mockMvc.perform(post("/api/v1/portfolios/{p}/accounts/{a}/transactions", portfolioId, accountId)
                        .contentType(MediaType.APPLICATION_JSON).content(divJson))
                .andExpect(status().isCreated());

        // 7. GET performance and verify income is captured
        mockMvc.perform(get("/api/v1/portfolios/{id}/performance", portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnMethod", is("TWR")))
                .andExpect(jsonPath("$.totalDividendIncome").value(25.0))
                .andExpect(jsonPath("$.totalTaxes").value(3.75))
                .andExpect(jsonPath("$.totalCostBasis").isNumber())
                .andExpect(jsonPath("$.valuationBasis", is("COST_BASIS")));
    }
}
