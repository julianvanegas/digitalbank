package com.udea.digitalbank.support.integration;

import com.jayway.jsonpath.JsonPath;
import com.udea.digitalbank.shared.utils.EmailService;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cliente de la API para las pruebas de flujos: habla con la aplicación por HTTP (MockMvc), igual que lo haría un
 * cliente real, y lee los códigos de verificación de los correos que la aplicación intenta enviar (el
 * {@link EmailService} es un mock). Los únicos datos que no entran por la API son los del administrador inicial,
 * porque la API no permite crear el primero.
 */
public final class BankApi {

    /** Contraseña válida según la política (mayúscula, minúscula, dígito, símbolo, 8 a 16 caracteres). */
    public static final String PASSWORD = "Clave#2026ok";
    public static final short CEDULA = 1;

    private static final Pattern CODE = Pattern.compile("Tu código es: (\\d{6})");

    private final MockMvc mockMvc;
    private final EmailService emailService;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final MutableClock clock;
    private int sequence;

    BankApi(MockMvc mockMvc, EmailService emailService, JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
            MutableClock clock) {
        this.mockMvc = mockMvc;
        this.emailService = emailService;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    // ============ PETICIONES ============

    public record Response(int status, String body) {

        public <T> T json(String path) {
            return JsonPath.read(body, path);
        }

        public String message() {
            return json("$.message");
        }

        public Response expect(int expected) {
            assertThat(status).as("HTTP %s con cuerpo %s", status, body).isEqualTo(expected);
            return this;
        }
    }

    public Response post(String path, String token, String json) {
        return perform(withBody(MockMvcRequestBuilders.post(path), json), token);
    }

    public Response patch(String path, String token, String json) {
        return perform(withBody(MockMvcRequestBuilders.patch(path), json), token);
    }

    public Response get(String path, String token) {
        return perform(MockMvcRequestBuilders.get(path), token);
    }

    private static MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, String json) {
        return json == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private Response perform(MockHttpServletRequestBuilder request, String token) {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        try {
            MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
            return new Response(response.getStatus(), response.getContentAsString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("La petición falló sin respuesta HTTP", e);
        }
    }

    // ============ CORREOS ============

    public record Email(String to, String subject, String text) {
    }

    /** Correos que la aplicación intentó enviar a esa dirección, en orden. */
    public List<Email> emailsTo(String to) {
        return Mockito.mockingDetails(emailService).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("send"))
                .map(Invocation::getArguments)
                .map(args -> new Email((String) args[0], (String) args[1], (String) args[2]))
                .filter(email -> email.to().equalsIgnoreCase(to))
                .toList();
    }

    /** El código del último correo con código enviado a esa dirección. */
    public String lastCode(String to) {
        List<String> codes = emailsTo(to).stream()
                .map(email -> CODE.matcher(email.text()))
                .filter(Matcher::find)
                .map(m -> m.group(1))
                .toList();
        assertThat(codes).as("códigos enviados a %s", to).isNotEmpty();
        return codes.getLast();
    }

    /** Un código de 6 dígitos distinto del vigente, para probar el código equivocado. */
    public static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    // ============ IDENTIDAD ============

    /** Crea el administrador directamente en la base (la API no puede crear el primero) e inicia su sesión. */
    public String admin(String email) {
        UUID id = UUID.randomUUID();
        LocalDateTime now = clock.now();
        jdbc.update("""
                insert into users (id, email, password_hash, password_changed_at, role_id, status_id,
                                   failed_attempts, status_changed_at, created_at)
                values (?, ?, ?, ?, 2, 1, 0, ?, ?)""",
                id, email, passwordEncoder.encode(PASSWORD), now, now, now);
        jdbc.update("""
                insert into employees (id, user_id, role_id, first_names, last_names, created_at, updated_at)
                values (?, ?, 2, 'Admin', 'Pruebas', ?, ?)""", UUID.randomUUID(), id, now, now);
        return login(email, PASSWORD);
    }

    public record Customer(UUID id, UUID userId, String email, String documentNumber, String token) {
    }

    /**
     * Cliente listo para operar, por el mismo camino que uno real: se registra, confirma su correo y define su
     * contraseña, un administrador lo activa y por último inicia sesión con segundo factor.
     */
    public Customer activeCustomer(String email, String adminToken) {
        String document = String.valueOf(1_000_000_000L + (++sequence));
        Response created = register(email, document).expect(201);
        verifyEmail(email, lastCode(email)).expect(204);
        changeStatus(adminToken, document, "ACTIVE").expect(204);
        return new Customer(UUID.fromString(created.json("$.id")), UUID.fromString(created.json("$.userId")),
                email, document, login(email, PASSWORD));
    }

    public Response register(String email, String documentNumber) {
        return post("/api/v1/customers", null, """
                {"firstNames": "Ana María", "lastNames": "Pérez Gómez", "documentTypeId": %d,
                 "documentNumber": "%s", "phone": "+573001234567", "birthDate": "%s", "email": "%s"}"""
                .formatted(CEDULA, documentNumber, LocalDate.now().minusYears(30), email));
    }

    public Response verifyEmail(String email, String code) {
        return post("/api/v1/auth/verify-email", null, """
                {"email": "%s", "code": "%s", "password": "%s", "confirmPassword": "%s"}"""
                .formatted(email, code, PASSWORD, PASSWORD));
    }

    public Response changeStatus(String adminToken, String documentNumber, String status) {
        return patch("/api/v1/customers/status", adminToken, """
                {"documentTypeId": %d, "documentNumber": "%s", "status": "%s"}"""
                .formatted(CEDULA, documentNumber, status));
    }

    /** Inicia sesión completa (contraseña y segundo factor) y devuelve el token. */
    public String login(String email, String password) {
        Response login = startLogin(email, password).expect(200);
        return verifyTwoFactor(login.json("$.challengeId"), lastCode(email)).expect(200).json("$.token");
    }

    public Response startLogin(String email, String password) {
        return post("/api/v1/auth/login", null, """
                {"email": "%s", "password": "%s"}""".formatted(email, password));
    }

    public Response verifyTwoFactor(String challengeId, String code) {
        return post("/api/v1/auth/verify-2fa", null, """
                {"challengeId": "%s", "code": "%s"}""".formatted(challengeId, code));
    }

    // ============ CUENTAS Y MOVIMIENTOS ============

    /** Abre una cuenta de ahorros al cliente dueño del token y devuelve su número. */
    public String openAccount(String token) {
        return post("/api/v1/accounts", token, null).expect(201).json("$.accountNumber");
    }

    public BigDecimal balance(String token, String accountNumber) {
        Object balance = get("/api/v1/accounts/" + accountNumber + "/balance", token).expect(200).json("$.balance");
        return new BigDecimal(balance.toString());
    }

    public Response deposit(String adminToken, String accountNumber, String amount) {
        return post("/api/v1/transactions/deposits", adminToken, """
                {"receiverAccountNumber": "%s", "amount": %s}""".formatted(accountNumber, amount));
    }

    public Response transfer(String token, String from, String to, String amount) {
        return post("/api/v1/transactions/transfers", token, """
                {"senderAccountNumber": "%s", "receiverAccountNumber": "%s", "amount": %s}"""
                .formatted(from, to, amount));
    }

    public Response withdraw(String token, String accountNumber, String amount) {
        return post("/api/v1/transactions/withdrawals", token, """
                {"accountNumber": "%s", "amount": %s}""".formatted(accountNumber, amount));
    }

    public Response confirm(String token, String reference, String code) {
        return post("/api/v1/transactions/" + reference + "/confirm", token, """
                {"code": "%s"}""".formatted(code));
    }

    public Response transaction(String token, String reference) {
        return get("/api/v1/transactions/" + reference, token);
    }
}
