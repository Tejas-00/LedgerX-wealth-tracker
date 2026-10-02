package com.ledgerx.portfolio.application.port.out;

import java.time.Instant;
import java.util.List;

import com.ledgerx.identity.domain.UserId;
import com.ledgerx.portfolio.application.model.PortfolioSummary;
import com.ledgerx.portfolio.domain.Portfolio;

public interface PortfolioPersistencePort {

	Portfolio createIdempotently(Portfolio portfolio, String idempotencyKey, String requestHash, Instant expiresAt);

	List<PortfolioSummary> findSummariesByOwner(UserId ownerId);
}
