package com.ledgerx.identity.adapter.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.ledgerx.identity.domain.UserId;

@Component
public class AuthenticatedUserProvider {

	public UserId requireUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
			throw new AuthenticationCredentialsNotFoundException("Authenticated JWT principal is required");
		}
		return UserId.fromSubject(jwtAuthentication.getToken().getSubject());
	}
}
