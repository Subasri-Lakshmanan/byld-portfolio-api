package com.byld.portfolio.service;

import com.byld.portfolio.dto.Dtos.*;
import com.byld.portfolio.entity.Holding;
import com.byld.portfolio.entity.Portfolio;
import com.byld.portfolio.entity.Transaction;
import com.byld.portfolio.exception.Exceptions.*;
import com.byld.portfolio.repository.HoldingRepository;
import com.byld.portfolio.repository.PortfolioRepository;
import com.byld.portfolio.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private HoldingRepository holdingRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks private PortfolioService service;

    private Portfolio portfolio;
    private final UUID portfolioId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio("Test Client", "MODERATE");
        // reflection-set id for mocking
        try {
            var field = Portfolio.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(portfolio, portfolioId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(holdingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Weighted-Average Cost Basis Tests ─────────────────────────────────────

    @Test
    @DisplayName("First BUY sets avgCostBasis to buy price")
    void firstBuySetsAvgCostBasis() {
        when(holdingRepository.findByPortfolioIdAndSymbol(portfolioId, "RELIANCE"))
                .thenReturn(Optional.empty());

        BuyRequest req = new BuyRequest("RELIANCE", new BigDecimal("10"), new BigDecimal("2900.0000"));
        service.buy(portfolioId, req);

        verify(holdingRepository).save(argThat(h -> {
            assertThat(h.getQuantity()).isEqualByComparingTo("10.0000");
            assertThat(h.getAvgCostBasis()).isEqualByComparingTo("2900.0000");
            return true;
        }));
    }

    @Test
    @DisplayName("Second BUY at higher price produces correct weighted average")
    void secondBuyRecalculatesWeightedAvg() {
        // Existing: 10 shares @ 2900
        Holding existing = makeHolding("RELIANCE", "10", "2900.0000");
        when(holdingRepository.findByPortfolioIdAndSymbol(portfolioId, "RELIANCE"))
                .thenReturn(Optional.of(existing));

        // Buy 5 more @ 3200
        BuyRequest req = new BuyRequest("RELIANCE", new BigDecimal("5"), new BigDecimal("3200.0000"));
        service.buy(portfolioId, req);

        // Expected: (10*2900 + 5*3200) / 15 = (29000 + 16000) / 15 = 45000/15 = 3000
        verify(holdingRepository).save(argThat(h -> {
            assertThat(h.getQuantity()).isEqualByComparingTo("15.0000");
            assertThat(h.getAvgCostBasis()).isEqualByComparingTo("3000.0000");
            return true;
        }));
    }

    @Test
    @DisplayName("SELL does not change avgCostBasis, only reduces quantity")
    void sellReducesQuantityNotAvg() {
        Holding existing = makeHolding("INFY", "20", "1500.0000");
        when(holdingRepository.findByPortfolioIdAndSymbol(portfolioId, "INFY"))
                .thenReturn(Optional.of(existing));

        SellRequest req = new SellRequest("INFY", new BigDecimal("5"), new BigDecimal("1700.0000"));
        service.sell(portfolioId, req);

        verify(holdingRepository).save(argThat(h -> {
            assertThat(h.getQuantity()).isEqualByComparingTo("15.0000");
            assertThat(h.getAvgCostBasis()).isEqualByComparingTo("1500.0000"); // unchanged
            return true;
        }));
    }

    @Test
    @DisplayName("SELL entire holding reduces quantity to zero")
    void sellAllReducesToZero() {
        Holding existing = makeHolding("TCS", "10", "3500.0000");
        when(holdingRepository.findByPortfolioIdAndSymbol(portfolioId, "TCS"))
                .thenReturn(Optional.of(existing));

        SellRequest req = new SellRequest("TCS", new BigDecimal("10"), new BigDecimal("4000.0000"));
        service.sell(portfolioId, req);

        verify(holdingRepository).save(argThat(h -> {
            assertThat(h.getQuantity()).isEqualByComparingTo("0.0000");
            return true;
        }));
    }

    @Test
    @DisplayName("SELL more than held throws 409 InsufficientHoldingException")
    void sellMoreThanHeldThrows409() {
        Holding existing = makeHolding("WIPRO", "5", "400.0000");
        when(holdingRepository.findByPortfolioIdAndSymbol(portfolioId, "WIPRO"))
                .thenReturn(Optional.of(existing));

        SellRequest req = new SellRequest("WIPRO", new BigDecimal("10"), new BigDecimal("450.0000"));

        assertThatThrownBy(() -> service.sell(portfolioId, req))
                .isInstanceOf(InsufficientHoldingException.class)
                .hasMessageContaining("WIPRO");
    }

    @Test
    @DisplayName("SELL non-existent symbol throws HoldingNotFoundException")
    void sellNonExistentSymbolThrowsNotFound() {
        when(holdingRepository.findByPortfolioIdAndSymbol(portfolioId, "UNKNOWN"))
                .thenReturn(Optional.empty());

        SellRequest req = new SellRequest("UNKNOWN", new BigDecimal("5"), new BigDecimal("100.0000"));

        assertThatThrownBy(() -> service.sell(portfolioId, req))
                .isInstanceOf(HoldingNotFoundException.class);
    }

    @Test
    @DisplayName("GET unknown portfolio throws PortfolioNotFoundException")
    void unknownPortfolioThrows404() {
        UUID unknown = UUID.randomUUID();
        when(portfolioRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPortfolio(unknown))
                .isInstanceOf(PortfolioNotFoundException.class);
    }

    @Test
    @DisplayName("Weighted avg is correct after multiple BUYs with fractional quantities")
    void weightedAvgWithFractionalQuantities() {
        // 100 shares @ 150.50 first
        Holding existing = makeHolding("BAJFINANCE", "100", "150.5000");
        when(holdingRepository.findByPortfolioIdAndSymbol(portfolioId, "BAJFINANCE"))
                .thenReturn(Optional.of(existing));

        // Buy 50 more @ 160.25
        BuyRequest req = new BuyRequest("BAJFINANCE", new BigDecimal("50"), new BigDecimal("160.2500"));
        service.buy(portfolioId, req);

        // Expected: (100*150.5 + 50*160.25) / 150 = (15050 + 8012.5) / 150 = 23062.5 / 150 = 153.75
        verify(holdingRepository).save(argThat(h -> {
            assertThat(h.getQuantity()).isEqualByComparingTo("150.0000");
            assertThat(h.getAvgCostBasis()).isEqualByComparingTo("153.7500");
            return true;
        }));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Holding makeHolding(String symbol, String qty, String avgCost) {
        Holding h = new Holding(portfolio, symbol);
        h.setQuantity(new BigDecimal(qty));
        h.setAvgCostBasis(new BigDecimal(avgCost));
        return h;
    }
}
