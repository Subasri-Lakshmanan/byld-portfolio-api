package com.byld.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class Dtos {

    private Dtos() {}

    // ── Request DTOs ──────────────────────────────────────────────────────────

    @Schema(description = "Request body for creating a portfolio")
    public record CreatePortfolioRequest(
            @NotBlank(message = "clientName is required")
            @Schema(example = "Arjun Mehta")
            String clientName,

            @NotBlank(message = "riskProfile is required")
            @Schema(example = "AGGRESSIVE", allowableValues = {"CONSERVATIVE", "MODERATE", "AGGRESSIVE"})
            String riskProfile
    ) {}

    @Schema(description = "Request body for a BUY transaction")
    public record BuyRequest(
            @NotBlank(message = "symbol is required")
            @Schema(example = "RELIANCE")
            String symbol,

            @NotNull(message = "quantity is required")
            @DecimalMin(value = "0.0001", message = "quantity must be positive")
            @Schema(example = "10.0000")
            BigDecimal quantity,

            @NotNull(message = "price is required")
            @DecimalMin(value = "0.0001", message = "price must be positive")
            @Schema(example = "2950.5000")
            BigDecimal price
    ) {}

    @Schema(description = "Request body for a SELL transaction")
    public record SellRequest(
            @NotBlank(message = "symbol is required")
            String symbol,

            @NotNull(message = "quantity is required")
            @DecimalMin(value = "0.0001", message = "quantity must be positive")
            BigDecimal quantity,

            @NotNull(message = "price is required")
            @DecimalMin(value = "0.0001", message = "price must be positive")
            BigDecimal price
    ) {}

    @Schema(description = "Request body for recording a dividend event")
    public record RecordDividendRequest(
            @NotBlank(message = "symbol is required")
            String symbol,

            @NotNull(message = "perShareAmount is required")
            @DecimalMin(value = "0.0001", message = "perShareAmount must be positive")
            BigDecimal perShareAmount,

            @NotNull(message = "recordDate is required")
            LocalDate recordDate
    ) {}

    // ── Response DTOs ─────────────────────────────────────────────────────────

    @Schema(description = "Portfolio summary including holdings and cash balance")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PortfolioResponse(
            UUID id,
            String clientName,
            String riskProfile,
            BigDecimal cashBalance,
            List<HoldingResponse> holdings,
            Instant createdAt
    ) {}

    @Schema(description = "A single holding with cost basis and unrealised P&L")
    public record HoldingResponse(
            UUID id,
            String symbol,
            BigDecimal quantity,
            BigDecimal avgCostBasis,
            BigDecimal totalCost
    ) {}

    @Schema(description = "A recorded transaction")
    public record TransactionResponse(
            UUID id,
            String symbol,
            String type,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal totalValue,
            Instant transactedAt
    ) {}

    @Schema(description = "A dividend entry recorded for a portfolio")
    public record DividendResponse(
            UUID id,
            String symbol,
            BigDecimal perShareAmount,
            LocalDate recordDate,
            BigDecimal sharesAtRecord,
            BigDecimal totalPayout,
            Instant createdAt
    ) {}

    @Schema(description = "Dividend summary grouped by symbol")
    public record DividendSummaryResponse(
            List<DividendResponse> dividends,
            List<SymbolTotal> totals
    ) {
        public record SymbolTotal(String symbol, BigDecimal totalPayout) {}
    }

    // ── Error DTO ─────────────────────────────────────────────────────────────

    public record ErrorResponse(
            int status,
            String error,
            String message,
            String requestId,
            Instant timestamp
    ) {}
}
