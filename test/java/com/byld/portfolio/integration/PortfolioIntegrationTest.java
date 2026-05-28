package com.byld.portfolio.integration;

import com.byld.portfolio.dto.Dtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class PortfolioIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("Full flow: create portfolio → BUY → SELL → dividend credits cash")
    void fullPortfolioFlow() throws Exception {

        // 1. Create portfolio
        String createBody = objectMapper.writeValueAsString(
                new CreatePortfolioRequest("Arjun Mehta", "AGGRESSIVE"));

        String portfolioJson = mockMvc.perform(post("/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.clientName").value("Arjun Mehta"))
                .andExpect(jsonPath("$.riskProfile").value("AGGRESSIVE"))
                .andExpect(jsonPath("$.cashBalance").value(0.0))
                .andReturn().getResponse().getContentAsString();

        String portfolioId = objectMapper.readTree(portfolioJson).get("id").asText();

        // 2. BUY 10 RELIANCE @ 2900
        String buyBody = objectMapper.writeValueAsString(
                new BuyRequest("RELIANCE", new BigDecimal("10"), new BigDecimal("2900.0000")));

        mockMvc.perform(post("/v1/portfolios/" + portfolioId + "/transactions/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("BUY"))
                .andExpect(jsonPath("$.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.totalValue").value(29000.0));

        // 3. BUY 5 more RELIANCE @ 3200 → avgCost = (10*2900 + 5*3200)/15 = 3000
        String buyBody2 = objectMapper.writeValueAsString(
                new BuyRequest("RELIANCE", new BigDecimal("5"), new BigDecimal("3200.0000")));

        mockMvc.perform(post("/v1/portfolios/" + portfolioId + "/transactions/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody2))
                .andExpect(status().isCreated());

        // 4. Verify holdings — avgCostBasis should be 3000
        mockMvc.perform(get("/v1/portfolios/" + portfolioId + "/holdings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE"))
                .andExpect(jsonPath("$[0].quantity").value(15.0))
                .andExpect(jsonPath("$[0].avgCostBasis").value(3000.0))
                .andExpect(jsonPath("$[0].totalCost").value(45000.0));

        // 5. SELL 5 RELIANCE @ 3500 — costBasis should remain 3000
        String sellBody = objectMapper.writeValueAsString(
                new SellRequest("RELIANCE", new BigDecimal("5"), new BigDecimal("3500.0000")));

        mockMvc.perform(post("/v1/portfolios/" + portfolioId + "/transactions/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sellBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SELL"));

        // 6. Holding quantity should now be 10
        mockMvc.perform(get("/v1/portfolios/" + portfolioId + "/holdings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantity").value(10.0))
                .andExpect(jsonPath("$[0].avgCostBasis").value(3000.0));

        // 7. Record a dividend: ₹25/share on 10 shares = ₹250 payout
        String divBody = objectMapper.writeValueAsString(
                new RecordDividendRequest("RELIANCE", new BigDecimal("25.0000"), LocalDate.of(2024, 3, 31)));

        mockMvc.perform(post("/v1/portfolios/" + portfolioId + "/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(divBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sharesAtRecord").value(10.0))
                .andExpect(jsonPath("$.totalPayout").value(250.0));

        // 8. Cash balance should now be 250
        mockMvc.perform(get("/v1/portfolios/" + portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(250.0));

        // 9. List dividends
        mockMvc.perform(get("/v1/portfolios/" + portfolioId + "/dividends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dividends", hasSize(1)))
                .andExpect(jsonPath("$.totals[0].symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.totals[0].totalPayout").value(250.0));
    }

    @Test
    @DisplayName("SELL more than held returns 409 Conflict")
    void sellMoreThanHeldReturns409() throws Exception {
        String createBody = objectMapper.writeValueAsString(
                new CreatePortfolioRequest("Test Client", "MODERATE"));
        String portfolioJson = mockMvc.perform(post("/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = objectMapper.readTree(portfolioJson).get("id").asText();

        String buyBody = objectMapper.writeValueAsString(
                new BuyRequest("INFY", new BigDecimal("5"), new BigDecimal("1500.0000")));
        mockMvc.perform(post("/v1/portfolios/" + portfolioId + "/transactions/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buyBody))
                .andExpect(status().isCreated());

        String sellBody = objectMapper.writeValueAsString(
                new SellRequest("INFY", new BigDecimal("10"), new BigDecimal("1600.0000")));
        mockMvc.perform(post("/v1/portfolios/" + portfolioId + "/transactions/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sellBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").containsString("INFY"));
    }

    @Test
    @DisplayName("GET unknown portfolio returns 404")
    void unknownPortfolioReturns404() throws Exception {
        mockMvc.perform(get("/v1/portfolios/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("BUY with missing fields returns 400")
    void buyWithMissingFieldsReturns400() throws Exception {
        String createBody = objectMapper.writeValueAsString(
                new CreatePortfolioRequest("Client", "MODERATE"));
        String portfolioJson = mockMvc.perform(post("/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = objectMapper.readTree(portfolioJson).get("id").asText();

        // Missing price
        String badBuy = "{\"symbol\":\"RELIANCE\",\"quantity\":10}";
        mockMvc.perform(post("/v1/portfolios/" + portfolioId + "/transactions/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBuy))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Dividend with no holding records event with zero payout")
    void dividendWithNoHoldingZeroPayout() throws Exception {
        String createBody = objectMapper.writeValueAsString(
                new CreatePortfolioRequest("New Client", "CONSERVATIVE"));
        String portfolioJson = mockMvc.perform(post("/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String portfolioId = objectMapper.readTree(portfolioJson).get("id").asText();

        String divBody = objectMapper.writeValueAsString(
                new RecordDividendRequest("TCS", new BigDecimal("50.0000"), LocalDate.of(2024, 12, 31)));

        mockMvc.perform(post("/v1/portfolios/" + portfolioId + "/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(divBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sharesAtRecord").value(0.0))
                .andExpect(jsonPath("$.totalPayout").value(0.0));
    }
}
