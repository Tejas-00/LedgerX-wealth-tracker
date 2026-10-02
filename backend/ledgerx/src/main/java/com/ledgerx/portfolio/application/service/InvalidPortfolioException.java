package com.ledgerx.portfolio.application.service;

public class InvalidPortfolioException extends RuntimeException {

	public InvalidPortfolioException(String message, Throwable cause) {
		super(message, cause);
	}
}
