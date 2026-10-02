package com.ledgerx.portfolio.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ledgerx.identity.domain.UserId;
import com.ledgerx.portfolio.application.model.PortfolioSummary;
import com.ledgerx.portfolio.application.port.in.CreatePortfolioUseCase.CreatePortfolioCommand;
import com.ledgerx.portfolio.application.port.out.PortfolioPersistencePort;
import com.ledgerx.portfolio.domain.Portfolio;

class CreatePortfolioServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Test
	void normalizesAndPersistsWithAuthenticatedOwnerAndIdempotencyWindow() {
		RecordingPortfolioPort persistencePort = new RecordingPortfolioPort();
		CreatePortfolioService service = new CreatePortfolioService(
				persistencePort, Clock.fixed(NOW, ZoneOffset.UTC));
		UserId ownerId = new UserId(UUID.randomUUID());

		Portfolio result = service.execute(ownerId,
				new CreatePortfolioCommand("  Emergency fund ", "USD", "portfolio-create-0001"));

		assertEquals(ownerId, result.ownerId());
		assertEquals("Emergency fund", result.name());
		assertEquals("USD", result.currency().value());
		assertEquals("portfolio-create-0001", persistencePort.idempotencyKey);
		assertEquals(NOW.plusSeconds(24 * 60 * 60), persistencePort.expiresAt);
		assertEquals(64, persistencePort.requestHash.length());
	}

	@Test
	void rejectsInvalidCurrencyBeforePersistence() {
		RecordingPortfolioPort persistencePort = new RecordingPortfolioPort();
		CreatePortfolioService service = new CreatePortfolioService(
				persistencePort, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThrows(InvalidPortfolioException.class, () -> service.execute(
				new UserId(UUID.randomUUID()), new CreatePortfolioCommand("Savings", "ABC", "portfolio-create-0002")));
		assertNull(persistencePort.idempotencyKey);
	}

	private static final class RecordingPortfolioPort implements PortfolioPersistencePort {
		private String idempotencyKey;
		private String requestHash;
		private Instant expiresAt;

		@Override
		public Portfolio createIdempotently(Portfolio portfolio, String key, String hash, Instant expiration) {
			idempotencyKey = key;
			requestHash = hash;
			expiresAt = expiration;
			return portfolio;
		}

		@Override
		public List<PortfolioSummary> findSummariesByOwner(UserId ownerId) {
			return List.of();
		}
	}
}
