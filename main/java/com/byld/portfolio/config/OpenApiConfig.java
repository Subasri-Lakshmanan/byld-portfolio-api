package com.byld.portfolio.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI portfolioOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BYLD Wealth — Portfolio API")
                        .description("""
                                Portfolio management API for BYLD Wealth.
                                Variant B: Dividend tracking.
                                
                                Features:
                                - Create portfolios and record BUY/SELL transactions
                                - Weighted-average cost basis calculation (BigDecimal, NUMERIC(19,4))
                                - Dividend recording with automatic cash balance credit
                                - Dividend history grouped by symbol with totals
                                
                                Auth: Not required for this assignment.
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("BYLD Wealth Engineering"))
                );
    }
}
