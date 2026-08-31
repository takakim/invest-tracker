package com.takakim.investtracker;

import com.takakim.investtracker.domain.TransactionType;
import com.takakim.investtracker.service.csv.DegiroCsvParser;
import com.takakim.investtracker.service.csv.ParsedTransactionRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DegiroCsvParserTests {

    private DegiroCsvParser parser;

    @BeforeEach
    void setUp() {
        parser = new DegiroCsvParser();
    }

    @Test
    @DisplayName("Supports DEGIRO headers in English, Dutch, and German")
    void supportsHeaders() {
        assertEquals("DEGIRO", parser.getBrokerName());

        List<String> headersEn = List.of("Date", "Time", "Product", "ISIN", "Reference", "Venue", "Quantity", "Price", "Local value", "Value", "Exchange rate", "Fee", "Total");
        assertTrue(parser.supports(headersEn));

        List<String> headersNl = List.of("Datum", "Tijd", "Product", "ISIN", "Referentie", "Beurs", "Aantal", "Koers", "Lokale waarde", "Waarde", "Wisselkoers", "Kosten", "Totaal");
        assertTrue(parser.supports(headersNl));

        List<String> headersDe = List.of("Datum", "Uhrzeit", "Produkt", "ISIN", "Referenz", "Ausführungsplatz", "Anzahl", "Kurs", "Lokaler Wert", "Gesamt", "Wechselkurs", "Gebühren", "Gesamtbetrag");
        assertTrue(parser.supports(headersDe));

        assertFalse(parser.supports(List.of("Action", "Time", "ISIN")));
        assertFalse(parser.supports(null));
        assertFalse(parser.supports(List.of("A")));
    }

    @Test
    @DisplayName("Parses DEGIRO buy, sell, deposit, withdrawal, and fee rows in English, Dutch, and German")
    void parsesDegiroRows() {
        String csv = """
            Date,Time,Product,ISIN,Reference,Venue,Quantity,Price,Local value,Value,Exchange rate,Fee,Total
            15-01-2026,14:30:00,VANGUARD S&P 500,IE00B3XXRP09,ORD123,EAM,10,85.50,855.00,855.00,1.0,-1.00,-856.00
            20/01/2026,10:15,ISHARES CORE MSCI,IE00B4L5Y983,ORD124,EAM,-5,75.00,-375.00,-375.00,1.0,-1.00,374.00
            2026-01-25,,Flatex Deposit,,,,,,,1000.00,1.0,,1000.00
            26-01-2026,09:00:00,Terugstorting naar bank,,,,,,,-200.00,1.0,,-200.00
            27-01-2026,09:00:00,DEGIRO Aansluiting Fee,,,,,,,-2.50,1.0,,-2.50
            28-01-2026,09:00:00,VANGUARD S&P 500 Dividende,IE00B3XXRP09,,,,,,15.00,1.0,,15.00
            29-01-2026,09:00:00,Auszahlung Bank,,,,,,,-100.00,1.0,,-100.00
            30-01-2026,09:00:00,Einzahlung Flatex,,,,,,,300.00,1.0,,300.00
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(8, rows.size());

        // Row 1: Buy
        ParsedTransactionRow r1 = rows.get(0);
        assertEquals(TransactionType.BUY, r1.mappedType());
        assertEquals("IE00B3XXRP09", r1.isin());
        assertEquals(new BigDecimal("10"), r1.quantity());
        assertEquals(new BigDecimal("85.50"), r1.price());
        assertEquals(new BigDecimal("1.00"), r1.feeAmount());

        // Row 2: Sell
        ParsedTransactionRow r2 = rows.get(1);
        assertEquals(TransactionType.SELL, r2.mappedType());
        assertEquals("IE00B4L5Y983", r2.isin());
        assertEquals(new BigDecimal("5"), r2.quantity());

        // Row 3: Deposit
        ParsedTransactionRow r3 = rows.get(2);
        assertEquals(TransactionType.DEPOSIT, r3.mappedType());

        // Row 4: Withdrawal
        ParsedTransactionRow r4 = rows.get(3);
        assertEquals(TransactionType.WITHDRAWAL, r4.mappedType());

        // Row 5: Fee
        ParsedTransactionRow r5 = rows.get(4);
        assertEquals(TransactionType.FEE, r5.mappedType());

        // Row 6: Dividend
        ParsedTransactionRow r6 = rows.get(5);
        assertEquals(TransactionType.DIVIDEND, r6.mappedType());

        // Row 7: German Auszahlung (Withdrawal)
        ParsedTransactionRow r7 = rows.get(6);
        assertEquals(TransactionType.WITHDRAWAL, r7.mappedType());

        // Row 8: German Einzahlung (Deposit)
        ParsedTransactionRow r8 = rows.get(7);
        assertEquals(TransactionType.DEPOSIT, r8.mappedType());
    }

    @Test
    @DisplayName("Handles currencies and European number formats properly")
    void handlesCurrenciesAndNumberFormats() {
        String csv = """
            Datum,Tijd,Produkt,ISIN,Referentie,Beurs,Aantal,Koers,Lokale waarde,Waarde,Wisselkoers,Kosten,Totaal
            15-01-2026,14:30,Apple Inc GBP,US0378331005,ORD1,LSE,10,"1.234,56","12.345,60","12.345,60",1.0,-2.50,"-12.348,10"
            16-01-2026,15:30,Microsoft Corp USD,US5949181045,ORD2,NASDAQ,5,"1,234.56","6,172.80","6,172.80",1.0,-1.50,"-6,174.30"
            17-01-2026,16:30,SAP SE EUR,DE0007164600,ORD3,XETRA,20,"123,45","2.469,00","2.469,00",1.0,-1.00,"-2.470,00"
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(3, rows.size());

        assertEquals("GBP", rows.get(0).currency());
        assertEquals(new BigDecimal("1234.56"), rows.get(0).price());

        assertEquals("USD", rows.get(1).currency());
        assertEquals(new BigDecimal("1234.56"), rows.get(1).price());

        assertEquals("EUR", rows.get(2).currency());
        assertEquals(new BigDecimal("123.45"), rows.get(2).price());
    }

    @Test
    @DisplayName("Parses additional German, Dutch, and English transaction types")
    void parsesAdditionalTypes() {
        String csv = """
            Datum,Tijd,Produkt,ISIN,Referentie,Beurs,Aantal,Koers,Lokale waarde,Waarde,Wisselkoers,Kosten,Totaal
            01-02-2026,10:00,Obligatie Coupon,NL0000000000,ORD10,EAM,,,,10.00,1.0,,10.00
            02-02-2026,10:00,Rente Flatex Bank,,ORD11,EAM,,,,2.50,1.0,,2.50
            03-02-2026,10:00,Zinsen Guthaben,,ORD12,EAM,,,,1.75,1.0,,1.75
            04-02-2026,10:00,DEGIRO Tax Adjustment,,ORD13,EAM,,,,-0.50,1.0,,-0.50
            05-02-2026,10:00,Cash Sweep Withdrawal,,ORD14,EAM,,,,-50.00,1.0,,-50.00
            06-02-2026,10:00,Asset Without Total,IE00B3XXRP09,ORD15,EAM,10,50.00,,,1.0,-1.00,
            07-02-2026,10:00,Unknown Direction Positive Total,,ORD16,EAM,,,,100.00,1.0,,100.00
            08-02-2026,10:00,Unknown Direction Negative Total,,ORD17,EAM,,,,-100.00,1.0,,-100.00
            """;

        List<ParsedTransactionRow> rows = parser.parse(csv);
        assertEquals(8, rows.size());

        assertEquals(TransactionType.DIVIDEND, rows.get(0).mappedType()); // Coupon -> Dividend
        assertEquals(TransactionType.INTEREST, rows.get(1).mappedType()); // Rente -> Interest
        assertEquals(TransactionType.INTEREST, rows.get(2).mappedType()); // Zinsen -> Interest
        assertEquals(TransactionType.FEE, rows.get(3).mappedType()); // Tax -> Fee
        assertEquals(TransactionType.WITHDRAWAL, rows.get(4).mappedType()); // Cash sweep -> Withdrawal
        assertEquals(TransactionType.BUY, rows.get(5).mappedType()); // qty * price fallback
        assertEquals(new BigDecimal("500.00"), rows.get(5).grossAmount());
        assertEquals(TransactionType.SELL, rows.get(6).mappedType()); // positive total -> sell
        assertEquals(TransactionType.BUY, rows.get(7).mappedType()); // negative total -> buy

        String additionalCsv = """
            Datum,Tijd,Produkt,ISIN,Referentie,Beurs,Aantal,Koers,Lokale waarde,Waarde,Wisselkoers,Kosten,Totaal
            01-02-2026,10:00:15,Aandeel Dividende,NL0000000001,ORD20,EAM,,,,10.00,1.0,,10.00
            02-02-2026,10:00:15,Terugstorting Rekening,,ORD21,EAM,,,,-100.00,1.0,,-100.00
            03-02-2026,10:00:15,Storting iDEAL,,ORD22,EAM,,,,200.00,1.0,,200.00
            04-02-2026,10:00:15,DEGIRO Aansluiting Kosten,,ORD23,EAM,,,,-2.50,1.0,,-2.50
            05-02-2026,10:00:15,Buy Asset with dash,IE00B3XXRP09,ORD24,EAM,10,50.00,-,-,1.0,-,-500.00
            """;
        List<ParsedTransactionRow> addRows = parser.parse(additionalCsv);
        assertEquals(5, addRows.size());
        assertEquals(TransactionType.DIVIDEND, addRows.get(0).mappedType());
        assertEquals(TransactionType.WITHDRAWAL, addRows.get(1).mappedType());
        assertEquals(TransactionType.DEPOSIT, addRows.get(2).mappedType());
        assertEquals(TransactionType.FEE, addRows.get(3).mappedType());
        assertEquals(TransactionType.BUY, addRows.get(4).mappedType());
    }

    @Test
    @DisplayName("Handles malformed DEGIRO rows safely")
    void handlesMalformedRows() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
        assertTrue(parser.parse("Date,Time\n").isEmpty());

        String malformed = """
            Date,Time,Product,ISIN,Reference,Venue,Quantity,Price,Local value,Value,Exchange rate,Fee,Total
            invalid-date,12:00,Prod,ISIN1,ref,ven,1,1,1,1,1,0,1
            """;
        List<ParsedTransactionRow> rows = parser.parse(malformed);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).isIgnored());
    }

    @Test
    @DisplayName("Tests DEGIRO supports combinations and date formats")
    void testSupportsCombinationsAndDates() {
        assertTrue(parser.supports(List.of("Product", "ISIN", "Waarde", "Quantity")));
        assertTrue(parser.supports(List.of("Produkt", "Beurs", "Kurs", "Anzahl")));
        assertTrue(parser.supports(List.of("Product", "Venue", "Wisselkoers", "Total")));
        assertTrue(parser.supports(List.of("Produkt", "ISIN", "Value", "Total")));
        assertTrue(parser.supports(List.of("Product", "Beurs", "Rate", "Total")));
        assertFalse(parser.supports(List.of("Other1", "Other2", "Other3", "Other4")));

        String dateNoTime = """
            Datum,Tijd,Produkt,ISIN,Referentie,Beurs,Aantal,Koers,Lokale waarde,Waarde,Wisselkoers,Kosten,Totaal
            2026-02-15,,Vanguard S&P 500,IE00B3XXRP09,ORD99,EAM,1,50.00,50.00,50.00,1.0,0.00,-50.00
            15/02/2026,,Vanguard S&P 500,IE00B3XXRP09,ORD99,EAM,1,50.00,50.00,50.00,1.0,0.00,-50.00
            """;
        List<ParsedTransactionRow> rows = parser.parse(dateNoTime);
        assertEquals(2, rows.size());
        assertNotNull(rows.get(0).timestamp());
        assertNotNull(rows.get(1).timestamp());
    }
}
