package com.ledgerx.identity.adapter.security;

import java.util.UUID;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class SupabaseClaimsValidator implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
			"invalid_token", "Token is missing a valid LedgerX audience or UUID subject", null);

	private final String requiredAudience;

	public SupabaseClaimsValidator(String requiredAudience) {
		if (requiredAudience == null || requiredAudience.isBlank()) {
			throw new IllegalArgumentException("requiredAudience must not be blank");
		}
		this.requiredAudience = requiredAudience;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		if (!token.getAudience().contains(requiredAudience) || !hasUuidSubject(token.getSubject())) {
			return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
		}
		return OAuth2TokenValidatorResult.success();
	}

	private boolean hasUuidSubject(String subject) {
		try {
			UUID subjectId = UUID.fromString(subject);
			return subjectId.toString().equalsIgnoreCase(subject);
		} catch (IllegalArgumentException | NullPointerException exception) {
			return false;
		}
	}
}
