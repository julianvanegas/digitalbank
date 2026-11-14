package com.udea.digitalbank.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj de la aplicación. Los servicios lo reciben por constructor en lugar de llamar a {@code now()} directamente,
 * así la hora y la zona se definen aquí y los tests pueden sustituirlo por un reloj fijo.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
