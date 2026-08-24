package com.takakim.investtracker;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransactionIntegrationTests {

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
    @DisplayName("Transaction recording, position auto-recalculation, and correction lifecycle")
    void transactionLifecycleAndPositionSync() throws Exception {
        // 1. Create Portfolio
        String portfolioJson = """
            {"name":"Trading Portfolio","baseCurrency":"GBP","costBasisMethod":"FIFO","returnMethod":"XIRR"}
            """;
        String pResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(portfolioJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(pResp, "$.id");

        // 2. Create Account
        String accountJson = """
            {"name":"Interactive Brokers ISA","brokerName":"Interactive Brokers","accountCurrency":"GBP"}
            """;
        String aResp = mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accountId = JsonPath.read(aResp, "$.id");

        // 3. Create Instrument
        String instJson = """
            {"name":"Apple Inc","assetClass":"STOCK","ticker":"AAPL","isin":"US0378331005","exchange":"NASDAQ","currency":"USD"}
            """;
        String iResp = mockMvc.perform(post("/api/v1/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String instrumentId = JsonPath.read(iResp, "$.id");

        // 4. Record DEPOSIT transaction (Cash)
        String depositJson = String.format("""
            {"type":"DEPOSIT","tradeDate":"%s","grossAmount":10000.0,"currency":"GBP","notes":"Initial Deposit"}
            """, Instant.now().toString());

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.netAmount").value(10000.0));

        // 5. Record BUY transaction -> auto-initializes Position for Apple Inc
        String buyJson = String.format("""
            {"instrumentId":"%s","type":"BUY","tradeDate":"%s","quantity":10.0,"price":150.0,"grossAmount":1500.0,"feeAmount":5.0,"currency":"USD"}
            """, instrumentId, Instant.now().toString());

        String buyResp = mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("BUY"))
                .andExpect(jsonPath("$.grossAmount").value(1500.0))
                .andExpect(jsonPath("$.netAmount").value(1505.0))
                .andReturn().getResponse().getContentAsString();
        String buyTxId = JsonPath.read(buyResp, "$.id");

        // 6. Verify Position Holdings auto-recalculated to quantity = 10.0
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].quantity").value(10.0))
                .andExpect(jsonPath("$[0].costBasisAmount").value(1505.0));

        // 7. Record SELL transaction -> auto-reduces Position quantity to 4.0
        String sellJson = String.format("""
            {"instrumentId":"%s","type":"SELL","tradeDate":"%s","quantity":6.0,"price":180.0,"grossAmount":1080.0,"feeAmount":5.0,"taxAmount":10.0,"currency":"USD"}
            """, instrumentId, Instant.now().toString());

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sellJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SELL"))
                .andExpect(jsonPath("$.netAmount").value(1065.0));

        // Verify Position Holdings auto-updated to quantity = 4.0
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantity").value(4.0));

        // 8. List transactions filtered by type
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions?type=BUY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(buyTxId));

        // 9. Correct BUY transaction (mark CORRECTED & replacement)
        String correctionJson = String.format("""
            {"replacementInstrumentId":"%s","replacementType":"BUY","replacementTradeDate":"%s","replacementQuantity":12.0,"replacementPrice":150.0,"replacementGrossAmount":1800.0,"replacementFeeAmount":5.0,"replacementCurrency":"USD"}
            """, instrumentId, Instant.now().toString());

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions/" + buyTxId + "/correct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(12.0))
                .andExpect(jsonPath("$.correctionOfTransactionId").value(buyTxId));

        // Verify original transaction is now marked CORRECTED
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions/" + buyTxId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CORRECTED"));

        // Attempting to correct an already corrected transaction -> 400 Bad Request
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions/" + buyTxId + "/correct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionJson))
                .andExpect(status().isBadRequest());

        // 10. List portfolio-level transactions across accounts
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        // 11. Record STOCK_SPLIT transaction -> increases position quantity by 4.0
        String splitJson = String.format("""
            {"instrumentId":"%s","type":"STOCK_SPLIT","tradeDate":"%s","quantity":4.0,"grossAmount":0.0,"currency":"USD"}
            """, instrumentId, Instant.now().toString());

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(splitJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("STOCK_SPLIT"));

        // 12. Record REVERSE_STOCK_SPLIT transaction -> reduces position quantity by 2.0
        String revSplitJson = String.format("""
            {"instrumentId":"%s","type":"REVERSE_STOCK_SPLIT","tradeDate":"%s","quantity":2.0,"grossAmount":0.0,"currency":"USD"}
            """, instrumentId, Instant.now().toString());

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(revSplitJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("REVERSE_STOCK_SPLIT"));

        // 13. Record SELL transaction selling more than remaining position -> auto resets totalQuantity to 0
        String sellExcessJson = String.format("""
            {"instrumentId":"%s","type":"SELL","tradeDate":"%s","quantity":100.0,"price":200.0,"grossAmount":20000.0,"currency":"USD"}
            """, instrumentId, Instant.now().toString());

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sellExcessJson))
                .andExpect(status().isCreated());

        // 14. Record and correct a DIVIDEND non-trade transaction
        String divJson = String.format("""
            {"instrumentId":"%s","type":"DIVIDEND","tradeDate":"%s","grossAmount":50.0,"currency":"USD"}
            """, instrumentId, Instant.now().toString());

        String divResp = mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(divJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String divTxId = JsonPath.read(divResp, "$.id");

        String divCorrectionJson = String.format("""
            {"replacementInstrumentId":"%s","replacementType":"DIVIDEND","replacementTradeDate":"%s","replacementGrossAmount":60.0,"replacementCurrency":"USD"}
            """, instrumentId, Instant.now().toString());

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions/" + divTxId + "/correct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(divCorrectionJson))
                .andExpect(status().isCreated());

        // 15. List all transactions for account
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9));
    }

    @Test
    @DisplayName("Transaction error paths")
    void transactionErrorPaths() throws Exception {
        UUID randomId = UUID.randomUUID();

        // 404 for missing portfolio
        mockMvc.perform(get("/api/v1/portfolios/" + randomId + "/accounts/" + randomId + "/transactions"))
                .andExpect(status().isNotFound());

        // 404 for portfolio transactions missing portfolio
        mockMvc.perform(get("/api/v1/portfolios/" + randomId + "/transactions"))
                .andExpect(status().isNotFound());

        // 400 for missing grossAmount
        String invalidReq = """
            {"type":"DEPOSIT","tradeDate":"2026-08-24T12:00:00Z","currency":"GBP"}
            """;
        mockMvc.perform(post("/api/v1/portfolios/" + randomId + "/accounts/" + randomId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidReq))
                .andExpect(status().isBadRequest());
    }
}
