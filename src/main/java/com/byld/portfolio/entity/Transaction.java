package com.byld.portfolio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    public enum Type { BUY, SELL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "total_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalValue;

    @Column(name = "transacted_at", nullable = false, updatable = false)
    private Instant transactedAt = Instant.now();

    public Transaction(Portfolio portfolio, String symbol, Type type,
                       BigDecimal quantity, BigDecimal price) {
        this.portfolio = portfolio;
        this.symbol = symbol;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.totalValue = quantity.multiply(price);
        this.transactedAt = Instant.now();
    }
}
