package com.udea.digitalbank.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI digitalBankOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Digital Bank API")
                        .version("1.0.0")
                        .description("API para la gestión del banco digital"))
                // Permite pegar el JWT en "Authorize" de Swagger UI para probar los endpoints protegidos
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
