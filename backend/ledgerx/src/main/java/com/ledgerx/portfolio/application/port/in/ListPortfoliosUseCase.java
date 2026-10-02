package com.ledgerx.portfolio.application.port.in;

import java.util.List;

import com.ledgerx.identity.domain.UserId;
import com.ledgerx.portfolio.application.model.PortfolioSummary;

public interface ListPortfoliosUseCase {

	List<PortfolioSummary> execute(UserId ownerId);
}
