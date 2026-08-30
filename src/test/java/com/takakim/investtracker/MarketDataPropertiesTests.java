package com.takakim.investtracker;

import com.takakim.investtracker.config.MarketDataConfig;
import com.takakim.investtracker.config.MarketDataProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataPropertiesTests {

    @Test
    void testMarketDataPropertiesGettersAndSetters() {
        MarketDataProperties props = new MarketDataProperties();
        assertNotNull(props.getTwelvedata());

        MarketDataProperties.TwelveDataProperties td = new MarketDataProperties.TwelveDataProperties();
        td.setEnabled(false);
        td.setApiKey("my-key");
        td.setBaseUrl("https://api.custom.com");
        td.setConnectTimeoutSeconds(15);
        td.setReadTimeoutSeconds(30);
        td.setCacheTtlSeconds(600);

        props.setTwelvedata(td);

        assertEquals(td, props.getTwelvedata());
        assertFalse(props.getTwelvedata().isEnabled());
        assertEquals("my-key", props.getTwelvedata().getApiKey());
        assertEquals("https://api.custom.com", props.getTwelvedata().getBaseUrl());
        assertEquals(15, props.getTwelvedata().getConnectTimeoutSeconds());
        assertEquals(30, props.getTwelvedata().getReadTimeoutSeconds());
        assertEquals(600, props.getTwelvedata().getCacheTtlSeconds());

        assertNotNull(props.getFmp());
        MarketDataProperties.FmpProperties fmp = new MarketDataProperties.FmpProperties();
        fmp.setEnabled(false);
        fmp.setApiKey("fmp-key");
        fmp.setBaseUrl("https://custom-fmp.com");
        fmp.setConnectTimeoutSeconds(10);
        fmp.setReadTimeoutSeconds(20);
        fmp.setCacheTtlSeconds(300);
        fmp.setMaxRequestsPerMinute(10);

        props.setFmp(fmp);
        assertEquals(fmp, props.getFmp());
        assertFalse(props.getFmp().isEnabled());
        assertEquals("fmp-key", props.getFmp().getApiKey());
        assertEquals("https://custom-fmp.com", props.getFmp().getBaseUrl());
        assertEquals(10, props.getFmp().getConnectTimeoutSeconds());
        assertEquals(20, props.getFmp().getReadTimeoutSeconds());
        assertEquals(300, props.getFmp().getCacheTtlSeconds());
        assertEquals(10, props.getFmp().getMaxRequestsPerMinute());

        assertNotNull(props.getYahoo());
        MarketDataProperties.YahooProperties yahoo = new MarketDataProperties.YahooProperties();
        yahoo.setEnabled(false);
        yahoo.setBaseUrl("https://custom-yahoo.com");
        yahoo.setConnectTimeoutSeconds(10);
        yahoo.setReadTimeoutSeconds(20);
        yahoo.setCacheTtlSeconds(300);
        yahoo.setMaxRequestsPerMinute(10);
        yahoo.setUserAgent("CustomAgent/1.0");

        props.setYahoo(yahoo);
        assertEquals(yahoo, props.getYahoo());
        assertFalse(props.getYahoo().isEnabled());
        assertEquals("https://custom-yahoo.com", props.getYahoo().getBaseUrl());
        assertEquals(10, props.getYahoo().getConnectTimeoutSeconds());
        assertEquals(20, props.getYahoo().getReadTimeoutSeconds());
        assertEquals(300, props.getYahoo().getCacheTtlSeconds());
        assertEquals(10, props.getYahoo().getMaxRequestsPerMinute());
        assertEquals("CustomAgent/1.0", props.getYahoo().getUserAgent());
    }

    @Test
    void testMarketDataConfig() {
        MarketDataConfig config = new MarketDataConfig();
        RestClient.Builder builder = config.restClientBuilder();
        assertNotNull(builder);
    }
}
