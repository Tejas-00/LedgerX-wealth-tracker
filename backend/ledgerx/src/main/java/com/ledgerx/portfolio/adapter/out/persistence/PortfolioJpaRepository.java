package com.ledgerx.portfolio.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioJpaRepository extends JpaRepository<PortfolioJpaEntity, UUID> {

	@Query(value = """
			SELECT p.id AS \"id\", p.name AS \"name\",
			       COALESCE(u.base_currency, 'INR') AS \"baseCurrency\",
			       p.created_at AS \"createdAt\"
			FROM portfolios p
			LEFT JOIN user_profiles u ON u.id = p.owner_id
			WHERE p.owner_id = :ownerId
			ORDER BY p.created_at DESC, p.id
			""", nativeQuery = true)
	List<PortfolioSummaryProjection> findSummariesByOwnerId(@Param("ownerId") UUID ownerId);

	java.util.Optional<PortfolioJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
}
