package com.ledgerx.portfolio.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.jayway.jsonpath.JsonPath.read;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerx.identity.adapter.security.AuthenticatedUserProvider;
import com.ledgerx.identity.domain.UserId;
import com.ledgerx.portfolio.application.model.PortfolioSummary;
import com.ledgerx.portfolio.application.model.PortfolioSummary.ValuationStatus;
import com.ledgerx.portfolio.application.port.in.CreatePortfolioUseCase;
import com.ledgerx.portfolio.application.port.in.CreatePortfolioUseCase.CreatePortfolioCommand;
import com.ledgerx.portfolio.application.port.in.ListPortfoliosUseCase;
import com.ledgerx.portfolio.domain.Portfolio;
import com.ledgerx.security.RequestCorrelationFilter;
import com.ledgerx.security.SecurityConfiguration;
import com.ledgerx.security.SecurityProblemWriter;
import com.ledgerx.shared.web.ApiExceptionHandler;

@WebMvcTest(controllers = PortfolioController.class)

@Import({SecurityConfiguration.class, SecurityProblemWriter.class, RequestCorrelationFilter.class,
		AuthenticatedUserProvider.class,
		ApiExceptionHandler.class, PortfolioControllerTest.JsonTestConfiguration.class})
@TestPropertySource(properties = {
		"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://ledgerx.test/auth/v1",
		"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://ledgerx.test/auth/v1/.well-known/jwks.json",
		"ledgerx.security.jwt.audience=authenticated"
})
class PortfolioControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CreatePortfolioUseCase createPortfolioUseCase;

	@MockitoBean
	private ListPortfoliosUseCase listPortfoliosUseCase;

	@Test
	void createsPortfolioForAuthenticatedOwnerAndReturnsLocation() throws Exception {
		UUID ownerId = UUID.randomUUID();
		Portfolio portfolio = Portfolio.create(new UserId(ownerId), "Long-term fund", "USD",
				Instant.parse("2026-01-01T00:00:00Z"));
		given(createPortfolioUseCase.execute(eq(new UserId(ownerId)), any(CreatePortfolioCommand.class)))
				.willReturn(portfolio);

		mockMvc.perform(post("/api/v1/portfolios")
					.with(jwt().jwt(token -> token.subject(ownerId.toString())))
					.header("Idempotency-Key", "portfolio-create-0001")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Long-term fund\",\"currency\":\"USD\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/v1/portfolios/" + portfolio.id()))
				.andExpect(jsonPath("$.id").value(portfolio.id().toString()))
				.andExpect(jsonPath("$.name").value("Long-term fund"))
				.andExpect(jsonPath("$.currency").value("USD"));
	}

	@Test
	void listsOnlySummariesReturnedForAuthenticatedOwner() throws Exception {
		UUID ownerId = UUID.randomUUID();
		given(listPortfoliosUseCase.execute(new UserId(ownerId))).willReturn(List.of(
				new PortfolioSummary(UUID.randomUUID(), "Retirement", null, "INR", ValuationStatus.INCOMPLETE, null)));

		mockMvc.perform(get("/api/v1/portfolios")
					.with(jwt().jwt(token -> token.subject(ownerId.toString()))))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$[0].name").value("Retirement"))
				.andExpect(jsonPath("$[0].totalValueBaseCurrency").doesNotExist())
				.andExpect(jsonPath("$[0].valuationStatus").value("INCOMPLETE"));
	}

	@Test
	void rejectsCreateWithoutIdempotencyKeyUsingProblemDetails() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/portfolios")
					.with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString())))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Long-term fund\",\"currency\":\"USD\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andReturn();
		assertEquals(result.getResponse().getHeader(RequestCorrelationFilter.TRACE_ID_HEADER),
				read(result.getResponse().getContentAsString(), "$.traceId"));
	}

	@Test
	void rejectsMalformedCreatePayloadUsingProblemDetails() throws Exception {
		mockMvc.perform(post("/api/v1/portfolios")
					.with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString())))
					.header("Idempotency-Key", "portfolio-create-0003")
					.contentType(MediaType.APPLICATION_JSON)
					.content("not-json"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class JsonTestConfiguration {

		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}
	}
}
