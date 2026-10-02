package com.ledgerx.portfolio.adapter.in.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ledgerx.identity.adapter.security.AuthenticatedUserProvider;
import com.ledgerx.portfolio.application.model.PortfolioSummary;
import com.ledgerx.portfolio.application.port.in.CreatePortfolioUseCase;
import com.ledgerx.portfolio.application.port.in.CreatePortfolioUseCase.CreatePortfolioCommand;
import com.ledgerx.portfolio.application.port.in.ListPortfoliosUseCase;
import com.ledgerx.portfolio.domain.Portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/portfolios")
@Validated
public class PortfolioController {

	private final CreatePortfolioUseCase createPortfolioUseCase;
	private final ListPortfoliosUseCase listPortfoliosUseCase;
	private final AuthenticatedUserProvider authenticatedUserProvider;

	public PortfolioController(CreatePortfolioUseCase createPortfolioUseCase,
			ListPortfoliosUseCase listPortfoliosUseCase, AuthenticatedUserProvider authenticatedUserProvider) {
		this.createPortfolioUseCase = createPortfolioUseCase;
		this.listPortfoliosUseCase = listPortfoliosUseCase;
		this.authenticatedUserProvider = authenticatedUserProvider;
	}

	@GetMapping
	public List<PortfolioSummaryResponse> listPortfolios() {
		return listPortfoliosUseCase.execute(authenticatedUserProvider.requireUserId()).stream()
				.map(PortfolioSummaryResponse::from)
				.toList();
	}

	@PostMapping
	public ResponseEntity<CreatedPortfolioResponse> createPortfolio(
			@Valid @RequestBody CreatePortfolioRequest request,
			@RequestHeader("Idempotency-Key") @NotBlank @Size(min = 16, max = 128)
			@Pattern(regexp = "[A-Za-z0-9._~-]{16,128}") String idempotencyKey) {
		Portfolio created = createPortfolioUseCase.execute(authenticatedUserProvider.requireUserId(),
				new CreatePortfolioCommand(request.name(), request.currency(), idempotencyKey));
		URI location = URI.create("/api/v1/portfolios/" + created.id());
		return ResponseEntity.created(location).body(CreatedPortfolioResponse.from(created));
	}

	public record CreatePortfolioRequest(
			@NotBlank @Size(max = Portfolio.MAX_NAME_LENGTH) String name,
			@NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
	}

	public record CreatedPortfolioResponse(UUID id, String name, String currency, Instant createdAt) {

		private static CreatedPortfolioResponse from(Portfolio portfolio) {
			return new CreatedPortfolioResponse(portfolio.id(), portfolio.name(),
					portfolio.currency().value(), portfolio.createdAt());
		}
	}

	public record PortfolioSummaryResponse(UUID id, String name, String totalValueBaseCurrency,
			String currency, PortfolioSummary.ValuationStatus valuationStatus, Instant valuationAsOf) {

		private static PortfolioSummaryResponse from(PortfolioSummary summary) {
			String total = summary.totalValueBaseCurrency() == null
					? null
					: summary.totalValueBaseCurrency().toPlainString();
			return new PortfolioSummaryResponse(summary.id(), summary.name(), total,
					summary.currency(), summary.valuationStatus(), summary.valuationAsOf());
		}
	}

}
