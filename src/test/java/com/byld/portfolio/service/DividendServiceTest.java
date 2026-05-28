package com.byld.portfolio.service;

import com.byld.portfolio.dto.Dtos.*;
import com.byld.portfolio.entity.Dividend;
import com.byld.portfolio.entity.Holding;
import com.byld.portfolio.entity.Portfolio;
import com.byld.portfolio.repository.DividendRepository;
import com.byld.portfolio.repository.HoldingRepository;
import com.byld.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DividendServiceTest {

    @Mock private DividendRepository dividendRepository;
    @Mock private HoldingRepository holdingRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioService portfolioService;

    @InjectMocks private DividendService dividendService;

    private Portfolio portfolio;
    private final UUID portfolioId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio("Test Client", "MODERATE");
        try {
            var field = Portfolio.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(portfolio, portfolioId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(portfolioService.findPortfolioOrThrow(portfolioId)).thenReturn(portfolio);
        when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dividendRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Dividend credits cash balance correctly when holding exists")
    void dividendCreditsCashCorrectly() {
        Holding holding = new Holding(portfolio, "RELIANCE");
        holding.setQuantity(new BigDecimal("100.0000"));
        holding.setAvgCostBasis(new BigDecimal("2900.0000"));

        when(holdingRepository.findByPortfolioIdAndSymbol(portfolioId, "RELIANCE"))
                .thenReturn(Optional.of(holding));

        RecordDividendRequest req = new RecordDividendRequest(
                "RELIANCE",
                new BigDecimal("25.0000"),   // ₹25 per share
                LocalDate.of(2024, 3, 31)
        );

        DividendResponse response = dividendService.recordDividend(portfolioId, req);

        // 100 shares * ₹25 = ₹2500
        assertThat(response.sharesAtRecord()).isEqualByComparingTo("100.0000");
        assertThat(response.totalPayout()).isEqualByComparingTo("2500.0000");
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("2500.0000");
        assertThat(response.symbol()).isEqualTo("RELIANCE");
        assertThat(response.perShareAmount()).isEqualByComparingTo("25.0000");

        verify(portfolioRepository).save(portfolio);
        verify(dividendRepository).save(any(Dividend.class));
    }

    @Test
    @DisplayName("Dividend with fractional shares computes payout precisely")
    void dividendWithFractionalSharesIsPrecise() {
        Holding holding = new Holding(portfolio, "INFY");
        holding.setQuantity(new BigDecimal("33.3333"));
        holding.setAvgCostBasis(new BigDecimal("1500.0000"));

        when(holdingRepository.findByPortfolioIdAndSymbol(portfolioId, "INFY"))
                .thenReturn(Optional.of(holding));

        RecordDividendRequest req = new RecordDividendRequest(
                "INFY",
                new BigDecimal("15.0000"),
                LocalDate.of(2024, 6, 30)
        );

        DividendResponse response = dividendService.recordDividend(portfolioId, req);

        // 33.3333 * 15 = 499.9995 → rounded to 4dp = 499.9995
        assertThat(response.totalPayout()).isEqualByComparingTo("499.9995");
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("499.9995");
    }

    @Test
    @DisplayName("Dividend with no holding results in zero payout but still records event")
    void dividendWithNoHoldingResultsInZeroPayout() {
        when(holdingRepository.findByPortfolioIdAndSymbol(portfolioId, "TCS"))
                .thenReturn(Optional.empty());

        RecordDividendRequest req = new RecordDividendRequest(
                "TCS",
                new BigDecimal("30.0000"),
                LocalDate.of(2024, 9, 30)
        );

        DividendResponse response = dividendService.recordDividend(portfolioId, req);

        assertThat(response.sharesAtRecord()).isEqualByComparingTo("0.0000");
        assertThat(response.totalPayout()).isEqualByComparingTo("0.0000");
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("0.0000");
        // Event should still be persisted for audit
        verify(dividendRepository).save(any(Dividend.class));
    }

    @Test
    @DisplayName("Multiple dividends accumulate cash balance correctly")
    void multipleDividendsAccumulateCash() {
        Holding reliance = new Holding(portfolio, "RELIANCE");
        reliance.setQuantity(new BigDecimal("50.0000"));
        reliance.setAvgCostBasis(new BigDecimal("2900.0000"));

        when(holdingRepository.findByPortfolioIdAndSymbol(portfolioId, "RELIANCE"))
                .thenReturn(Optional.of(reliance));

        // First dividend: 50 * 10 = 500
        dividendService.recordDividend(portfolioId,
                new RecordDividendRequest("RELIANCE", new BigDecimal("10.0000"), LocalDate.of(2024, 3, 31)));

        // Second dividend: 50 * 20 = 1000
        dividendService.recordDividend(portfolioId,
                new RecordDividendRequest("RELIANCE", new BigDecimal("20.0000"), LocalDate.of(2024, 9, 30)));

        // Total cash: 500 + 1000 = 1500
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("1500.0000");
    }

    @Test
    @DisplayName("getDividends returns summary grouped by symbol with correct totals")
    void getDividendsReturnsSummaryGrouped() {
        Dividend d1 = new Dividend(portfolio, "RELIANCE", new BigDecimal("25.0000"),
                LocalDate.of(2024, 3, 31), new BigDecimal("100.0000"), new BigDecimal("2500.0000"));
        Dividend d2 = new Dividend(portfolio, "INFY", new BigDecimal("15.0000"),
                LocalDate.of(2024, 6, 30), new BigDecimal("50.0000"), new BigDecimal("750.0000"));
        Dividend d3 = new Dividend(portfolio, "RELIANCE", new BigDecimal("30.0000"),
                LocalDate.of(2024, 9, 30), new BigDecimal("100.0000"), new BigDecimal("3000.0000"));

        when(dividendRepository.findByPortfolioIdOrderByRecordDateDesc(portfolioId))
                .thenReturn(List.of(d3, d2, d1));

        DividendSummaryResponse summary = dividendService.getDividends(portfolioId);

        assertThat(summary.dividends()).hasSize(3);
        assertThat(summary.totals()).hasSize(2);

        // INFY: 750
        var infyTotal = summary.totals().stream()
                .filter(t -> t.symbol().equals("INFY")).findFirst().orElseThrow();
        assertThat(infyTotal.totalPayout()).isEqualByComparingTo("750.0000");

        // RELIANCE: 2500 + 3000 = 5500
        var relianceTotal = summary.totals().stream()
                .filter(t -> t.symbol().equals("RELIANCE")).findFirst().orElseThrow();
        assertThat(relianceTotal.totalPayout()).isEqualByComparingTo("5500.0000");
    }
}
