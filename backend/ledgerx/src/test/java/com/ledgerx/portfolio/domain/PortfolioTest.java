package com.ledgerx.portfolio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ledgerx.identity.domain.UserId;

class PortfolioTest {

	@Test
	void normalizesPortfolioNameAndCurrency() {
		Portfolio portfolio = Portfolio.create(new UserId(UUID.randomUUID()), "  Long-term fund  ", "usd",
				Instant.parse("2026-01-01T00:00:00Z"));

		assertEquals("Long-term fund", portfolio.name());
		assertEquals("USD", portfolio.currency().value());
	}

	@Test
	void rejectsBlankName() {
		assertThrows(IllegalArgumentException.class, () -> Portfolio.create(
				new UserId(UUID.randomUUID()), "   ", "USD", Instant.now()));
	}

	@Test
	void rejectsUnknownCurrency() {
		assertThrows(IllegalArgumentException.class, () -> Portfolio.create(
				new UserId(UUID.randomUUID()), "Savings", "ABC", Instant.now()));
	}

	@Test
	void rejectsCurrencyWithoutDefinedMinorUnit() {
		assertThrows(IllegalArgumentException.class, () -> Portfolio.create(
				new UserId(UUID.randomUUID()), "Savings", "XXX", Instant.now()));
	}

	@Test
	void rejectsNamesLongerThanContractLimit() {
		String longName = "x".repeat(Portfolio.MAX_NAME_LENGTH + 1);
		assertThrows(IllegalArgumentException.class, () -> Portfolio.create(
				new UserId(UUID.randomUUID()), longName, "USD", Instant.now()));
	}
}
