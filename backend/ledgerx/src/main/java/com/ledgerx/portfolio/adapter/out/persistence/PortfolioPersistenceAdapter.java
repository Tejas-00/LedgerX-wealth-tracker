package com.ledgerx.portfolio.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ledgerx.identity.domain.UserId;
import com.ledgerx.portfolio.application.model.PortfolioSummary;
import com.ledgerx.portfolio.application.model.PortfolioSummary.ValuationStatus;
import com.ledgerx.portfolio.application.port.out.PortfolioPersistencePort;
import com.ledgerx.portfolio.application.service.IdempotencyConflictException;
import com.ledgerx.portfolio.domain.Portfolio;

@Repository
public class PortfolioPersistenceAdapter implements PortfolioPersistencePort {

	private static final String CREATE_PORTFOLIO_OPERATION = "POST:/api/v1/portfolios";

	private final PortfolioJpaRepository portfolioRepository;
	private final UserProfileJpaRepository userProfileRepository;
	private final IdempotencyRecordJpaRepository idempotencyRepository;

	public PortfolioPersistenceAdapter(PortfolioJpaRepository portfolioRepository,
			UserProfileJpaRepository userProfileRepository,
			IdempotencyRecordJpaRepository idempotencyRepository) {
		this.portfolioRepository = Objects.requireNonNull(portfolioRepository, "portfolioRepository must not be null");
		this.userProfileRepository = Objects.requireNonNull(userProfileRepository, "userProfileRepository must not be null");
		this.idempotencyRepository = Objects.requireNonNull(idempotencyRepository, "idempotencyRepository must not be null");
	}

	@Override
	@Transactional
	public Portfolio createIdempotently(Portfolio portfolio, String idempotencyKey, String requestHash,
			Instant expiresAt) {
		UUID ownerId = portfolio.ownerId().value();
		Instant now = portfolio.createdAt();
		userProfileRepository.createDefaultProfileIfMissing(ownerId, now);
		idempotencyRepository.deleteExpired(ownerId, CREATE_PORTFOLIO_OPERATION, idempotencyKey, now);

		int claimed = idempotencyRepository.claim(UUID.randomUUID(), ownerId, CREATE_PORTFOLIO_OPERATION,
				idempotencyKey, requestHash, now, expiresAt);
		if (claimed == 0) {
			return replayExisting(ownerId, idempotencyKey, requestHash);
		}

		PortfolioJpaEntity created = portfolioRepository.saveAndFlush(new PortfolioJpaEntity(portfolio));
		int completed = idempotencyRepository.attachResource(ownerId, CREATE_PORTFOLIO_OPERATION,
				idempotencyKey, created.toDomain().id());
		if (completed != 1) {
			throw new IllegalStateException("Idempotency record was not completed");
		}
		return created.toDomain();
	}

	@Override
	@Transactional(readOnly = true)
	public List<PortfolioSummary> findSummariesByOwner(UserId ownerId) {
		return portfolioRepository.findSummariesByOwnerId(ownerId.value()).stream()
				.map(this::toSummary)
				.toList();
	}

	private Portfolio replayExisting(UUID ownerId, String idempotencyKey, String requestHash) {
		IdempotencyRecordJpaEntity record = idempotencyRepository
				.findForUpdate(ownerId, CREATE_PORTFOLIO_OPERATION, idempotencyKey)
				.orElseThrow(() -> new IllegalStateException("Idempotency record disappeared during replay"));
		if (!record.requestHash().equals(requestHash)) {
			throw new IdempotencyConflictException();
		}
		if (record.resourceId() == null) {
			throw new IllegalStateException("Committed idempotency record has no resource");
		}
		return portfolioRepository.findByIdAndOwnerId(record.resourceId(), ownerId)
				.map(PortfolioJpaEntity::toDomain)
				.orElseThrow(() -> new IllegalStateException("Idempotent portfolio resource is missing"));
	}

	private PortfolioSummary toSummary(PortfolioSummaryProjection projection) {
		return new PortfolioSummary(projection.getId(), projection.getName(), null,
				projection.getBaseCurrency().trim(), ValuationStatus.INCOMPLETE, null);
	}
}
