package com.ledgerx.portfolio.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface IdempotencyRecordJpaRepository extends JpaRepository<IdempotencyRecordJpaEntity, UUID> {

	@Modifying
	@Query(value = """
			DELETE FROM idempotency_records
			WHERE owner_id = :ownerId AND operation = :operation
			  AND idempotency_key = :key AND expires_at <= :now
			""", nativeQuery = true)
	int deleteExpired(@Param("ownerId") UUID ownerId, @Param("operation") String operation,
			@Param("key") String key, @Param("now") Instant now);

	@Modifying
	@Query(value = """
			INSERT INTO idempotency_records
				(id, owner_id, operation, idempotency_key, request_hash, created_at, expires_at)
			VALUES (:id, :ownerId, :operation, :key, :requestHash, :createdAt, :expiresAt)
			ON CONFLICT (owner_id, operation, idempotency_key) DO NOTHING
			""", nativeQuery = true)
	int claim(@Param("id") UUID id, @Param("ownerId") UUID ownerId, @Param("operation") String operation,
			@Param("key") String key, @Param("requestHash") String requestHash,
			@Param("createdAt") Instant createdAt, @Param("expiresAt") Instant expiresAt);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select record from IdempotencyRecordJpaEntity record where record.ownerId = :ownerId "
			+ "and record.operation = :operation and record.idempotencyKey = :key")
	Optional<IdempotencyRecordJpaEntity> findForUpdate(@Param("ownerId") UUID ownerId,
			@Param("operation") String operation, @Param("key") String key);

	@Modifying
	@Query(value = """
			UPDATE idempotency_records SET resource_id = :resourceId
			WHERE owner_id = :ownerId AND operation = :operation AND idempotency_key = :key
			""", nativeQuery = true)
	int attachResource(@Param("ownerId") UUID ownerId, @Param("operation") String operation,
			@Param("key") String key, @Param("resourceId") UUID resourceId);
}
