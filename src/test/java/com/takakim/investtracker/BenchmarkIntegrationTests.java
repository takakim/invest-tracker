package com.takakim.investtracker;

import com.jayway.jsonpath.JsonPath;
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
class BenchmarkIntegrationTests {

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
    @DisplayName("Benchmark comparison lifecycle: fetches available benchmarks and compares portfolio vs benchmark")
    void benchmarkLifecycle() throws Exception {
        // 1. Create Portfolio
        String portJson = """
            {"name":"Benchmark Portfolio","baseCurrency":"GBP","costBasisMethod":"FIFO","returnMethod":"TWR"}
            """;
        String portResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(portJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(portResp, "$.id");

        // 2. Create Benchmark Instrument (S&P 500 ETF)
        String instJson = """
            {"name":"Vanguard S&P 500 ETF","assetClass":"ETF","ticker":"VUSA","currency":"GBP"}
            """;
        String instResp = mockMvc.perform(post("/api/v1/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String benchmarkId = JsonPath.read(instResp, "$.id");

        // 3. Record price override for benchmark
        String quoteJson = """
            {"price":60.00,"currency":"GBP"}
            """;
        mockMvc.perform(post("/api/v1/instruments/{id}/quotes/override", benchmarkId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson))
                .andExpect(status().isCreated());

        // 4. Fetch available benchmarks
        mockMvc.perform(get("/api/v1/benchmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 5. Compare portfolio to benchmark
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/benchmark-comparison", portfolioId)
                        .param("benchmarkInstrumentId", benchmarkId)
                        .param("period", "1Y"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value(portfolioId))
                .andExpect(jsonPath("$.benchmarkInstrumentId").value(benchmarkId))
                .andExpect(jsonPath("$.benchmarkTicker").value("VUSA"))
                .andExpect(jsonPath("$.baseCurrency").value("GBP"))
                .andExpect(jsonPath("$.portfolioReturn").exists())
                .andExpect(jsonPath("$.benchmarkReturn").exists())
                .andExpect(jsonPath("$.excessReturn").exists());
    }
}
