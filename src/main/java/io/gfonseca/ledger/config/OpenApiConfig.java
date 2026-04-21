package io.gfonseca.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI ledgerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ledger API")
                        .version("v1")
                        .description("Double-entry ledger service. Records money movements as balanced transactions "
                                + "composed of postings that sum to zero per currency. Balances are derived "
                                + "from the posting log and never stored."));
    }
}
