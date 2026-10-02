package com.ledgerx.identity.adapter.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SupabaseClaimsValidatorTest {

	private static final String AUDIENCE = "authenticated";

	private final SupabaseClaimsValidator validator = new SupabaseClaimsValidator(AUDIENCE);

	@Test
	void acceptsTokenWithExpectedAudienceAndUuidSubject() {
		Jwt token = token(List.of(AUDIENCE), UUID.randomUUID().toString());

		assertFalse(validator.validate(token).hasErrors());
	}

	@Test
	void rejectsTokenWithUnexpectedAudience() {
		Jwt token = token(List.of("public"), UUID.randomUUID().toString());

		assertTrue(validator.validate(token).hasErrors());
	}

	@Test
	void rejectsTokenWithNonUuidSubject() {
		Jwt token = token(List.of(AUDIENCE), "user-123");

		assertTrue(validator.validate(token).hasErrors());
	}

	private Jwt token(List<String> audiences, String subject) {
		return Jwt.withTokenValue("test-token")
				.header("alg", "RS256")
				.subject(subject)
				.audience(audiences)
				.issuedAt(java.time.Instant.now())
				.expiresAt(java.time.Instant.now().plusSeconds(60))
				.build();
	}
}
