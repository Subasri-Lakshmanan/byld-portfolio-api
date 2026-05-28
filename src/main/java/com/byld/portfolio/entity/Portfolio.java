package com.byld.portfolio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "portfolios")
@Getter
@Setter
@NoArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "risk_profile", nullable = false)
    private String riskProfile;

    @Column(name = "cash_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashBalance = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Holding> holdings = new ArrayList<>();

    public Portfolio(String clientName, String riskProfile) {
        this.clientName = clientName;
        this.riskProfile = riskProfile;
        this.cashBalance = BigDecimal.ZERO;
        this.createdAt = Instant.now();
    }
}
