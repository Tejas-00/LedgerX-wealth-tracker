package com.ledgerx.portfolio.domain;

import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

public record CurrencyCode(String value) {

	public CurrencyCode {
		Objects.requireNonNull(value, "value must not be null");
		String normalizedValue = value.trim().toUpperCase(Locale.ROOT);
		if (normalizedValue.length() != 3) {
			throw new IllegalArgumentException("currency must be an ISO 4217 code");
		}
		try {
			Currency currency = Currency.getInstance(normalizedValue);
			if (currency.getDefaultFractionDigits() < 0) {
				throw new IllegalArgumentException("currency must have a defined ISO 4217 minor unit");
			}
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("currency must be a supported ISO 4217 code", exception);
		}
		value = normalizedValue;
	}
}
