package com.udea.digitalbank.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI digitalBankOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Digital Bank API")
                        .version("1.0.0")
                        .description("API para la gestión del banco digital"));
    }
}