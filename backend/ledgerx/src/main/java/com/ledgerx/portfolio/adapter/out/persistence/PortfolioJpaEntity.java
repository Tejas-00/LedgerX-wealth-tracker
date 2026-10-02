package com.ledgerx.portfolio.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.ledgerx.identity.domain.UserId;
import com.ledgerx.portfolio.domain.CurrencyCode;
import com.ledgerx.portfolio.domain.Portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "portfolios")
public class PortfolioJpaEntity {

	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "is_default", nullable = false)
	private boolean defaultPortfolio;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PortfolioJpaEntity() {
	}

	public PortfolioJpaEntity(Portfolio portfolio) {
		this.id = portfolio.id();
		this.ownerId = portfolio.ownerId().value();
		this.name = portfolio.name();
		this.currency = portfolio.currency().value();
		this.createdAt = portfolio.createdAt();
	}

	public Portfolio toDomain() {
		return new Portfolio(id, new UserId(ownerId), name, new CurrencyCode(currency.trim()), createdAt);
	}
}
