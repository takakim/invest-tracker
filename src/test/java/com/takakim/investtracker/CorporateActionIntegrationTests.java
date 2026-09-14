package com.takakim.investtracker;

import com.jayway.jsonpath.JsonPath;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CorporateAction;
import com.takakim.investtracker.domain.CorporateActionStatus;
import com.takakim.investtracker.domain.CorporateActionType;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.repository.CorporateActionRepository;
import com.takakim.investtracker.repository.InstrumentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
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
class CorporateActionIntegrationTests {

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

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private CorporateActionRepository corporateActionRepository;

    @Test
    @DisplayName("End-to-end corporate actions flow: discovery, split application, dividend application, and dismissal")
    void corporateActionsLifecycle() throws Exception {
        // 1. Create Portfolio
        String portJson = """
            {"name":"Corporate Actions Portfolio","baseCurrency":"USD","costBasisMethod":"FIFO","returnMethod":"TWR"}
            """;
        String portResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(portJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(portResp, "$.id");

        // 2. Create Account
        String accJson = """
            {"name":"Trading Account","brokerName":"Interactive Brokers","accountCurrency":"USD"}
            """;
        String accResp = mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/accounts", portfolioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accountId = JsonPath.read(accResp, "$.id");

        // 3. Create Instrument (Nvidia)
        Instrument nvda = new Instrument(
                "NVIDIA Corporation", AssetClass.STOCK, "NVDA", "US67066G1040", "NASDAQ", new Currency("USD")
        );
        nvda = instrumentRepository.save(nvda);
        UUID instrumentId = nvda.getId();

        // 4. Record Initial Buy Transaction: 10 shares @ $120.00
        Instant buyDate = Instant.now().minusSeconds(86400 * 30);
        String buyJson = String.format("""
            {
                "instrumentId": "%s",
                "type": "BUY",
                "tradeDate": "%s",
                "quantity": 10.00000000,
                "price": 120.0000,
                "grossAmount": 1200.0000,
                "feeAmount": 0.0000,
                "taxAmount": 0.0000,
                "currency": "USD"
            }
            """, instrumentId, buyDate.toString());

        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/accounts/{accountId}/transactions", portfolioId, accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyJson))
                .andExpect(status().isCreated());

        // Verify initial position: 10 shares
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/positions", portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].quantity", is(10.0)));

        // 5. Ingest a 10-for-1 Stock Split Corporate Action
        Instant exDate = Instant.now().minusSeconds(86400 * 10);
        CorporateAction splitAction = new CorporateAction(
                nvda,
                CorporateActionType.STOCK_SPLIT,
                exDate,
                null,
                null,
                BigDecimal.ONE,
                BigDecimal.TEN,
                null,
                null,
                "10-for-1 forward stock split",
                "YAHOO_FINANCE",
                "YF-NVDA-SPLIT-" + exDate.getEpochSecond()
        );
        splitAction = corporateActionRepository.save(splitAction);
        UUID splitActionId = splitAction.getId();

        // 6. List pending actions
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/corporate-actions?status=PENDING", portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(splitActionId.toString())))
                .andExpect(jsonPath("$[0].ticker", is("NVDA")))
                .andExpect(jsonPath("$[0].actionType", is("STOCK_SPLIT")))
                .andExpect(jsonPath("$[0].heldQuantityAtExDate", is(10.0)))
                .andExpect(jsonPath("$[0].proposedImpactQuantity", is(90.0))); // 10 * (10 - 1) = +90 shares

        // 7. Apply the Stock Split to the Account
        String applySplitJson = String.format("""
            {
                "accountId": "%s"
            }
            """, accountId);

        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/corporate-actions/{actionId}/apply", portfolioId, splitActionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applySplitJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPLIED")))
                .andExpect(jsonPath("$.appliedTransactionId", notNullValue()));

        // 8. Verify position is now 100 shares! (10 original + 90 split)
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/positions", portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].quantity", is(100.0)));

        // 9. Ingest a Dividend Corporate Action: $0.10/share
        Instant divDate = Instant.now().minusSeconds(86400 * 2);
        CorporateAction divAction = new CorporateAction(
                nvda,
                CorporateActionType.DIVIDEND,
                divDate,
                null,
                null,
                null,
                null,
                new BigDecimal("0.1000"),
                "USD",
                "Quarterly Cash Dividend $0.10/shr",
                "YAHOO_FINANCE",
                "YF-NVDA-DIV-" + divDate.getEpochSecond()
        );
        divAction = corporateActionRepository.save(divAction);
        UUID divActionId = divAction.getId();

        // Check proposed dividend amount: 100 shares * $0.10 = $10.00
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/corporate-actions?status=PENDING", portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].proposedImpactAmount", is(10.0)));

        // Apply dividend
        String applyDivJson = String.format("""
            {
                "accountId": "%s",
                "grossAmount": 10.0000,
                "taxAmount": 1.5000
            }
            """, accountId);

        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/corporate-actions/{actionId}/apply", portfolioId, divActionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyDivJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPLIED")));

        // 10. Test Dismiss Action
        CorporateAction dismissAction = new CorporateAction(
                nvda,
                CorporateActionType.STOCK_SPLIT,
                Instant.now(),
                null,
                null,
                BigDecimal.ONE,
                new BigDecimal("2"),
                null,
                null,
                "2:1 split to dismiss",
                "MANUAL",
                null
        );
        dismissAction = corporateActionRepository.save(dismissAction);

        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/corporate-actions/{actionId}/dismiss", portfolioId, dismissAction.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DISMISSED")));
    }
}
