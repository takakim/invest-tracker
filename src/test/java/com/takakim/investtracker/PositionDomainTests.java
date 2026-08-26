package com.takakim.investtracker;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.AssetClass;
import com.takakim.investtracker.domain.CostBasisMethod;
import com.takakim.investtracker.domain.Currency;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Portfolio;
import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.domain.PositionStatus;
import com.takakim.investtracker.domain.Quantity;
import com.takakim.investtracker.domain.ReturnMethod;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PositionDomainTests {

    @Test
    @DisplayName("Position validates required fields and sets initial active state")
    void positionValidatesInputsAndSetsDefaults() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "ISA Account", "Broker", gbp);
        Instrument instrument = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));

        assertThrows(NullPointerException.class, () -> new Position(null, instrument, new Quantity(BigDecimal.TEN), null));
        assertThrows(NullPointerException.class, () -> new Position(account, null, new Quantity(BigDecimal.TEN), null));
        assertThrows(IllegalArgumentException.class, () -> new Position(account, instrument, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Position(account, instrument, new Quantity(new BigDecimal("-1.0")), null));

        Money costBasis = new Money(new BigDecimal("150.5000"), new Currency("USD"));
        Position position = new Position(account, instrument, new Quantity(new BigDecimal("25.50000000")), costBasis);

        assertEquals(account, position.getAccount());
        assertEquals(instrument, position.getInstrument());
        assertEquals(new BigDecimal("25.50000000"), position.getQuantity());
        assertEquals(new BigDecimal("25.50000000"), position.getQuantityValueObject().value());
        assertEquals(new BigDecimal("150.5000"), position.getCostBasisAmount());
        assertEquals("USD", position.getCostBasisCurrency());
        assertEquals(costBasis, position.getCostBasisMoney());
        assertEquals(PositionStatus.ACTIVE, position.getStatus());
        assertNotNull(position.getId());
        assertNotNull(position.getCreatedAt());
        assertNotNull(position.getUpdatedAt());
    }

    @Test
    @DisplayName("Position updates quantity and cost basis correctly")
    void positionUpdatesCorrectly() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "ISA Account", "Broker", gbp);
        Instrument instrument = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));

        Position position = new Position(account, instrument, new Quantity(BigDecimal.TEN), null);
        assertNull(position.getCostBasisMoney());

        Money newCostBasis = new Money(new BigDecimal("2000.00"), gbp);
        position.update(new Quantity(new BigDecimal("50.00")), newCostBasis);

        position.update(new Quantity(new BigDecimal("50.00")), null);
        assertNull(position.getCostBasisAmount());
        assertNull(position.getCostBasisCurrency());
        assertNull(position.getCostBasisMoney());

        assertThrows(IllegalArgumentException.class, () -> position.update(null, null));
        assertThrows(IllegalArgumentException.class, () -> position.update(new Quantity(new BigDecimal("-0.1")), null));
    }

    @Test
    @DisplayName("Position handles partial cost basis null combinations correctly")
    void positionCostBasisNullVariations() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "ISA Account", "Broker", gbp);
        Instrument instrument = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));

        Position pos1 = new Position(account, instrument, new Quantity(BigDecimal.TEN), null);
        assertNull(pos1.getCostBasisAmount());
        assertNull(pos1.getCostBasisCurrency());
        assertNull(pos1.getCostBasisMoney());

        Money costBasis = new Money(new BigDecimal("100.00"), gbp);
        pos1.update(new Quantity(BigDecimal.TEN), costBasis);
        assertNotNull(pos1.getCostBasisMoney());
    }

    @Test
    @DisplayName("Position validates null quantity and negative quantity on update")
    void positionValidatesQuantityOnUpdate() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "ISA Account", "Broker", gbp);
        Instrument instrument = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));

        Position position = new Position(account, instrument, new Quantity(BigDecimal.TEN), null);
        assertThrows(IllegalArgumentException.class, () -> position.update(null, null));
        assertThrows(IllegalArgumentException.class, () -> position.update(new Quantity(new BigDecimal("-1.0")), null));
    }

    @Test
    @DisplayName("Archived position prevents updates")
    void positionArchivingEnforcesInvariants() {
        Currency gbp = new Currency("GBP");
        Portfolio portfolio = new Portfolio("Main", gbp, CostBasisMethod.FIFO, ReturnMethod.XIRR);
        Account account = new Account(portfolio, "ISA Account", "Broker", gbp);
        Instrument instrument = new Instrument("Apple Inc", AssetClass.STOCK, "AAPL", "US0378331005", "NASDAQ", new Currency("USD"));

        Position position = new Position(account, instrument, new Quantity(BigDecimal.ONE), null);
        position.archive();

        assertEquals(PositionStatus.ARCHIVED, position.getStatus());
        assertThrows(IllegalStateException.class, () -> position.update(new Quantity(BigDecimal.TEN), null));

        position.unarchive();
        assertEquals(PositionStatus.ACTIVE, position.getStatus());
        position.update(new Quantity(BigDecimal.TEN), null);
        assertEquals(new BigDecimal("10"), position.getQuantity());
    }
}
