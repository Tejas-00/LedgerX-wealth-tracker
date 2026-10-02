package com.ledgerx.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {

	@Container
	protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("ledgerx_test");

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("SUPABASE_DB_URL", POSTGRES::getJdbcUrl);
		registry.add("SUPABASE_DB_USERNAME", POSTGRES::getUsername);
		registry.add("SUPABASE_DB_PASSWORD", POSTGRES::getPassword);
		registry.add("SUPABASE_JWT_ISSUER_URI", () -> "https://ledgerx.test/auth/v1");
		registry.add("SUPABASE_JWKS_URI", () -> "https://ledgerx.test/auth/v1/.well-known/jwks.json");
		registry.add("SUPABASE_JWT_AUDIENCE", () -> "authenticated");
	}
}
