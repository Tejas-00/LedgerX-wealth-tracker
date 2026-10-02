package com.ledgerx.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.ledgerx.identity.adapter.security.SupabaseClaimsValidator;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, SecurityProblemWriter problemWriter) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, exception) -> problemWriter.write(
						request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"))
				.accessDeniedHandler((request, response, exception) -> problemWriter.write(
						request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is not permitted")))
			.oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
		return http.build();
	}

	@Bean
	JwtDecoder jwtDecoder(
			@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
			@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
			@Value("${ledgerx.security.jwt.audience}") String audience) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(issuerUri), new SupabaseClaimsValidator(audience)));
		return decoder;
	}
}
