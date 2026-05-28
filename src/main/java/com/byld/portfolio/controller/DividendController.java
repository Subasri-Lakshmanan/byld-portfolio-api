package com.byld.portfolio.controller;

import com.byld.portfolio.dto.Dtos.*;
import com.byld.portfolio.service.DividendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/portfolios")
@RequiredArgsConstructor
@Tag(name = "Dividends", description = "Variant B — dividend recording and retrieval")
public class DividendController {

    private final DividendService dividendService;

    @PostMapping("/{id}/dividends")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a dividend event; credits cash balance based on held quantity")
    public DividendResponse recordDividend(@PathVariable UUID id,
                                           @Valid @RequestBody RecordDividendRequest req) {
        return dividendService.recordDividend(id, req);
    }

    @GetMapping("/{id}/dividends")
    @Operation(summary = "List all dividends received, grouped by symbol with totals")
    public DividendSummaryResponse getDividends(@PathVariable UUID id) {
        return dividendService.getDividends(id);
    }
}
