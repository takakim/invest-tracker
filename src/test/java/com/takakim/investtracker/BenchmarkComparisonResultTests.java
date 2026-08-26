package com.takakim.investtracker;

import com.takakim.investtracker.service.benchmark.BenchmarkComparisonResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkComparisonResultTests {

    @Test
    void benchmarkComparisonResult_handlesNullListsAndValidatesNonNulls() {
        UUID pId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        Instant now = Instant.now();

        BenchmarkComparisonResult result = new BenchmarkComparisonResult(
                pId, bId, "S&P 500 ETF", "VUSA",
                now.minusSeconds(86400), now,
                new BigDecimal("0.1500"), new BigDecimal("0.1000"), new BigDecimal("0.0500"),
                new BigDecimal("0.1500"), new BigDecimal("0.1000"), new BigDecimal("0.0500"),
                true, "GBP", null
        );

        assertEquals(pId, result.portfolioId());
        assertEquals(bId, result.benchmarkInstrumentId());
        assertEquals("S&P 500 ETF", result.benchmarkName());
        assertEquals("VUSA", result.benchmarkTicker());
        assertTrue(result.outperforming());
        assertNotNull(result.warnings());
        assertTrue(result.warnings().isEmpty());

        assertThrows(NullPointerException.class, () -> new BenchmarkComparisonResult(
                null, bId, "S&P 500", "VUSA", now, now,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true, "GBP", null
        ));
    }
}
