package com.takakim.investtracker;

import com.jayway.jsonpath.JsonPath;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TaxAllowanceIntegrationTests {

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

    @Test
    @DisplayName("End-to-end tax allowance flow: accounts with tax treatment, realized gains, dividends, custom settings, and available years")
    void taxAllowanceLifecycle() throws Exception {
        // 1. Create Portfolio (GBP base currency)
        String portJson = """
            {"name":"Tax Tracking Portfolio","baseCurrency":"GBP","costBasisMethod":"FIFO","returnMethod":"TWR"}
            """;
        String portResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(portJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(portResp, "$.id");

        // 2. Create Taxable Account (GIA)
        String giaJson = """
            {"name":"General Investment Account","brokerName":"Freetrade","accountCurrency":"GBP","taxTreatment":"TAXABLE"}
            """;
        String giaResp = mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(giaJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taxTreatment", is("TAXABLE")))
                .andReturn().getResponse().getContentAsString();
        String giaId = JsonPath.read(giaResp, "$.id");

        // 3. Create Tax-Exempt Account (ISA)
        String isaJson = """
            {"name":"Stocks and Shares ISA","brokerName":"Trading212","accountCurrency":"GBP","taxTreatment":"TAX_EXEMPT"}
            """;
        String isaResp = mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(isaJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taxTreatment", is("TAX_EXEMPT")))
                .andReturn().getResponse().getContentAsString();
        String isaId = JsonPath.read(isaResp, "$.id");

        // 4. Create Instrument
        Instrument inst = new Instrument("Legal & General Group", AssetClass.STOCK, "LGEN", "GB0005603997", "LSE", new Currency("GBP"), false);
        inst = instrumentRepository.save(inst);
        UUID instId = inst.getId();

        // 5. Add Transactions to GIA:
        // Cash deposit
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + giaId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {"type":"DEPOSIT","grossAmount":10000.00,"currency":"GBP","tradeDate":"2024-05-01T10:00:00Z"}
                            """)))
                .andExpect(status().isCreated());

        // Buy 100 shares @ £20 = £2000
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + giaId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {"type":"BUY","instrumentId":"%s","quantity":100,"price":20.00,"grossAmount":2000.00,"currency":"GBP","tradeDate":"2024-05-02T10:00:00Z"}
                            """, instId)))
                .andExpect(status().isCreated());

        // Sell 50 shares @ £30 = £1500 (Realized gain: 50 * (30 - 20) = £500)
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + giaId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {"type":"SELL","instrumentId":"%s","quantity":50,"price":30.00,"grossAmount":1500.00,"currency":"GBP","tradeDate":"2024-06-15T10:00:00Z"}
                            """, instId)))
                .andExpect(status().isCreated());

        // Dividend £300 in GIA
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + giaId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {"type":"DIVIDEND","instrumentId":"%s","grossAmount":300.00,"currency":"GBP","tradeDate":"2024-07-01T10:00:00Z"}
                            """, instId)))
                .andExpect(status().isCreated());

        // 6. Query Available Tax Years
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/tax-allowances/available-years"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableUkTaxYears", hasItem("2024/25")))
                .andExpect(jsonPath("$.availableCalendarYears", hasItem("2024")));

        // 7. Query Tax Report for 2024/25
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/tax-allowances")
                        .param("taxYear", "2024/25")
                        .param("regime", "UK_HMRC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxYear", is("2024/25")))
                .andExpect(jsonPath("$.taxRegime", is("UK_HMRC")))
                .andExpect(jsonPath("$.capitalGains.netRealizedGainLoss", is(500.0)))
                .andExpect(jsonPath("$.capitalGains.annualExemptAmount", is(3000.0)))
                .andExpect(jsonPath("$.capitalGains.allowanceUsed", is(500.0)))
                .andExpect(jsonPath("$.capitalGains.allowanceRemaining", is(2500.0)))
                .andExpect(jsonPath("$.capitalGains.taxableCapitalGain", is(0.0)))
                .andExpect(jsonPath("$.dividendIncome.totalGrossDividends", is(300.0)))
                .andExpect(jsonPath("$.dividendIncome.annualDividendAllowance", is(500.0)))
                .andExpect(jsonPath("$.dividendIncome.allowanceUsed", is(300.0)))
                .andExpect(jsonPath("$.dividendIncome.taxableDividendIncome", is(0.0)));

        // 8. Update custom settings (reduce CGT allowance to £300, dividend allowance to £200)
        String customSettingsJson = """
            {"taxYear":"2024/25","taxRegime":"UK_HMRC","cgtAllowance":300.00,"dividendAllowance":200.00,"lossCarryforward":0.00,"notes":"Custom threshold"}
            """;
        mockMvc.perform(put("/api/v1/portfolios/" + portfolioId + "/tax-allowances/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customSettingsJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cgtAllowance", is(300.0)))
                .andExpect(jsonPath("$.dividendAllowance", is(200.0)));

        // 9. Verify Tax Report reflects custom allowances:
        // Gain is £500, allowance £300 -> Taxable CGT = £200.
        // Dividend is £300, allowance £200 -> Taxable Div = £100.
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/tax-allowances")
                        .param("taxYear", "2024/25")
                        .param("regime", "UK_HMRC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capitalGains.annualExemptAmount", is(300.0)))
                .andExpect(jsonPath("$.capitalGains.taxableCapitalGain", is(200.0)))
                .andExpect(jsonPath("$.capitalGains.estimatedTaxBasicRate", is(36.0)))
                .andExpect(jsonPath("$.capitalGains.estimatedTaxHigherRate", is(48.0)))
                .andExpect(jsonPath("$.dividendIncome.annualDividendAllowance", is(200.0)))
                .andExpect(jsonPath("$.dividendIncome.taxableDividendIncome", is(100.0)))
                .andExpect(jsonPath("$.dividendIncome.estimatedTaxBasicRate", is(8.75)));
    }
}
