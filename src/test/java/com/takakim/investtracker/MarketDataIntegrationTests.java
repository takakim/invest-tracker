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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MarketDataIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.market-data.yahoo.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Market Data lifecycle: create instrument, fetch latest quote from default provider, override price, verify history")
    void marketDataLifecycle() throws Exception {
        // 1. Create instrument with known ticker (e.g. AAPL)
        String instJson = """
            {"name":"Apple Inc","assetClass":"STOCK","ticker":"AAPL","currency":"USD"}
            """;
        String instResp = mockMvc.perform(post("/api/v1/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String instrumentId = JsonPath.read(instResp, "$.id");

        // 2. Fetch latest price quote (from DefaultMarketDataProvider)
        mockMvc.perform(get("/api/v1/instruments/{instrumentId}/quotes/latest", instrumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentId", is(instrumentId)))
                .andExpect(jsonPath("$.price", is(185.5000)))
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.sourceType", is("PROVIDER")))
                .andExpect(jsonPath("$.isStale", is(false)));

        // 3. Post manual price override
        String overrideJson = """
            {"price":195.75,"currency":"USD","reason":"Manual close adjustment"}
            """;
        mockMvc.perform(post("/api/v1/instruments/{instrumentId}/quotes/override", instrumentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentId", is(instrumentId)))
                .andExpect(jsonPath("$.price", is(195.75)))
                .andExpect(jsonPath("$.sourceType", is("MANUAL")))
                .andExpect(jsonPath("$.sourceReference", is("Manual close adjustment")));

        // 4. Latest price quote now returns the manual override
        mockMvc.perform(get("/api/v1/instruments/{instrumentId}/quotes/latest", instrumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price", is(195.75)))
                .andExpect(jsonPath("$.sourceType", is("MANUAL")));

        // 5. Query price history
        mockMvc.perform(get("/api/v1/instruments/{instrumentId}/quotes/history", instrumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)));
    }

    @Test
    @DisplayName("FX lifecycle: fetch direct rate, fetch inverse rate, override rate, verify history")
    void fxLifecycle() throws Exception {
        // 1. Fetch direct rate GBP -> USD
        mockMvc.perform(get("/api/v1/currencies/rates")
                        .param("base", "GBP")
                        .param("quote", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency", is("GBP")))
                .andExpect(jsonPath("$.quoteCurrency", is("USD")))
                .andExpect(jsonPath("$.rate", is(1.28)))
                .andExpect(jsonPath("$.sourceType", is("PROVIDER")));

        // 2. Fetch inverted rate USD -> GBP
        mockMvc.perform(get("/api/v1/currencies/rates")
                        .param("base", "USD")
                        .param("quote", "GBP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency", is("USD")))
                .andExpect(jsonPath("$.quoteCurrency", is("GBP")))
                .andExpect(jsonPath("$.isDerived", is(true)));

        // 3. Post manual FX override for GBP -> USD
        String overrideJson = """
            {"baseCurrency":"GBP","quoteCurrency":"USD","rate":1.3250,"reason":"Live broker quote"}
            """;
        mockMvc.perform(post("/api/v1/currencies/rates/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.baseCurrency", is("GBP")))
                .andExpect(jsonPath("$.quoteCurrency", is("USD")))
                .andExpect(jsonPath("$.rate", is(1.3250)))
                .andExpect(jsonPath("$.sourceType", is("MANUAL")));

        // 4. Rate query now returns the manual override
        mockMvc.perform(get("/api/v1/currencies/rates")
                        .param("base", "GBP")
                        .param("quote", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate", is(1.3250)))
                .andExpect(jsonPath("$.sourceType", is("MANUAL")));

        // 5. Query FX history
        mockMvc.perform(get("/api/v1/currencies/rates/history")
                        .param("base", "GBP")
                        .param("quote", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", notNullValue()));
    }

    @Test
    @DisplayName("Error validation for Market Data & FX endpoints")
    void errorValidation() throws Exception {
        UUID unknownId = UUID.randomUUID();

        // 404 for unknown instrument latest quote
        mockMvc.perform(get("/api/v1/instruments/{instrumentId}/quotes/latest", unknownId))
                .andExpect(status().isNotFound());

        // 404 for unknown instrument price override
        String overrideJson = """
            {"price":100.0}
            """;
        mockMvc.perform(post("/api/v1/instruments/{instrumentId}/quotes/override", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideJson))
                .andExpect(status().isNotFound());

        // 400 for negative price
        String invalidOverride = """
            {"price":-10.0}
            """;
        mockMvc.perform(post("/api/v1/instruments/{instrumentId}/quotes/override", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidOverride))
                .andExpect(status().isBadRequest());

        // 400 for invalid FX override (non-positive rate)
        String invalidFx = """
            {"baseCurrency":"USD","quoteCurrency":"EUR","rate":-1.0}
            """;
        mockMvc.perform(post("/api/v1/currencies/rates/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidFx))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Market Data refresh queue endpoints: refresh price, refresh all prices, and get queue status")
    void testMarketDataRefreshQueueEndpoints() throws Exception {
        String instJson = """
            {"name":"Tesla Inc","assetClass":"STOCK","ticker":"TSLA","currency":"USD"}
            """;
        String instResp = mockMvc.perform(post("/api/v1/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String instrumentId = JsonPath.read(instResp, "$.id");

        // Refresh single instrument (enqueues)
        mockMvc.perform(post("/api/v1/instruments/{id}/refresh-price", instrumentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(instrumentId)));

        // Refresh all instruments (enqueues)
        mockMvc.perform(post("/api/v1/instruments/refresh-prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", notNullValue()));

        // Inspect queue status endpoint
        mockMvc.perform(get("/api/v1/instruments/refresh-queue/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount", notNullValue()))
                .andExpect(jsonPath("$.processingCount", notNullValue()))
                .andExpect(jsonPath("$.failedCount", notNullValue()));
    }
}
