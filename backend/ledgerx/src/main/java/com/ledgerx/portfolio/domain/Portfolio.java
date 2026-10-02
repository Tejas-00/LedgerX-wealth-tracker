package com.ledgerx.portfolio.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.ledgerx.identity.domain.UserId;

public record Portfolio(UUID id, UserId ownerId, String name, CurrencyCode currency, Instant createdAt) {

	public static final int MAX_NAME_LENGTH = 120;

	public Portfolio {
		Objects.requireNonNull(id, "id must not be null");
		Objects.requireNonNull(ownerId, "ownerId must not be null");
		Objects.requireNonNull(currency, "currency must not be null");
		Objects.requireNonNull(createdAt, "createdAt must not be null");
		name = normalizeName(name);
	}

	public static Portfolio create(UserId ownerId, String name, String currency, Instant createdAt) {
		return new Portfolio(UUID.randomUUID(), ownerId, name, new CurrencyCode(currency), createdAt);
	}

	private static String normalizeName(String name) {
		Objects.requireNonNull(name, "name must not be null");
		String normalizedName = name.trim();
		if (normalizedName.isEmpty()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		if (normalizedName.length() > MAX_NAME_LENGTH) {
			throw new IllegalArgumentException("name must be at most " + MAX_NAME_LENGTH + " characters");
		}
		return normalizedName;
	}
}
