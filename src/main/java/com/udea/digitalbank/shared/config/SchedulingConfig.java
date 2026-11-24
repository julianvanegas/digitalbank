package com.udea.digitalbank.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa los trabajos programados ({@code @Scheduled}). Están activos salvo que {@code scheduling.enabled=false}: las
 * pruebas de integración los apagan para que un trabajo no cambie los datos en mitad de una prueba, y los invocan
 * directamente cuando los quieren probar.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
