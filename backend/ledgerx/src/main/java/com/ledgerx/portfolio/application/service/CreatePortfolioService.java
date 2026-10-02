package com.ledgerx.portfolio.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.ledgerx.identity.domain.UserId;
import com.ledgerx.portfolio.application.port.in.CreatePortfolioUseCase;
import com.ledgerx.portfolio.application.port.out.PortfolioPersistencePort;
import com.ledgerx.portfolio.domain.Portfolio;

@Service
public class CreatePortfolioService implements CreatePortfolioUseCase {

	private static final Duration IDEMPOTENCY_RETENTION = Duration.ofHours(24);
	private static final String HASH_ALGORITHM = "SHA-256";

	private final PortfolioPersistencePort persistencePort;
	private final Clock clock;

	public CreatePortfolioService(PortfolioPersistencePort persistencePort, Clock clock) {
		this.persistencePort = Objects.requireNonNull(persistencePort, "persistencePort must not be null");
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
	}

	@Override
	public Portfolio execute(UserId ownerId, CreatePortfolioCommand command) {
		Objects.requireNonNull(ownerId, "ownerId must not be null");
		Objects.requireNonNull(command, "command must not be null");
		try {
			Instant now = clock.instant();
			Portfolio portfolio = Portfolio.create(
					ownerId, command.name(), command.currency(), now);
			String requestHash = requestHash(portfolio.name(), portfolio.currency().value());
			return persistencePort.createIdempotently(
					portfolio, command.idempotencyKey(), requestHash, now.plus(IDEMPOTENCY_RETENTION));
		} catch (IllegalArgumentException exception) {
			throw new InvalidPortfolioException(exception.getMessage(), exception);
		}
	}

	private String requestHash(String name, String currency) {
		try {
			byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM)
					.digest((name + "\n" + currency).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("Required request hash algorithm is unavailable", exception);
		}
	}
}
