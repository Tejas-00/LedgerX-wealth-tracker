package com.ledgerx;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerx.security.RequestCorrelationFilter;
import com.ledgerx.security.SecurityConfiguration;
import com.ledgerx.security.SecurityProblemWriter;

@WebMvcTest(controllers = SecurityProbeController.class)
@Import({SecurityConfiguration.class, SecurityProblemWriter.class, RequestCorrelationFilter.class,
		LedgerXApplicationTests.JsonTestConfiguration.class})
@TestPropertySource(properties = {
		"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://ledgerx.test/auth/v1",
		"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://ledgerx.test/auth/v1/.well-known/jwks.json",
		"ledgerx.security.jwt.audience=authenticated"
})
class LedgerXApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void protectedEndpointReturnsProblemDetailsWhenUnauthenticated() throws Exception {
		mockMvc.perform(get("/probe"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.traceId").isNotEmpty())
				.andExpect(header().exists(RequestCorrelationFilter.TRACE_ID_HEADER));
	}

	@Test
	void healthPathDoesNotRequireAuthentication() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class JsonTestConfiguration {

		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}
	}

}
