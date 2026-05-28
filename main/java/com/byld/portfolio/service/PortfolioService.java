package com.byld.portfolio.service;

import com.byld.portfolio.dto.Dtos.*;
import com.byld.portfolio.entity.Holding;
import com.byld.portfolio.entity.Portfolio;
import com.byld.portfolio.entity.Transaction;
import com.byld.portfolio.exception.Exceptions.*;
import com.byld.portfolio.repository.HoldingRepository;
import com.byld.portfolio.repository.PortfolioRepository;
import com.byld.portfolio.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int SCALE = 4;

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public PortfolioResponse createPortfolio(CreatePortfolioRequest req) {
        Portfolio portfolio = new Portfolio(req.clientName().trim(), req.riskProfile().trim().toUpperCase());
        portfolio = portfolioRepository.save(portfolio);
        log.info("Created portfolio id={} client={}", portfolio.getId(), portfolio.getClientName());
        return toResponse(portfolio, List.of());
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(UUID id) {
        Portfolio portfolio = findPortfolioOrThrow(id);
        List<Holding> holdings = holdingRepository.findByPortfolioId(id);
        return toResponse(portfolio, holdings);
    }

    @Transactional
    public TransactionResponse buy(UUID portfolioId, BuyRequest req) {
        Portfolio portfolio = findPortfolioOrThrow(portfolioId);
        String symbol = req.symbol().trim().toUpperCase();

        Holding holding = holdingRepository
                .findByPortfolioIdAndSymbol(portfolioId, symbol)
                .orElseGet(() -> new Holding(portfolio, symbol));

        // Weighted-average cost basis recalculation:
        // newAvg = (oldQty * oldAvg + buyQty * buyPrice) / (oldQty + buyQty)
        BigDecimal oldQty = holding.getQuantity();
        BigDecimal oldAvg = holding.getAvgCostBasis();
        BigDecimal buyQty = req.quantity();
        BigDecimal buyPrice = req.price();

        BigDecimal newQty = oldQty.add(buyQty, MC);
        BigDecimal numerator = oldQty.multiply(oldAvg, MC).add(buyQty.multiply(buyPrice, MC), MC);
        BigDecimal newAvg = numerator.divide(newQty, SCALE, RoundingMode.HALF_UP);

        holding.setQuantity(newQty.setScale(SCALE, RoundingMode.HALF_UP));
        holding.setAvgCostBasis(newAvg);
        holdingRepository.save(holding);

        Transaction tx = new Transaction(portfolio, symbol, Transaction.Type.BUY, buyQty, buyPrice);
        tx = transactionRepository.save(tx);

        log.info("BUY portfolio={} symbol={} qty={} price={} newAvg={}",
                portfolioId, symbol, buyQty, buyPrice, newAvg);

        return toTransactionResponse(tx);
    }

    @Transactional
    public TransactionResponse sell(UUID portfolioId, SellRequest req) {
        Portfolio portfolio = findPortfolioOrThrow(portfolioId);
        String symbol = req.symbol().trim().toUpperCase();

        Holding holding = holdingRepository
                .findByPortfolioIdAndSymbol(portfolioId, symbol)
                .orElseThrow(() -> new HoldingNotFoundException(symbol));

        BigDecimal sellQty = req.quantity();
        if (sellQty.compareTo(holding.getQuantity()) > 0) {
            throw new InsufficientHoldingException(symbol, sellQty, holding.getQuantity());
        }

        BigDecimal newQty = holding.getQuantity().subtract(sellQty, MC)
                .setScale(SCALE, RoundingMode.HALF_UP);
        holding.setQuantity(newQty);
        // Cost basis stays the same on SELL — only quantity decreases
        holdingRepository.save(holding);

        Transaction tx = new Transaction(portfolio, symbol, Transaction.Type.SELL, sellQty, req.price());
        tx = transactionRepository.save(tx);

        log.info("SELL portfolio={} symbol={} qty={} price={}", portfolioId, symbol, sellQty, req.price());
        return toTransactionResponse(tx);
    }

    @Transactional(readOnly = true)
    public List<HoldingResponse> getHoldings(UUID portfolioId) {
        findPortfolioOrThrow(portfolioId); // ensure portfolio exists
        return holdingRepository.findByPortfolioId(portfolioId)
                .stream()
                .map(this::toHoldingResponse)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public Portfolio findPortfolioOrThrow(UUID id) {
        return portfolioRepository.findById(id)
                .orElseThrow(() -> new PortfolioNotFoundException(id));
    }

    private PortfolioResponse toResponse(Portfolio portfolio, List<Holding> holdings) {
        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getClientName(),
                portfolio.getRiskProfile(),
                portfolio.getCashBalance().setScale(SCALE, RoundingMode.HALF_UP),
                holdings.stream().map(this::toHoldingResponse).toList(),
                portfolio.getCreatedAt()
        );
    }

    private HoldingResponse toHoldingResponse(Holding h) {
        BigDecimal totalCost = h.getQuantity().multiply(h.getAvgCostBasis(), MC)
                .setScale(SCALE, RoundingMode.HALF_UP);
        return new HoldingResponse(
                h.getId(),
                h.getSymbol(),
                h.getQuantity(),
                h.getAvgCostBasis(),
                totalCost
        );
    }

    private TransactionResponse toTransactionResponse(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getSymbol(),
                tx.getType().name(),
                tx.getQuantity(),
                tx.getPrice(),
                tx.getTotalValue(),
                tx.getTransactedAt()
        );
    }
}
