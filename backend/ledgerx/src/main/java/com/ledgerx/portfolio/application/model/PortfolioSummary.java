package com.ledgerx.portfolio.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PortfolioSummary(
		UUID id,
		String name,
		BigDecimal totalValueBaseCurrency,
		String currency,
		ValuationStatus valuationStatus,
		Instant valuationAsOf) {

	public enum ValuationStatus {
		COMPLETE,
		INCOMPLETE,
		STALE
	}
}
