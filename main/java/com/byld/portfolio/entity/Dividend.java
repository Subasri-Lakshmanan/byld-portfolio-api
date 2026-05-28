package com.byld.portfolio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "dividends")
@Getter
@Setter
@NoArgsConstructor
public class Dividend {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "per_share_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal perShareAmount;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    /**
     * Quantity of shares held in this portfolio for this symbol on recordDate.
     * Computed at recording time by looking at holdings.
     */
    @Column(name = "shares_at_record", nullable = false, precision = 19, scale = 4)
    private BigDecimal sharesAtRecord = BigDecimal.ZERO;

    /**
     * Total dividend payout = sharesAtRecord * perShareAmount.
     * Credited to portfolio cash balance at recording time.
     */
    @Column(name = "total_payout", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPayout = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Dividend(Portfolio portfolio, String symbol,
                    BigDecimal perShareAmount, LocalDate recordDate,
                    BigDecimal sharesAtRecord, BigDecimal totalPayout) {
        this.portfolio = portfolio;
        this.symbol = symbol;
        this.perShareAmount = perShareAmount;
        this.recordDate = recordDate;
        this.sharesAtRecord = sharesAtRecord;
        this.totalPayout = totalPayout;
        this.createdAt = Instant.now();
    }
}
