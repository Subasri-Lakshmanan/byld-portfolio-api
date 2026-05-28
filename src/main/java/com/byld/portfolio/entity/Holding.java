package com.byld.portfolio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "holdings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "symbol"}))
@Getter
@Setter
@NoArgsConstructor
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity = BigDecimal.ZERO;

    /**
     * Weighted-average cost basis per share.
     * Recalculated on every BUY: newAvg = (oldQty * oldAvg + buyQty * buyPrice) / (oldQty + buyQty)
     * On SELL: cost basis stays the same (only quantity decreases).
     */
    @Column(name = "avg_cost_basis", nullable = false, precision = 19, scale = 4)
    private BigDecimal avgCostBasis = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Holding(Portfolio portfolio, String symbol) {
        this.portfolio = portfolio;
        this.symbol = symbol;
        this.quantity = BigDecimal.ZERO;
        this.avgCostBasis = BigDecimal.ZERO;
        this.createdAt = Instant.now();
    }
}
