package com.udea.digitalbank.shared.config;

/**
 * Datos de ejemplo del contrato OpenAPI. Es un solo universo ficticio y coherente: la misma persona, las mismas
 * cuentas y la misma referencia aparecen en todos los endpoints, de modo que lo que devuelve una operación se puede
 * usar en la siguiente desde Swagger UI sin editar nada.
 *
 * <p>Los valores son constantes de compilación porque se usan como {@code @Schema(example = ...)}. Todos cumplen las
 * validaciones de los DTO a los que se aplican.
 */
public final class OpenApiExamples {

    // ---- Cliente
    public static final String FIRST_NAMES = "Ana María";
    public static final String LAST_NAMES = "Gómez Ruiz";
    public static final String EMAIL = "ana.gomez@example.com";
    public static final String PHONE = "+573001112233";
    public static final String DOCUMENT_TYPE_ID = "1";
    public static final String DOCUMENT_NAME = "Cédula de ciudadanía";
    public static final String DOCUMENT_NUMBER = "1000000001";
    public static final String BIRTH_DATE = "1990-05-14";
    public static final String PASSWORD = "Segura#2026";
    public static final String TOKEN =
            "eyJhbGciOiJIUzI1NiJ9."
                    + "eyJzdWIiOiJiNGQ3ZjFjOC0yYTk1LTRlMDMtOGM2Yi01ZDFlOWYzYTdiMjAiLCJyb2xlIjoiQ1VTVE9NRVIifQ."
                    + "ZmlybWEtZGUtZWplbXBsbw";

    // ---- Empleado
    public static final String EMPLOYEE_FIRST_NAMES = "Carlos Andrés";
    public static final String EMPLOYEE_LAST_NAMES = "Ríos Mejía";
    public static final String EMPLOYEE_EMAIL = "carlos.rios@example.com";

    // ---- Identificadores (UUID fijos)
    public static final String CUSTOMER_ID = "3f2b8a10-5c4d-4e6f-9a1b-2c3d4e5f6a7b";
    public static final String EMPLOYEE_ID = "7a9c1e24-8b3f-4d50-a6e2-1f0b9c8d7e6a";
    public static final String USER_ID = "b4d7f1c8-2a95-4e03-8c6b-5d1e9f3a7b20";
    public static final String EMPLOYEE_USER_ID = "e5a8c2d9-3b06-4f14-9d7c-6e2f0a4b8c31";
    public static final String ACCOUNT_ID = "c81e5a37-6d0b-4f92-b3a4-7e2d8c9f1a56";
    public static final String CHALLENGE_ID = "d92f6b48-7e1c-4a03-84b5-9f3e0d2a6c17";

    // ---- Cuentas y dinero
    public static final String ACCOUNT_NUMBER = "100000000001";
    public static final String RECEIVER_ACCOUNT_NUMBER = "200000000002";
    public static final String ACCOUNT_KEY = "@AN1001";
    public static final String AMOUNT = "150000.00";
    public static final String BALANCE = "850000.00";

    // ---- Segundo factor y transacciones
    public static final String CODE = "123456";
    public static final String REFERENCE = "TRF-20261017-7Xk2Pq9RmZ4tNvB8cHdWeA";
    public static final String REASON = "Actividad sospechosa reportada por el titular";
    public static final String UNLOCK_REASON = "Revisión completada sin actividad irregular";
    public static final String REMAINING_RESENDS = "1";
    public static final String RETRY_AFTER_SECONDS = "60";

    // ---- Fechas
    public static final String TIMESTAMP = "2026-10-17T10:15:30";
    public static final String EXPIRES_AT = "2026-10-17T10:18:30";
    public static final String CLOSED_AT = "2026-12-01T09:00:00";

    private OpenApiExamples() {
    }
}
