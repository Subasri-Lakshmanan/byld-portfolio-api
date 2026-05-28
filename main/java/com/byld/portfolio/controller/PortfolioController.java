package com.byld.portfolio.controller;

import com.byld.portfolio.dto.Dtos.*;
import com.byld.portfolio.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/portfolios")
@RequiredArgsConstructor
@Tag(name = "Portfolios", description = "Portfolio management and transaction endpoints")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new portfolio")
    public PortfolioResponse createPortfolio(@Valid @RequestBody CreatePortfolioRequest req) {
        return portfolioService.createPortfolio(req);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get portfolio summary with cash balance and holdings")
    public PortfolioResponse getPortfolio(@PathVariable UUID id) {
        return portfolioService.getPortfolio(id);
    }

    @PostMapping("/{id}/transactions/buy")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a BUY transaction")
    public TransactionResponse buy(@PathVariable UUID id,
                                   @Valid @RequestBody BuyRequest req) {
        return portfolioService.buy(id, req);
    }

    @PostMapping("/{id}/transactions/sell")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a SELL transaction (409 if quantity exceeds held)")
    public TransactionResponse sell(@PathVariable UUID id,
                                    @Valid @RequestBody SellRequest req) {
        return portfolioService.sell(id, req);
    }

    @GetMapping("/{id}/holdings")
    @Operation(summary = "Get all holdings with weighted-average cost basis")
    public List<HoldingResponse> getHoldings(@PathVariable UUID id) {
        return portfolioService.getHoldings(id);
    }
}
