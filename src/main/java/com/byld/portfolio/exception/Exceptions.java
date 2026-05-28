package com.byld.portfolio.exception;

import java.util.UUID;

public final class Exceptions {

    private Exceptions() {}

    public static class PortfolioNotFoundException extends RuntimeException {
        public PortfolioNotFoundException(UUID id) {
            super("Portfolio not found: " + id);
        }
    }

    public static class InsufficientHoldingException extends RuntimeException {
        public InsufficientHoldingException(String symbol, Object requested, Object available) {
            super(String.format(
                    "Insufficient holding for %s: requested %s but only %s available",
                    symbol, requested, available));
        }
    }

    public static class HoldingNotFoundException extends RuntimeException {
        public HoldingNotFoundException(String symbol) {
            super("No holding found for symbol: " + symbol);
        }
    }
}
