package com.ledgerx.portfolio.application.port.in;

import java.util.Objects;
import java.util.regex.Pattern;

import com.ledgerx.identity.domain.UserId;
import com.ledgerx.portfolio.domain.Portfolio;

public interface CreatePortfolioUseCase {

	Portfolio execute(UserId ownerId, CreatePortfolioCommand command);

	record CreatePortfolioCommand(String name, String currency, String idempotencyKey) {
		private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._~-]{16,128}");

		public CreatePortfolioCommand {
			Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
			if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
				throw new IllegalArgumentException("idempotencyKey must contain 16 to 128 allowed characters");
			}
		}
	}
}
