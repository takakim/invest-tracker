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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    @DisplayName("Transaction in-place update (PUT) updates fields and synchronizes positions across instruments")
    void transactionInPlaceUpdateLifecycleAndPositionSync() throws Exception {
        // 1. Create Portfolio & Account
        String pResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"InPlace Portfolio","baseCurrency":"GBP","costBasisMethod":"FIFO","returnMethod":"XIRR"}
                            """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(pResp, "$.id");

        String aResp = mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Dealing Account","brokerName":"Vanguard","accountCurrency":"GBP"}
                            """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accountId = JsonPath.read(aResp, "$.id");

        // 2. Create Two Instruments
        String i1Resp = mockMvc.perform(post("/api/v1/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Alphabet Inc","assetClass":"STOCK","ticker":"GOOGL","isin":"US02079K3059","exchange":"NASDAQ","currency":"USD"}
                            """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String instrument1Id = JsonPath.read(i1Resp, "$.id");

        String i2Resp = mockMvc.perform(post("/api/v1/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name":"Amazon.com Inc","assetClass":"STOCK","ticker":"AMZN","isin":"US0231351067","exchange":"NASDAQ","currency":"USD"}
                            """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String instrument2Id = JsonPath.read(i2Resp, "$.id");

        // 3. Record initial BUY of Alphabet (quantity: 10, price: 100, gross: 1000, fee: 10)
        String buyJson = String.format("""
            {"instrumentId":"%s","type":"BUY","tradeDate":"%s","quantity":10.0,"price":100.0,"grossAmount":1000.0,"feeAmount":10.0,"currency":"USD","notes":"Initial GOOGL purchase"}
            """, instrument1Id, Instant.now().toString());

        String buyResp = mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String txId = JsonPath.read(buyResp, "$.id");

        // Verify initial position for GOOGL
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].instrumentTicker").value("GOOGL"))
                .andExpect(jsonPath("$[0].quantity").value(10.0))
                .andExpect(jsonPath("$[0].costBasisAmount").value(1010.0));

        // 4. Update BUY in-place: increase quantity to 15, price to 120, fee to 15
        String updateJson = String.format("""
            {"instrumentId":"%s","type":"BUY","tradeDate":"%s","quantity":15.0,"price":120.0,"grossAmount":1800.0,"feeAmount":15.0,"currency":"USD","notes":"Corrected GOOGL purchase"}
            """, instrument1Id, Instant.now().toString());

        mockMvc.perform(put("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions/" + txId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(txId))
                .andExpect(jsonPath("$.quantity").value(15.0))
                .andExpect(jsonPath("$.price").value(120.0))
                .andExpect(jsonPath("$.grossAmount").value(1800.0))
                .andExpect(jsonPath("$.feeAmount").value(15.0))
                .andExpect(jsonPath("$.netAmount").value(1815.0))
                .andExpect(jsonPath("$.notes").value("Corrected GOOGL purchase"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Verify position for GOOGL has been updated in-place
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].instrumentTicker").value("GOOGL"))
                .andExpect(jsonPath("$[0].quantity").value(15.0))
                .andExpect(jsonPath("$[0].costBasisAmount").value(1815.0));

        // 5. Update transaction to switch instrument from GOOGL to AMZN
        String updateInstJson = String.format("""
            {"instrumentId":"%s","type":"BUY","tradeDate":"%s","quantity":8.0,"price":150.0,"grossAmount":1200.0,"feeAmount":5.0,"currency":"USD","notes":"Actually bought AMZN"}
            """, instrument2Id, Instant.now().toString());

        mockMvc.perform(put("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions/" + txId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateInstJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(txId))
                .andExpect(jsonPath("$.instrumentTicker").value("AMZN"))
                .andExpect(jsonPath("$.quantity").value(8.0))
                .andExpect(jsonPath("$.netAmount").value(1205.0));

        // Verify active positions: GOOGL is archived (0 active), AMZN is active (qty 8)
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].instrumentTicker").value("AMZN"))
                .andExpect(jsonPath("$[0].quantity").value(8.0))
                .andExpect(jsonPath("$[0].costBasisAmount").value(1205.0));

        // 6. Correct the transaction (mark original as CORRECTED)
        String correctionJson = String.format("""
            {"replacementInstrumentId":"%s","replacementType":"BUY","replacementTradeDate":"%s","replacementQuantity":8.0,"replacementPrice":150.0,"replacementGrossAmount":1200.0,"replacementFeeAmount":5.0,"replacementCurrency":"USD"}
            """, instrument2Id, Instant.now().toString());

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions/" + txId + "/correct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionJson))
                .andExpect(status().isCreated());

        // 7. Updating an already CORRECTED transaction should fail with 400 Bad Request
        mockMvc.perform(put("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions/" + txId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateInstJson))
                .andExpect(status().isBadRequest());

        // 8. Updating a non-existent transaction should return 404
        mockMvc.perform(put("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/transactions/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateInstJson))
                .andExpect(status().isNotFound());
    }
}
