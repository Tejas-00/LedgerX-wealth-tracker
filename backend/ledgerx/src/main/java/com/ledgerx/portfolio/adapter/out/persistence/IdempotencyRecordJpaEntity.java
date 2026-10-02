package com.ledgerx.portfolio.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecordJpaEntity {

	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	@Column(nullable = false, length = 120)
	private String operation;

	@Column(name = "idempotency_key", nullable = false, length = 128)
	private String idempotencyKey;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(name = "resource_id")
	private UUID resourceId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected IdempotencyRecordJpaEntity() {
	}

	public String requestHash() {
		return requestHash;
	}

	public UUID resourceId() {
		return resourceId;
	}
}
