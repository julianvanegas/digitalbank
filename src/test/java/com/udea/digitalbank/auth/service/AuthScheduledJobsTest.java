package com.udea.digitalbank.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Pruebas unitarias (AAA) de {@link AuthScheduledJobs}: las tareas programadas que evalúan la inactividad de los
 * clientes y limpian códigos y sesiones vencidos. Se verifica a quién delega cada tarea y con qué frecuencia corre.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU04 / HU09 - AuthScheduledJobs")
class AuthScheduledJobsTest {

    @Mock
    private VerificationService verificationService;
    @Mock
    private UserStatusService userStatusService;
    @Mock
    private SessionService sessionService;

    private AuthScheduledJobs jobs;

    @BeforeEach
    void setUp() {
        jobs = new AuthScheduledJobs(verificationService, userStatusService, sessionService);
    }

    @Test
    @DisplayName("HU09 CA03 - La evaluación de inactividad marca a los clientes inactivos")
    void deberiaEvaluarLaInactividad() {
        // Act
        jobs.markInactive();

        // Assert
        verify(userStatusService).markInactiveCustomers();
        verifyNoInteractions(verificationService, sessionService);
    }

    @Test
    @DisplayName("HU09 CA03 - La evaluación de inactividad corre automáticamente todos los días a las 2:00 a. m.")
    void deberiaEvaluarLaInactividadCadaDia() throws NoSuchMethodException {
        // Act
        Scheduled scheduled = AuthScheduledJobs.class.getMethod("markInactive").getAnnotation(Scheduled.class);

        // Assert
        assertThat(scheduled.cron()).isEqualTo("0 0 2 * * *");
    }

    @Test
    @DisplayName("HU04 CA03 - La limpieza borra los códigos y las sesiones vencidos")
    void deberiaLimpiarCodigosYSesionesVencidos() {
        // Act
        jobs.cleanExpired();

        // Assert
        verify(verificationService).deleteExpired();
        verify(sessionService).deleteExpired();
        verifyNoInteractions(userStatusService);
    }
}
