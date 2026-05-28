package com.byld.portfolio.repository;

import com.byld.portfolio.entity.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, UUID> {

    List<Dividend> findByPortfolioIdOrderByRecordDateDesc(UUID portfolioId);

    @Query("""
        SELECT d.symbol, SUM(d.totalPayout)
        FROM Dividend d
        WHERE d.portfolio.id = :portfolioId
        GROUP BY d.symbol
        ORDER BY d.symbol
    """)
    List<Object[]> sumPayoutBySymbol(@Param("portfolioId") UUID portfolioId);
}
