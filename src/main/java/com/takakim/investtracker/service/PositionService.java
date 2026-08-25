package com.takakim.investtracker.service;

import com.takakim.investtracker.domain.Account;
import com.takakim.investtracker.domain.Instrument;
import com.takakim.investtracker.domain.Money;
import com.takakim.investtracker.domain.Position;
import com.takakim.investtracker.domain.PositionStatus;
import com.takakim.investtracker.domain.Quantity;

import com.takakim.investtracker.repository.AccountRepository;
import com.takakim.investtracker.repository.InstrumentRepository;
import com.takakim.investtracker.repository.PortfolioRepository;
import com.takakim.investtracker.repository.PositionRepository;
import com.takakim.investtracker.service.position.PositionCalculationResult;
import com.takakim.investtracker.service.position.PositionEngine;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PositionService {

    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;
    private final PositionRepository positionRepository;
    private final PositionEngine positionEngine;

    public PositionService(
            PortfolioRepository portfolioRepository,
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository,
            PositionRepository positionRepository,
            PositionEngine positionEngine) {
        this.portfolioRepository = portfolioRepository;
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
        this.positionRepository = positionRepository;
        this.positionEngine = positionEngine;
    }

    @Transactional(readOnly = true)
    public List<Position> listPositions(UUID portfolioId, UUID accountId) {
        getValidatedAccount(portfolioId, accountId);
        return positionRepository.findByAccountIdAndStatus(accountId, PositionStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Position> listPortfolioPositions(UUID portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }
        return positionRepository.findByAccountPortfolioIdAndStatus(portfolioId, PositionStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Position getPosition(UUID portfolioId, UUID accountId, UUID positionId) {
        getValidatedAccount(portfolioId, accountId);
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + positionId));
        if (!position.getAccount().getId().equals(accountId)) {
            throw new ResourceNotFoundException("Position " + positionId + " does not belong to account " + accountId);
        }
        return position;
    }

    @Transactional(readOnly = true)
    public PositionCalculationResult getPositionLots(UUID portfolioId, UUID accountId, UUID positionId) {
        return positionEngine.getPositionLots(portfolioId, accountId, positionId);
    }

    public List<PositionCalculationResult> recalculatePortfolio(UUID portfolioId) {
        return positionEngine.recalculatePortfolio(portfolioId);
    }

    public Position createPosition(
            UUID portfolioId,
            UUID accountId,
            UUID instrumentId,
            Quantity quantity,
            Money costBasis) {
        Account account = getValidatedAccount(portfolioId, accountId);
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + instrumentId));

        if (positionRepository.findByAccountIdAndInstrumentId(accountId, instrumentId).isPresent()) {
            throw new ConflictException("Position already exists for instrument " + instrumentId + " in account " + accountId);
        }

        Position position = new Position(account, instrument, quantity, costBasis);
        return positionRepository.save(position);
    }

    public Position updatePosition(
            UUID portfolioId,
            UUID accountId,
            UUID positionId,
            Quantity quantity,
            Money costBasis) {
        Position position = getPosition(portfolioId, accountId, positionId);
        position.update(quantity, costBasis);
        return positionRepository.save(position);
    }

    public void archivePosition(UUID portfolioId, UUID accountId, UUID positionId) {
        Position position = getPosition(portfolioId, accountId, positionId);
        position.archive();
        positionRepository.save(position);
    }

    private Account getValidatedAccount(UUID portfolioId, UUID accountId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found: " + portfolioId);
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        if (!account.getPortfolio().getId().equals(portfolioId)) {
            throw new ResourceNotFoundException("Account " + accountId + " does not belong to portfolio " + portfolioId);
        }
        return account;
    }
}

