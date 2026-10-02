package com.ledgerx.portfolio.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ledgerx.identity.domain.UserId;
import com.ledgerx.portfolio.application.model.PortfolioSummary.ValuationStatus;
import com.ledgerx.portfolio.application.port.in.CreatePortfolioUseCase;
import com.ledgerx.portfolio.application.port.in.CreatePortfolioUseCase.CreatePortfolioCommand;
import com.ledgerx.portfolio.application.port.in.ListPortfoliosUseCase;
import com.ledgerx.portfolio.application.service.IdempotencyConflictException;
import com.ledgerx.portfolio.domain.Portfolio;
import com.ledgerx.support.PostgresIntegrationTest;

@SpringBootTest
class PortfolioPersistenceIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private CreatePortfolioUseCase createPortfolioUseCase;

	@Autowired
	private ListPortfoliosUseCase listPortfoliosUseCase;

	@Test
	void idempotentReplayReturnsOriginalPortfolioAndRejectsChangedPayload() {
		UserId ownerId = new UserId(UUID.randomUUID());
		String idempotencyKey = UUID.randomUUID().toString();
		Portfolio created = createPortfolioUseCase.execute(ownerId,
				new CreatePortfolioCommand("Retirement", "USD", idempotencyKey));
		Portfolio replay = createPortfolioUseCase.execute(ownerId,
				new CreatePortfolioCommand("Retirement", "USD", idempotencyKey));

		assertEquals(created.id(), replay.id());
		assertThrows(IdempotencyConflictException.class, () -> createPortfolioUseCase.execute(ownerId,
				new CreatePortfolioCommand("Emergency fund", "USD", idempotencyKey)));
	}

	@Test
	void portfolioListIsScopedToOwnerAndUnvaluedTotalsAreExplicitlyIncomplete() {
		UserId ownerId = new UserId(UUID.randomUUID());
		UserId otherOwnerId = new UserId(UUID.randomUUID());
		createPortfolioUseCase.execute(ownerId,
				new CreatePortfolioCommand("Owner portfolio", "USD", UUID.randomUUID().toString()));
		createPortfolioUseCase.execute(otherOwnerId,
				new CreatePortfolioCommand("Other portfolio", "EUR", UUID.randomUUID().toString()));

		var summaries = listPortfoliosUseCase.execute(ownerId);

		assertEquals(1, summaries.size());
		assertEquals("Owner portfolio", summaries.getFirst().name());
		assertEquals(null, summaries.getFirst().totalValueBaseCurrency());
		assertEquals(ValuationStatus.INCOMPLETE, summaries.getFirst().valuationStatus());
	}
}
