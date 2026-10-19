package com.udea.digitalbank.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Datos generales del contrato OpenAPI: información de la API, servidor, esquema de seguridad y la lista de tags, que
 * fija el orden en que Swagger UI muestra los grupos. Lo que depende de cada ruta (candados y errores
 * comunes) lo agrega {@link OpenApiContractCustomizer}.
 */
@Configuration
public class OpenAPIConfig {

    static final String BEARER = "bearerAuth";

    /** Nombres de los tags: los controllers los usan en {@code @Tag(name = ...)} para agrupar sus operaciones. */
    public static final String TAG_AUTH = "Autenticación";
    public static final String TAG_CUSTOMERS = "Clientes";
    public static final String TAG_EMPLOYEES = "Empleados";
    public static final String TAG_ACCOUNTS = "Cuentas";
    public static final String TAG_TRANSACTIONS = "Transacciones";

    @Bean
    public OpenAPI digitalBankOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Digital Bank API")
                        .version("1.0.0")
                        .description("API REST del banco digital: autenticación con segundo factor, clientes, "
                                + "empleados, cuentas y transferencias."))
                // Ruta relativa: Swagger UI llama al mismo servidor desde el que se sirve, sea cual sea el entorno
                .servers(List.of(new Server().url("/").description("Servidor actual")))
                .tags(List.of(
                        new Tag().name(TAG_AUTH).description(
                                "Inicio de sesión con segundo factor, confirmación de correo y recuperación de "
                                        + "contraseña."),
                        new Tag().name(TAG_CUSTOMERS).description("Registro, perfil y administración de clientes."),
                        new Tag().name(TAG_EMPLOYEES).description("Alta, perfil y administración de empleados."),
                        new Tag().name(TAG_ACCOUNTS).description("Apertura, consulta y ciclo de vida de las cuentas."),
                        new Tag().name(TAG_TRANSACTIONS).description(
                                "Transferencias entre cuentas con segundo factor y consulta de comprobantes.")))
                // Permite pegar el JWT en "Authorize" de Swagger UI para probar los endpoints protegidos
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token JWT que devuelve verify-2fa tras iniciar sesión con login. "
                                + "Se envía en la cabecera Authorization como: Bearer <token>.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
