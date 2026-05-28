package com.byld.portfolio.service;

import com.byld.portfolio.dto.Dtos.*;
import com.byld.portfolio.entity.Dividend;
import com.byld.portfolio.entity.Holding;
import com.byld.portfolio.entity.Portfolio;
import com.byld.portfolio.repository.DividendRepository;
import com.byld.portfolio.repository.HoldingRepository;
import com.byld.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DividendService {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int SCALE = 4;

    private final DividendRepository dividendRepository;
    private final HoldingRepository holdingRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioService portfolioService;

    /**
     * Records a dividend for a given symbol and recordDate.
     *
     * Business rules:
     * 1. Look up the current holding quantity for the symbol in this portfolio.
     *    (In a real system you'd snapshot quantity as of recordDate from transaction history;
     *     here we use current holdings as a reasonable approximation.)
     * 2. Compute totalPayout = sharesAtRecord * perShareAmount.
     * 3. Credit portfolio.cashBalance += totalPayout.
     * 4. Persist the Dividend record.
     *
     * If no holding exists for the symbol, sharesAtRecord = 0 and payout = 0
     * (portfolio had no exposure — still record the event for audit purposes).
     */
    @Transactional
    public DividendResponse recordDividend(UUID portfolioId, RecordDividendRequest req) {
        Portfolio portfolio = portfolioService.findPortfolioOrThrow(portfolioId);
        String symbol = req.symbol().trim().toUpperCase();

        Optional<Holding> holdingOpt = holdingRepository.findByPortfolioIdAndSymbol(portfolioId, symbol);
        BigDecimal sharesAtRecord = holdingOpt
                .map(Holding::getQuantity)
                .orElse(BigDecimal.ZERO);

        BigDecimal totalPayout = sharesAtRecord
                .multiply(req.perShareAmount(), MC)
                .setScale(SCALE, RoundingMode.HALF_UP);

        // Credit cash balance
        portfolio.setCashBalance(
                portfolio.getCashBalance().add(totalPayout, MC).setScale(SCALE, RoundingMode.HALF_UP));
        portfolioRepository.save(portfolio);

        Dividend dividend = new Dividend(
                portfolio, symbol,
                req.perShareAmount(),
                req.recordDate(),
                sharesAtRecord,
                totalPayout
        );
        dividend = dividendRepository.save(dividend);

        log.info("Dividend recorded portfolio={} symbol={} perShare={} shares={} payout={}",
                portfolioId, symbol, req.perShareAmount(), sharesAtRecord, totalPayout);

        return toDividendResponse(dividend);
    }

    /**
     * Returns all dividends for a portfolio, grouped by symbol with totals.
     */
    @Transactional(readOnly = true)
    public DividendSummaryResponse getDividends(UUID portfolioId) {
        portfolioService.findPortfolioOrThrow(portfolioId);

        List<Dividend> dividends = dividendRepository.findByPortfolioIdOrderByRecordDateDesc(portfolioId);

        List<DividendResponse> dividendResponses = dividends.stream()
                .map(this::toDividendResponse)
                .toList();

        // Build per-symbol totals from the already-loaded list (avoids extra DB round-trip for small sets)
        Map<String, BigDecimal> totalsMap = dividends.stream()
                .collect(Collectors.groupingBy(
                        Dividend::getSymbol,
                        Collectors.reducing(BigDecimal.ZERO, Dividend::getTotalPayout, BigDecimal::add)
                ));

        List<DividendSummaryResponse.SymbolTotal> totals = totalsMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new DividendSummaryResponse.SymbolTotal(
                        e.getKey(),
                        e.getValue().setScale(SCALE, RoundingMode.HALF_UP)))
                .toList();

        return new DividendSummaryResponse(dividendResponses, totals);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private DividendResponse toDividendResponse(Dividend d) {
        return new DividendResponse(
                d.getId(),
                d.getSymbol(),
                d.getPerShareAmount(),
                d.getRecordDate(),
                d.getSharesAtRecord(),
                d.getTotalPayout(),
                d.getCreatedAt()
        );
    }
}
