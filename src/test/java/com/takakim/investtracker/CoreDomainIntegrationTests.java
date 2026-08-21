package com.takakim.investtracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CoreDomainIntegrationTests {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4");

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void portfolioLifecycleAndAccounts() throws Exception {
        String portfolio = """
            {"name":"Long Term","baseCurrency":"gbp","costBasisMethod":"FIFO","returnMethod":"XIRR"}
            """;
        String id = mockMvc.perform(post("/api/v1/portfolios").contentType(MediaType.APPLICATION_JSON).content(portfolio))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Long Term"))
            .andExpect(jsonPath("$.baseCurrency").value("GBP")).andReturn().getResponse().getContentAsString();
        String portfolioId = com.jayway.jsonpath.JsonPath.read(id, "$.id");

        mockMvc.perform(get("/api/v1/portfolios")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(portfolioId));
        mockMvc.perform(get("/api/v1/portfolios/{id}", portfolioId)).andExpect(status().isOk()).andExpect(jsonPath("$.costBasisMethod").value("FIFO"));

        String update = """{"name":"Long Term Updated","baseCurrency":"USD","costBasisMethod":"AVERAGE_COST","returnMethod":"TWR"}""";
        mockMvc.perform(put("/api/v1/portfolios/{id}", portfolioId).contentType(MediaType.APPLICATION_JSON).content(update))
            .andExpect(status().isOk()).andExpect(jsonPath("$.baseCurrency").value("USD"));

        String account = """{"name":"Main","brokerName":"Example Broker","accountCurrency":"GBP"}""";
        String accountJson = mockMvc.perform(post("/api/v1/portfolios/{id}/accounts", portfolioId).contentType(MediaType.APPLICATION_JSON).content(account))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.portfolioId").value(portfolioId)).andReturn().getResponse().getContentAsString();
        String accountId = com.jayway.jsonpath.JsonPath.read(accountJson, "$.id");
        mockMvc.perform(get("/api/v1/portfolios/{id}/accounts", portfolioId)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(accountId));
        mockMvc.perform(get("/api/v1/portfolios/{id}/accounts/{accountId}", portfolioId, accountId)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Main"));
        mockMvc.perform(put("/api/v1/portfolios/{id}/accounts/{accountId}", portfolioId, accountId).contentType(MediaType.APPLICATION_JSON).content("""{"name":"Main 2","brokerName":"Broker 2","accountCurrency":"EUR"}"""))
            .andExpect(status().isOk()).andExpect(jsonPath("$.accountCurrency").value("EUR"));
        mockMvc.perform(delete("/api/v1/portfolios/{id}/accounts/{accountId}", portfolioId, accountId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/portfolios/{id}/accounts", portfolioId)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void archivedPortfolioCannotAcceptAccounts() throws Exception {
        String portfolio = """{"name":"Archive Me","baseCurrency":"GBP","costBasisMethod":"LIFO","returnMethod":"MWR"}""";
        String json = mockMvc.perform(post("/api/v1/portfolios").contentType(MediaType.APPLICATION_JSON).content(portfolio))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String portfolioId = com.jayway.jsonpath.JsonPath.read(json, "$.id");
        mockMvc.perform(delete("/api/v1/portfolios/{id}", portfolioId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/portfolios/{id}", portfolioId)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(post("/api/v1/portfolios/{id}/accounts", portfolioId).contentType(MediaType.APPLICATION_JSON).content("""{"name":"A","brokerName":"B","accountCurrency":"GBP"}"""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void instrumentLifecycleAndValidation() throws Exception {
        String instrument = """{"name":"Acme","assetClass":"STOCK","ticker":"ACME","isin":"GB00ACME1234","exchange":"LSE","currency":"gbp"}""";
        String json = mockMvc.perform(post("/api/v1/instruments").contentType(MediaType.APPLICATION_JSON).content(instrument))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.currency").value("GBP")).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(json, "$.id");
        mockMvc.perform(get("/api/v1/instruments")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mockMvc.perform(get("/api/v1/instruments/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.ticker").value("ACME"));
        mockMvc.perform(post("/api/v1/instruments").contentType(MediaType.APPLICATION_JSON).content(instrument)).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/instruments").contentType(MediaType.APPLICATION_JSON).content("""{"name":"","assetClass":"STOCK","currency":"GBP"}""")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/portfolios/00000000-0000-0000-0000-000000000000")).andExpect(status().isNotFound());
    }
}
