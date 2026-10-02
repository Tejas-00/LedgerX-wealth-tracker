package com.ledgerx.portfolio.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

public interface PortfolioSummaryProjection {

	UUID getId();

	String getName();

	String getBaseCurrency();

	Instant getCreatedAt();
}
