package com.einvoice.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** SpringDoc configuration: platform-wide OpenAPI metadata + integration-gateway group (FR-022). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI platformOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("E-Invoice Platform API")
                        .version("0.1.0")
                        .description("Multi-authority e-invoicing compliance platform"));
    }

    @Bean
    public GroupedOpenApi integrationGateway() {
        return GroupedOpenApi.builder()
                .group("integration-gateway")
                .pathsToMatch("/api/integration/**")
                .build();
    }
}
