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
class RebalancingIntegrationTests {

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
    @DisplayName("Target Allocation & Rebalancing lifecycle: save plan, calculate drift, cash injection, and delete")
    void targetAllocationAndRebalancingLifecycle() throws Exception {
        // 1. Create Portfolio
        String portJson = """
            {"name":"Rebalancing Portfolio","baseCurrency":"GBP","costBasisMethod":"FIFO","returnMethod":"TWR"}
            """;
        String portResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(portJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(portResp, "$.id");

        // 2. Create Account
        String accJson = """
            {"name":"Rebalancing Account","brokerName":"Trading212","accountCurrency":"GBP"}
            """;
        String accResp = mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/accounts", portfolioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accountId = JsonPath.read(accResp, "$.id");

        // 3. Create Instrument (Stock)
        String instJson = """
            {"name":"Microsoft Corp","assetClass":"STOCK","ticker":"MSFT","currency":"USD"}
            """;
        String instResp = mockMvc.perform(post("/api/v1/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String instId = JsonPath.read(instResp, "$.id");

        // 4. Deposit Cash 10,000 GBP
        String depositJson = """
            {"type":"DEPOSIT","tradeDate":"2026-08-01T10:00:00Z","grossAmount":10000.00,"currency":"GBP"}
            """;
        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/transactions", portfolioId, accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositJson))
                .andExpect(status().isCreated());

        // 5. Initial GET Target Allocation -> 404
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/target-allocation", portfolioId))
                .andExpect(status().isNotFound());

        // 6. Save Target Allocation Plan (60% STOCK, 40% CASH)
        String planJson = """
            {
              "name": "60/40 Equity Cash",
              "allocationType": "ASSET_CLASS",
              "driftTolerancePercentage": 5.00,
              "items": [
                {"categoryKey": "STOCK", "categoryLabel": "Equities", "targetPercentage": 60.00},
                {"categoryKey": "CASH", "categoryLabel": "Cash", "targetPercentage": 40.00}
              ]
            }
            """;

        mockMvc.perform(put("/api/v1/portfolios/{portfolioId}/target-allocation", portfolioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("60/40 Equity Cash")))
                .andExpect(jsonPath("$.allocationType", is("ASSET_CLASS")))
                .andExpect(jsonPath("$.driftTolerancePercentage", is(5.00)))
                .andExpect(jsonPath("$.items", hasSize(2)));

        // 7. GET Target Allocation Plan -> 200
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/target-allocation", portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("60/40 Equity Cash")))
                .andExpect(jsonPath("$.items[0].categoryKey", is("STOCK")));

        // 8. GET Rebalancing Analysis (Full Rebalance)
        // Currently 10,000 CASH (100%), 0 STOCK (0%).
        // Target is 6,000 STOCK (BUY 6,000), 4,000 CASH (SELL/spend 6,000)
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/rebalancing", portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId", is(portfolioId)))
                .andExpect(jsonPath("$.totalPortfolioValue", is(10000.00)))
                .andExpect(jsonPath("$.hasDriftToleranceExceeded", is(true)))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].categoryKey", is("STOCK")))
                .andExpect(jsonPath("$.items[0].action", is("BUY")))
                .andExpect(jsonPath("$.items[0].orderAmount", is(6000.00)))
                .andExpect(jsonPath("$.items[1].categoryKey", is("CASH")))
                .andExpect(jsonPath("$.items[1].action", is("SELL")))
                .andExpect(jsonPath("$.items[1].orderAmount", is(6000.00)));

        // 9. GET Rebalancing Analysis with Cash Injection (+5,000 GBP)
        // Total post value = 15,000 GBP. Ideal STOCK = 9,000 (shortfall = 9,000). Ideal CASH = 6,000 (currently 10,000).
        // Cash injection distributes 5,000 to BUY STOCK without selling any cash!
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/rebalancing", portfolioId)
                        .param("cashInjection", "5000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashInjectionAmount", is(5000.00)))
                .andExpect(jsonPath("$.totalPostRebalanceValue", is(15000.00)))
                .andExpect(jsonPath("$.items[0].categoryKey", is("STOCK")))
                .andExpect(jsonPath("$.items[0].action", is("BUY")))
                .andExpect(jsonPath("$.items[0].orderAmount", is(5000.00)))
                .andExpect(jsonPath("$.items[1].categoryKey", is("CASH")))
                .andExpect(jsonPath("$.items[1].action", is("HOLD")));

        // 10. Delete Target Allocation Plan
        mockMvc.perform(delete("/api/v1/portfolios/{portfolioId}/target-allocation", portfolioId))
                .andExpect(status().isNoContent());

        // 11. Verify Plan is Deleted -> 404
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/target-allocation", portfolioId))
                .andExpect(status().isNotFound());
    }
}
