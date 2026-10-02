package com.ledgerx.portfolio.application.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.ledgerx.identity.domain.UserId;
import com.ledgerx.portfolio.application.model.PortfolioSummary;
import com.ledgerx.portfolio.application.port.in.ListPortfoliosUseCase;
import com.ledgerx.portfolio.application.port.out.PortfolioPersistencePort;

@Service
public class ListPortfoliosService implements ListPortfoliosUseCase {

	private final PortfolioPersistencePort persistencePort;

	public ListPortfoliosService(PortfolioPersistencePort persistencePort) {
		this.persistencePort = Objects.requireNonNull(persistencePort, "persistencePort must not be null");
	}

	@Override
	public List<PortfolioSummary> execute(UserId ownerId) {
		return persistencePort.findSummariesByOwner(Objects.requireNonNull(ownerId, "ownerId must not be null"));
	}
}
