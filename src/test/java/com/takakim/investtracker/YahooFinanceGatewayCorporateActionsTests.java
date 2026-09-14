package com.takakim.investtracker;

import com.takakim.investtracker.config.MarketDataProperties;
import com.takakim.investtracker.domain.CorporateActionType;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceDtos;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceGateway;
import com.takakim.investtracker.service.market.yahoo.YahooFinanceGateway.DiscoveredCorporateAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class YahooFinanceGatewayCorporateActionsTests {

    private MarketDataProperties properties;
    private MockRestServiceServer mockServer;
    private YahooFinanceGateway restGateway;
    private YahooFinanceGateway spyGateway;

    @BeforeEach
    void setUp() {
        properties = new MarketDataProperties();
        MarketDataProperties.YahooProperties yahoo = properties.getYahoo();
        yahoo.setEnabled(true);
        yahoo.setBaseUrl("https://query1.finance.yahoo.com");
        yahoo.setMaxRequestsPerMinute(60);

        RestClient.Builder builder = RestClient.builder().baseUrl("https://query1.finance.yahoo.com");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restGateway = new YahooFinanceGateway(properties, builder.build());

        spyGateway = spy(new YahooFinanceGateway(properties, (RestClient) null));
    }

    @Test
    @DisplayName("fetchChartWithEvents returns chart entry when API response is valid")
    void testFetchChartWithEvents_success() {
        String json = """
            {
                "chart": {
                    "result": [
                        {
                            "meta": {
                                "currency": "USD",
                                "symbol": "AAPL",
                                "exchangeName": "NASDAQ",
                                "regularMarketPrice": 180.50
                            },
                            "timestamp": [1718000000],
                            "events": {
                                "splits": {
                                    "1718000000": {
                                        "date": 1718000000,
                                        "numerator": 4,
                                        "denominator": 1,
                                        "splitRatio": "4:1"
                                    }
                                },
                                "dividends": {
                                    "1715000000": {
                                        "amount": 0.25,
                                        "date": 1715000000
                                    }
                                }
                            }
                        }
                    ]
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1y&events=div,split"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<YahooFinanceDtos.ChartEntry> res = restGateway.fetchChartWithEvents("AAPL", "1d", "1y");
        mockServer.verify();

        assertTrue(res.isPresent());
        assertEquals("AAPL", res.get().meta().symbol());
        assertNotNull(res.get().events());
        assertNotNull(res.get().events().splits());
        assertNotNull(res.get().events().dividends());
    }

    @Test
    @DisplayName("fetchChartWithEvents handles default interval, range, and error responses")
    void testFetchChartWithEvents_defaultsAndErrors() {
        // Null or blank symbol
        assertTrue(restGateway.fetchChartWithEvents(null, null, null).isEmpty());
        assertTrue(restGateway.fetchChartWithEvents("   ", null, null).isEmpty());

        // Gateway disabled
        properties.getYahoo().setEnabled(false);
        assertTrue(restGateway.fetchChartWithEvents("AAPL", null, null).isEmpty());
        properties.getYahoo().setEnabled(true);

        // API error payload
        String errorJson = """
            {
                "chart": {
                    "error": {
                        "code": "Not Found",
                        "description": "No data found for symbol INVALID"
                    }
                }
            }
            """;

        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/INVALID?interval=1d&range=1y&events=div,split"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(errorJson, MediaType.APPLICATION_JSON));

        Optional<YahooFinanceDtos.ChartEntry> errorRes = restGateway.fetchChartWithEvents("INVALID", null, null);
        mockServer.verify();
        assertTrue(errorRes.isEmpty());

        // HTTP 404
        mockServer.reset();
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/NOT_FOUND?interval=1d&range=1y&events=div,split"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        assertTrue(restGateway.fetchChartWithEvents("NOT_FOUND", "1d", "1y").isEmpty());
        mockServer.verify();

        // HTTP 500
        mockServer.reset();
        mockServer.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/SERVER_ERR?interval=1d&range=1y&events=div,split"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertTrue(restGateway.fetchChartWithEvents("SERVER_ERR", "1d", "1y").isEmpty());
        mockServer.verify();
    }

    @Test
    void fetchCorporateActions_parsesSplitsAndDividendsCorrectly() {
        YahooFinanceDtos.ChartMeta meta = new YahooFinanceDtos.ChartMeta(
                "USD", "AAPL", "NASDAQ", "Nasdaq", "EQUITY",
                new BigDecimal("150.00"), new BigDecimal("149.00"), new BigDecimal("149.00"), 1700000000L
        );

        YahooFinanceDtos.SplitEvent splitEvent = new YahooFinanceDtos.SplitEvent(
                1718000000L, new BigDecimal("4"), BigDecimal.ONE, "4:1"
        );
        YahooFinanceDtos.DividendEvent divEvent = new YahooFinanceDtos.DividendEvent(
                new BigDecimal("0.25"), 1715000000L
        );

        YahooFinanceDtos.ChartEvents events = new YahooFinanceDtos.ChartEvents(
                Map.of("1715000000", divEvent),
                Map.of("1718000000", splitEvent)
        );

        YahooFinanceDtos.ChartEntry entry = new YahooFinanceDtos.ChartEntry(
                meta, List.of(1718000000L), null, events
        );

        doReturn(Optional.of(entry)).when(spyGateway).fetchChartWithEvents(eq("AAPL"), any(), any());

        List<DiscoveredCorporateAction> actions = spyGateway.fetchCorporateActions("AAPL", "1y");

        assertNotNull(actions);
        assertEquals(2, actions.size());

        // Chronological descending order: split was 1718000000, div was 1715000000
        DiscoveredCorporateAction first = actions.get(0);
        assertEquals(CorporateActionType.STOCK_SPLIT, first.actionType());
        assertEquals(BigDecimal.ONE, first.ratioFrom());
        assertEquals(new BigDecimal("4"), first.ratioTo());
        assertEquals("YF-AAPL-SPLIT-1718000000", first.externalId());

        DiscoveredCorporateAction second = actions.get(1);
        assertEquals(CorporateActionType.DIVIDEND, second.actionType());
        assertEquals(new BigDecimal("0.25"), second.amountPerShare());
        assertEquals("USD", second.currency());
        assertEquals("YF-AAPL-DIV-1715000000", second.externalId());
    }

    @Test
    void fetchCorporateActions_reverseSplit() {
        YahooFinanceDtos.ChartMeta meta = new YahooFinanceDtos.ChartMeta(
                "GBP", "RGL.L", "LSE", "London Stock Exchange", "EQUITY",
                new BigDecimal("10.00"), null, null, null
        );

        YahooFinanceDtos.SplitEvent reverseSplit = new YahooFinanceDtos.SplitEvent(
                1720000000L, BigDecimal.ONE, new BigDecimal("10"), "1:10"
        );

        YahooFinanceDtos.ChartEvents events = new YahooFinanceDtos.ChartEvents(
                null,
                Map.of("1720000000", reverseSplit)
        );

        YahooFinanceDtos.ChartEntry entry = new YahooFinanceDtos.ChartEntry(
                meta, List.of(1720000000L), null, events
        );

        doReturn(Optional.of(entry)).when(spyGateway).fetchChartWithEvents(eq("RGL.L"), any(), any());

        List<DiscoveredCorporateAction> actions = spyGateway.fetchCorporateActions("RGL.L", "1y");
        assertEquals(1, actions.size());
        assertEquals(CorporateActionType.REVERSE_STOCK_SPLIT, actions.get(0).actionType());
        assertEquals(new BigDecimal("10"), actions.get(0).ratioFrom());
        assertEquals(BigDecimal.ONE, actions.get(0).ratioTo());
    }

    @Test
    void fetchCorporateActions_skipsInvalidEvents() {
        YahooFinanceDtos.ChartMeta meta = new YahooFinanceDtos.ChartMeta(
                "USD", "TEST", "NASDAQ", "Nasdaq", "EQUITY",
                BigDecimal.TEN, null, null, null
        );

        // Invalid split (numerator is null) & invalid dividend (amount is negative or zero)
        YahooFinanceDtos.SplitEvent invalidSplit = new YahooFinanceDtos.SplitEvent(
                1720000000L, null, BigDecimal.ONE, "invalid"
        );
        YahooFinanceDtos.DividendEvent invalidDiv1 = new YahooFinanceDtos.DividendEvent(
                null, 1715000000L
        );
        YahooFinanceDtos.DividendEvent invalidDiv2 = new YahooFinanceDtos.DividendEvent(
                BigDecimal.ZERO, 1716000000L
        );

        YahooFinanceDtos.ChartEvents events = new YahooFinanceDtos.ChartEvents(
                Map.of("1", invalidDiv1, "2", invalidDiv2),
                Map.of("3", invalidSplit)
        );

        YahooFinanceDtos.ChartEntry entry = new YahooFinanceDtos.ChartEntry(
                meta, List.of(), null, events
        );

        doReturn(Optional.of(entry)).when(spyGateway).fetchChartWithEvents(eq("TEST"), any(), any());

        List<DiscoveredCorporateAction> actions = spyGateway.fetchCorporateActions("TEST", "1y");
        assertTrue(actions.isEmpty());
    }

    @Test
    void fetchCorporateActions_emptyEventsOrChartOpt() {
        doReturn(Optional.empty()).when(spyGateway).fetchChartWithEvents(eq("UNKNOWN"), any(), any());
        assertTrue(spyGateway.fetchCorporateActions("UNKNOWN", "1y").isEmpty());

        YahooFinanceDtos.ChartEntry entryNoEvents = new YahooFinanceDtos.ChartEntry(
                null, List.of(), null, null
        );
        doReturn(Optional.of(entryNoEvents)).when(spyGateway).fetchChartWithEvents(eq("EMPTY"), any(), any());
        assertTrue(spyGateway.fetchCorporateActions("EMPTY", "1y").isEmpty());
    }
}
