package com.takakim.investtracker.service.system;

import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.FxObservationRepository;
import com.takakim.investtracker.repository.ImportBatchRepository;
import com.takakim.investtracker.repository.ImportRecordRepository;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.MarketObservationRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.PositionRepository;
import com.takakim.investtracker.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SystemService {

    private final MarketObservationRepository marketObservationRepository;
    private final FxObservationRepository fxObservationRepository;
    private final ImportRecordRepository importRecordRepository;
    private final ImportBatchRepository importBatchRepository;
    private final TransactionRepository transactionRepository;
    private final PositionRepository positionRepository;
    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final InstrumentRepository instrumentRepository;

    public SystemService(
            MarketObservationRepository marketObservationRepository,
            FxObservationRepository fxObservationRepository,
            ImportRecordRepository importRecordRepository,
            ImportBatchRepository importBatchRepository,
            TransactionRepository transactionRepository,
            PositionRepository positionRepository,
            AccountRepository accountRepository,
            PortfolioRepository portfolioRepository,
            InstrumentRepository instrumentRepository) {
        this.marketObservationRepository = marketObservationRepository;
        this.fxObservationRepository = fxObservationRepository;
        this.importRecordRepository = importRecordRepository;
        this.importBatchRepository = importBatchRepository;
        this.transactionRepository = transactionRepository;
        this.positionRepository = positionRepository;
        this.accountRepository = accountRepository;
        this.portfolioRepository = portfolioRepository;
        this.instrumentRepository = instrumentRepository;
    }

    public void resetDatabase() {
        importRecordRepository.deleteAllInBatch();
        importBatchRepository.deleteAllInBatch();
        marketObservationRepository.deleteAllInBatch();
        fxObservationRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        positionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        portfolioRepository.deleteAllInBatch();
        instrumentRepository.deleteAllInBatch();
    }
}
