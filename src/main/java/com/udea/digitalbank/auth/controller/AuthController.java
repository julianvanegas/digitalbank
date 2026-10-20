package com.udea.digitalbank.auth.controller;

import com.udea.digitalbank.auth.dto.AuthResponse;
import com.udea.digitalbank.auth.dto.EmailRequest;
import com.udea.digitalbank.auth.dto.LoginRequest;
import com.udea.digitalbank.auth.dto.LoginResponse;
import com.udea.digitalbank.auth.dto.PasswordResetRequest;
import com.udea.digitalbank.auth.dto.ResendTwoFactorRequest;
import com.udea.digitalbank.auth.dto.ResendTwoFactorResponse;
import com.udea.digitalbank.auth.dto.VerifyEmailRequest;
import com.udea.digitalbank.auth.dto.VerifyTwoFactorRequest;
import com.udea.digitalbank.auth.service.AuthService;
import com.udea.digitalbank.auth.service.EmailVerificationService;
import com.udea.digitalbank.auth.service.PasswordResetService;
import com.udea.digitalbank.shared.security.Actor;
import com.udea.digitalbank.shared.security.CurrentActor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.udea.digitalbank.shared.config.OpenAPIConfig;
import com.udea.digitalbank.shared.config.OpenApiExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * No hay endpoint de registro: los usuarios se crean desde el módulo de la persona (UserApi.createUser)
 * y definen su contraseña al confirmar su correo (verify-email).
 */
@Tag(name = OpenAPIConfig.TAG_AUTH)
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService,
                          EmailVerificationService emailVerificationService,
                          PasswordResetService passwordResetService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
    }

    @Operation(summary = "Confirmar el correo y definir la contraseña")
    @ApiResponse(responseCode = "204", description = "Correo confirmado y contraseña definida")
    @ApiResponse(responseCode = "400",
            description = "Solicitud inválida, código inválido o expirado, o la confirmación de la contraseña "
                + "no coincide")
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verifyEmail(
                request.getEmail(), request.getCode(), request.getPassword(), request.getConfirmPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reenviar el código de confirmación de correo")
    @ApiResponse(responseCode = "204", description = "Solicitud aceptada; responde igual exista o no el usuario")
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody EmailRequest request) {
        emailVerificationService.resendVerification(request.getEmail());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Iniciar sesión")
    @ApiResponse(responseCode = "200",
            description = "Credenciales válidas; el código de segundo factor se envía al correo")
    @ApiResponse(responseCode = "401", description = "Credenciales inválidas")
    @ApiResponse(responseCode = "403", description = "El usuario no puede iniciar sesión en su estado actual")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // requiresTwoFactor siempre viene en true si las credenciales son correctas:
        // el JWT todavía no se emite en este paso, ver AuthService.login
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Verificar el segundo factor")
    @ApiResponse(responseCode = "200", description = "Segundo factor verificado; devuelve el token de acceso")
    @ApiResponse(responseCode = "400", description = "Solicitud inválida, o código inválido o expirado")
    @ApiResponse(responseCode = "403", description = "El usuario ya no puede iniciar sesión")
    @PostMapping("/verify-2fa")
    public ResponseEntity<AuthResponse> verifyTwoFactor(@Valid @RequestBody VerifyTwoFactorRequest request) {
        return ResponseEntity.ok(authService.verifyTwoFactor(request.getChallengeId(), request.getCode()));
    }

    /** Público como /verify-2fa: el challengeId que devuelve /login prueba que el primer factor ya se supera. */
    @Operation(summary = "Reenviar el código de segundo factor")
    @ApiResponse(responseCode = "200", description = "Código de segundo factor reenviado")
    @ApiResponse(responseCode = "400", description = "Solicitud inválida, o código inválido o expirado")
    @ApiResponse(responseCode = "403", description = "El usuario ya no puede iniciar sesión")
    @ApiResponse(responseCode = "429", description = "Demasiados reenvíos, o reenvío demasiado pronto",
            headers = @Header(name = "Retry-After", description = "Segundos que faltan para poder pedir otro código; "
                    + "solo se envía cuando el reenvío es demasiado pronto, no cuando ya no quedan reenvíos",
                    schema = @Schema(type = "integer", example = OpenApiExamples.RETRY_AFTER_SECONDS)))
    @PostMapping("/resend-2fa")
    public ResponseEntity<ResendTwoFactorResponse> resendTwoFactor(@Valid @RequestBody ResendTwoFactorRequest request) {
        return ResponseEntity.ok(authService.resendTwoFactor(request.getChallengeId()));
    }

    @Operation(summary = "Cerrar sesión")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CurrentActor Actor actor) {
        authService.logout(actor.userId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Solicitar la recuperación de contraseña")
    @ApiResponse(responseCode = "204", description = "Solicitud aceptada; responde igual exista o no el usuario")
    @PostMapping("/recover-password")
    public ResponseEntity<Void> recoverPassword(@Valid @RequestBody EmailRequest request) {
        passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restablecer la contraseña")
    @ApiResponse(responseCode = "204", description = "Contraseña actualizada")
    @ApiResponse(responseCode = "400", description = "Solicitud inválida, o código inválido o expirado")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.resetPassword(request.getEmail(), request.getCode(), request.getPassword());
        return ResponseEntity.noContent().build();
    }
}
