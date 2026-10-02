package com.ledgerx.identity.domain;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

	public UserId {
		Objects.requireNonNull(value, "value must not be null");
	}

	public static UserId fromSubject(String subject) {
		return new UserId(UUID.fromString(subject));
	}
}
