package com.ledgerx.portfolio.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profiles")
public class UserProfileJpaEntity {

	@Id
	private UUID id;

	@Column(name = "base_currency", nullable = false, length = 3)
	private String baseCurrency;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserProfileJpaEntity() {
	}
}
