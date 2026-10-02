package com.ledgerx.portfolio.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, UUID> {

	@Modifying
	@Query(value = """
			INSERT INTO user_profiles (id, base_currency, created_at, updated_at)
			VALUES (:userId, 'INR', :createdAt, :createdAt)
			ON CONFLICT (id) DO NOTHING
			""", nativeQuery = true)
	int createDefaultProfileIfMissing(@Param("userId") UUID userId, @Param("createdAt") Instant createdAt);
}
