package com.takakim.investtracker;

import com.jayway.jsonpath.JsonPath;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
class CsvImportIntegrationTests {

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
    @DisplayName("CSV import preview, execution, duplicate idempotency, and position sync")
    void csvImportLifecycle() throws Exception {
        // 1. Create Portfolio & Account
        String portfolioJson = """
            {"name":"Freetrade Portfolio","baseCurrency":"GBP","costBasisMethod":"FIFO","returnMethod":"XIRR"}
            """;
        String pResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(portfolioJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(pResp, "$.id");

        String accountJson = """
            {"name":"Freetrade GIA","brokerName":"Freetrade","accountCurrency":"GBP"}
            """;
        String aResp = mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accountId = JsonPath.read(aResp, "$.id");

        // 2. Prepare Freetrade CSV sample content
        String csvContent = """
            Title,Type,Timestamp,Account Currency,Total Amount in Account Currency,Buy / Sell,Ticker,ISIN,Price per Share in Account Currency,Stamp Duty,Quantity,Venue,Order ID,Order Type,Instrument Currency,Total Amount in Instrument Currency,Price per Share,FX Rate,Base FX Rate,FX Fee (BPS),FX Fee Amount,Dividend Ex Date,Dividend Pay Date,Dividend Eligible Quantity,Dividend Amount Per Share,Dividend Gross Distribution Amount,Dividend Net Distribution Amount,Dividend Withheld Tax Percentage,Dividend Withheld Tax Amount
            Cerebras Systems,ORDER,2026-05-14T17:42:56.661Z,GBP,494.33,BUY,CBRS,US15675D1037,245.71500000,0.00,2.00000000,G1 Execution Services,339ABW9OS97A,MARKET,USD,659.40,329.69990000,1.33387544,1.34179201,59,2.90,,,,,,,,,,,,,,,,,,,,,,,
            Apple,DIVIDEND,2026-05-14T14:50:00.000Z,GBP,14.78,,AAPL,US0378331005,,,86.99994805,,,,USD,,,,0.74049939,0,0.00,2026-05-11,2026-05-14,86.99994805,0.27000000,23.49,19.97,15,3.52,,,,,,,,,,,,,,,
            April Statement,MONTHLY_STATEMENT,2026-05-01T00:00:00.000Z,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
            """;

        // 3. Dry-run Preview
        String reqJson = toJson("activity-feed-export.csv", csvContent);

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/imports/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brokerName").value("Freetrade"))
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.importableRows").value(2))
                .andExpect(jsonPath("$.duplicateRows").value(0))
                .andExpect(jsonPath("$.ignoredRows").value(1));

        // 4. Execute Import
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.importedRows").value(2))
                .andExpect(jsonPath("$.skippedRows").value(1));

        // 5. Verify Instrument master data was auto-created for Cerebras Systems (CBRS)
        mockMvc.perform(get("/api/v1/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ticker=='CBRS')].name").value("Cerebras Systems"));

        // 6. Verify Position Holdings auto-calculated for Cerebras Systems
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].quantity").value(2.0));

        // 7. Verify Idempotency — Re-importing the exact same CSV skips all rows as duplicates
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/imports/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importableRows").value(0))
                .andExpect(jsonPath("$.duplicateRows").value(2));

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedRows").value(0))
                .andExpect(jsonPath("$.skippedRows").value(3));

        // 8. List import history batches
        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("CSV import error paths and validation")
    void csvImportErrorPaths() throws Exception {
        // Create valid Portfolio & Account
        String portfolioJson = """
            {"name":"Error Test Portfolio","baseCurrency":"GBP","costBasisMethod":"FIFO","returnMethod":"XIRR"}
            """;
        String pResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(portfolioJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = JsonPath.read(pResp, "$.id");

        String accountJson = """
            {"name":"Error Test Account","brokerName":"Freetrade","accountCurrency":"GBP"}
            """;
        String aResp = mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accountId = JsonPath.read(aResp, "$.id");

        UUID randomId = UUID.randomUUID();

        // 404 for missing portfolio/account
        String reqJson = toJson("activity.csv", "Title,Type,Timestamp\n");
        mockMvc.perform(post("/api/v1/portfolios/" + randomId + "/accounts/" + randomId + "/imports/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isNotFound());

        // 400 for unsupported CSV header format with valid account
        String unsupportedCsv = toJson("unsupported.csv", "ColumnA,ColumnB,ColumnC\n1,2,3\n");
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/imports/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unsupportedCsv))
                .andExpect(status().isBadRequest());

        // 400 for empty CSV content
        String emptyCsv = toJson("empty.csv", "");
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/imports/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyCsv))
                .andExpect(status().isBadRequest());

        // 404 for account belonging to another portfolio
        String wrongPortfolioJson = """
            {"name":"Other Portfolio","baseCurrency":"GBP","costBasisMethod":"FIFO","returnMethod":"XIRR"}
            """;
        String otherPResp = mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPortfolioJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String otherPortfolioId = JsonPath.read(otherPResp, "$.id");

        mockMvc.perform(post("/api/v1/portfolios/" + otherPortfolioId + "/accounts/" + accountId + "/imports/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unsupportedCsv))
                .andExpect(status().isNotFound());

        // 8. Trading 212 Import Flow
        String t212Csv = """
            Action,Time (UTC),ISIN,Ticker,Name,Notes,ID,No. of shares,Price / share,Currency (Price / share),Exchange rate,Result,Currency (Result),Total,Currency (Total),Withholding tax,Currency (Withholding tax),Stamp duty,Currency (Stamp duty),Currency conversion fee,Currency (Currency conversion fee)
            Deposit,2024-07-13 06:37:13+00:00,,,,Notes,ID1,,,,,,,500.00,"GBP",,,,,,
            Market buy,2024-07-15 07:00:31+00:00,IE00BMC38736,SMGB,"VanEck Semiconductor (Acc)",,EOF1,1.0000000000,35.0000000000,GBP,1.00000000,,,35.00,"GBP",,,,,,
            """;
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/imports/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson("trading212.csv", t212Csv)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brokerName").value("Trading 212"))
                .andExpect(jsonPath("$.importableRows").value(2));

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson("trading212.csv", t212Csv)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedRows").value(2));

        // 9. InvestEngine Import Flow
        String ieCsv = """
            Transaction Statement: 01 Feb 2025 - 25 Aug 2026 (Portfolio: DIY 1 / Reference: IP01985296)
            Security / ISIN,Transaction Type,Quantity,Share Price,Total Trade Value,Trade Date/Time,Settlement Date,Broker
            Global X NASDAQ 100 Covered Call / ISIN IE00BM8R0J59,Buy,10.000000,£12.5000,£125.00,04/03/25 15:06:44,06/03/25,None
            """;
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/imports/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson("investengine.csv", ieCsv)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brokerName").value("InvestEngine"))
                .andExpect(jsonPath("$.importableRows").value(1));

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/accounts/" + accountId + "/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson("investengine.csv", ieCsv)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedRows").value(1));
    }

    private String toJson(String fileName, String csvContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"fileName\":");
        escapeJsonString(fileName, sb);
        sb.append(",\"csvContent\":");
        escapeJsonString(csvContent, sb);
        sb.append("}");
        return sb.toString();
    }

    private void escapeJsonString(String str, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
    }
}
