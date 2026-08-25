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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PositionIntegrationTests {

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
    @DisplayName("Complete CRUD lifecycle and invariants for Position holdings")
    void positionCrudLifecycle() throws Exception {
        // 1. Create Portfolio
        String portfolioJson = """
            {"name":"Growth Portfolio","baseCurrency":"GBP","costBasisMethod":"FIFO","returnMethod":"XIRR"}
            """;
        String pResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(portfolioJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(pResp, "$.id");

        // 2. Create Account under Portfolio
        String accountJson = """
            {"name":"Trading 212 ISA","brokerName":"Trading 212","accountCurrency":"GBP"}
            """;
        String aResp = mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accountId = JsonPath.read(aResp, "$.id");

        // 3. Create Instrument
        String instJson = """
            {"name":"Vanguard S&P 500 ETF","assetClass":"ETF","ticker":"VUAG","isin":"IE00BFMXXD85","exchange":"LSE","currency":"GBP"}
            """;
        String iResp = mockMvc.perform(post("/api/v1/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String instrumentId = JsonPath.read(iResp, "$.id");

        // 4. Create Position
        String posJson = String.format("""
            {"instrumentId":"%s","quantity":100.5,"costBasisAmount":8500.0,"costBasisCurrency":"GBP"}
            """, instrumentId);

        String posResp = mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(posJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(100.5))
                .andExpect(jsonPath("$.costBasisAmount").value(8500.0))
                .andExpect(jsonPath("$.costBasisCurrency").value("GBP"))
                .andExpect(jsonPath("$.instrumentName").value("Vanguard S&P 500 ETF"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        String positionId = JsonPath.read(posResp, "$.id");

        // 5. Attempt duplicate position for same instrument -> 409 Conflict
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(posJson))
                .andExpect(status().isConflict());

        // 6. List positions for Account
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(positionId));

        // 7. List positions for Portfolio
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(positionId));

        // 8. Update Position
        String updateJson = """
            {"quantity":150.75,"costBasisAmount":12800.5,"costBasisCurrency":"GBP"}
            """;
        mockMvc.perform(put("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/positions/" + positionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(150.75))
                .andExpect(jsonPath("$.costBasisAmount").value(12800.5));

        // 9. Archive Position
        mockMvc.perform(delete("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/positions/" + positionId))
                .andExpect(status().isNoContent());

        // 10. List positions after archive returns empty active list
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Position APIs validate input bounds and return 404 for missing resources")
    void positionErrorPaths() throws Exception {
        UUID randomId = UUID.randomUUID();

        // 404 for non-existent portfolio
        mockMvc.perform(get("/api/v1/portfolios/" + randomId + "/accounts/" + randomId + "/positions"))
                .andExpect(status().isNotFound());

        // 404 for portfolio positions non-existent portfolio
        mockMvc.perform(get("/api/v1/portfolios/" + randomId + "/positions"))
                .andExpect(status().isNotFound());

        // 400 for negative quantity
        String invalidReq = String.format("""
            {"instrumentId":"%s","quantity":-5.0}
            """, randomId);
        mockMvc.perform(post("/api/v1/portfolios/" + randomId + "/accounts/" + randomId + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidReq))
                .andExpect(status().isBadRequest());

        // 404 for getPosition with random ID
        mockMvc.perform(get("/api/v1/portfolios/" + randomId + "/accounts/" + randomId + "/positions/" + randomId))
                .andExpect(status().isNotFound());

        // 404 for updatePosition with random ID
        String updateReq = """
            {"quantity":10.0}
            """;
        mockMvc.perform(put("/api/v1/portfolios/" + randomId + "/accounts/" + randomId + "/positions/" + randomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateReq))
                .andExpect(status().isNotFound());

        // 404 for archivePosition with random ID
        mockMvc.perform(delete("/api/v1/portfolios/" + randomId + "/accounts/" + randomId + "/positions/" + randomId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Position service error paths that require valid portfolio/account")
    void positionServiceLambdaErrorPaths() throws Exception {
        // Create a real portfolio and account
        String pResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Portfolio\",\"baseCurrency\":\"GBP\",\"costBasisMethod\":\"FIFO\",\"returnMethod\":\"XIRR\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String realPortfolioId = JsonPath.read(pResp, "$.id");

        String aResp = mockMvc.perform(post("/api/v1/portfolios/" + realPortfolioId + "/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Account\",\"brokerName\":\"Test Broker\",\"accountCurrency\":\"GBP\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String realAccountId = JsonPath.read(aResp, "$.id");

        UUID randomId = UUID.randomUUID();

        // Real portfolio, invalid accountId → triggers lambda$getValidatedAccount$0
        mockMvc.perform(get("/api/v1/portfolios/" + realPortfolioId + "/accounts/" + randomId + "/positions"))
                .andExpect(status().isNotFound());

        // Real portfolio, real account, invalid positionId → triggers lambda$getPosition$0
        mockMvc.perform(get("/api/v1/portfolios/" + realPortfolioId + "/accounts/" + realAccountId + "/positions/" + randomId))
                .andExpect(status().isNotFound());

        // Real portfolio, real account, invalid instrumentId → triggers lambda$createPosition$0
        String badInstrumentJson = String.format("""
            {"instrumentId":"%s","quantity":1.0}
            """, randomId);
        mockMvc.perform(post("/api/v1/portfolios/" + realPortfolioId + "/accounts/" + realAccountId + "/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badInstrumentJson))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Query position lots and recalculate portfolio positions")
    void positionLotsAndRecalculateFlow() throws Exception {
        // 1. Create Portfolio with FIFO
        String pResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lot Portfolio\",\"baseCurrency\":\"USD\",\"costBasisMethod\":\"FIFO\",\"returnMethod\":\"TWR\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String pId = JsonPath.read(pResp, "$.id");

        // 2. Create Account
        String aResp = mockMvc.perform(post("/api/v1/portfolios/" + pId + "/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lot Account\",\"brokerName\":\"Broker\",\"accountCurrency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String aId = JsonPath.read(aResp, "$.id");

        // 3. Create Instrument
        String iResp = mockMvc.perform(post("/api/v1/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Microsoft\",\"assetClass\":\"STOCK\",\"ticker\":\"MSFT\",\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String instId = JsonPath.read(iResp, "$.id");

        // 4. Record Buy Transaction
        String buyJson = String.format("""
            {"instrumentId":"%s","type":"BUY","tradeDate":"2026-01-01T10:00:00Z","quantity":10.0,"price":200.0,"grossAmount":2000.0,"currency":"USD"}
            """, instId);
        mockMvc.perform(post("/api/v1/portfolios/" + pId + "/accounts/" + aId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyJson))
                .andExpect(status().isCreated());

        // 5. Query Active Positions to get Position ID
        String posResp = mockMvc.perform(get("/api/v1/portfolios/" + pId + "/accounts/" + aId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String posId = JsonPath.read(posResp, "$[0].id");

        // 6. Query Lots Detail
        mockMvc.perform(get("/api/v1/portfolios/" + pId + "/accounts/" + aId + "/positions/" + posId + "/lots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionId").value(posId))
                .andExpect(jsonPath("$.totalQuantity").value(10.0))
                .andExpect(jsonPath("$.totalCostBasisAmount").value(2000.0))
                .andExpect(jsonPath("$.openLots.length()").value(1));

        // 7. Recalculate Portfolio Positions
        mockMvc.perform(post("/api/v1/portfolios/" + pId + "/positions/recalculate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value(pId))
                .andExpect(jsonPath("$.recalculatedPositionsCount").value(1));
    }
}
