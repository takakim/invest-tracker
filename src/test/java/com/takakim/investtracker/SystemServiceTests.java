package com.takakim.investtracker;

import com.takakim.investtracker.api.ApiDtos;
import com.takakim.investtracker.api.SystemController;
import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.FxObservationRepository;
import com.takakim.investtracker.repository.ImportBatchRepository;
import com.takakim.investtracker.repository.ImportRecordRepository;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.MarketObservationRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.PositionRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import com.takakim.investtracker.service.system.SystemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SystemServiceTests {

    @Mock
    private MarketObservationRepository marketObservationRepository;
    @Mock
    private FxObservationRepository fxObservationRepository;
    @Mock
    private ImportRecordRepository importRecordRepository;
    @Mock
    private ImportBatchRepository importBatchRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private InstrumentRepository instrumentRepository;

    @InjectMocks
    private SystemService systemService;

    @Test
    @DisplayName("SystemService resetDatabase deletes all repository tables in batch")
    void resetDatabaseDeletesAllRepositories() {
        systemService.resetDatabase();

        verify(importRecordRepository).deleteAllInBatch();
        verify(importBatchRepository).deleteAllInBatch();
        verify(marketObservationRepository).deleteAllInBatch();
        verify(fxObservationRepository).deleteAllInBatch();
        verify(transactionRepository).deleteAllInBatch();
        verify(positionRepository).deleteAllInBatch();
        verify(accountRepository).deleteAllInBatch();
        verify(portfolioRepository).deleteAllInBatch();
        verify(instrumentRepository).deleteAllInBatch();
    }

    @Test
    @DisplayName("SystemController resetDatabase validates confirmation keyword")
    void systemControllerValidatesConfirmation() {
        SystemController controller = new SystemController(systemService);

        assertThrows(IllegalArgumentException.class, () -> controller.resetDatabase(null));
        assertThrows(IllegalArgumentException.class, () -> controller.resetDatabase(new ApiDtos.DatabaseResetRequest(null)));
        assertThrows(IllegalArgumentException.class, () -> controller.resetDatabase(new ApiDtos.DatabaseResetRequest("no")));
        assertThrows(IllegalArgumentException.class, () -> controller.resetDatabase(new ApiDtos.DatabaseResetRequest(" ")));

        ResponseEntity<ApiDtos.DatabaseResetResponse> res = controller.resetDatabase(new ApiDtos.DatabaseResetRequest("RESET"));
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertNotNull(res.getBody().message());
        assertNotNull(res.getBody().timestamp());

        ResponseEntity<ApiDtos.DatabaseResetResponse> resLower = controller.resetDatabase(new ApiDtos.DatabaseResetRequest("reset"));
        assertEquals(200, resLower.getStatusCode().value());
    }
}
