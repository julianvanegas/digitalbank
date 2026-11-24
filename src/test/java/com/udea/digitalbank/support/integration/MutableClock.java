package com.udea.digitalbank.support.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Reloj que la prueba controla: arranca en el instante real en que empieza cada prueba y solo avanza cuando la prueba
 * lo pide. Sirve para comprobar vencimientos (códigos, sesiones, transacciones pendientes) y cambios de día sin
 * esperar. Arranca en la hora real y no en una fija porque la librería JWT valida la expiración del token con el
 * reloj del sistema: un token emitido en una fecha pasada llegaría ya vencido.
 */
public final class MutableClock extends Clock {

    public static final ZoneId ZONE = ZoneOffset.UTC;

    private Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    public synchronized void reset() {
        now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public synchronized LocalDateTime now() {
        return LocalDateTime.ofInstant(now, ZONE);
    }

    public synchronized void advance(Duration duration) {
        now = now.plus(duration);
    }

    public synchronized void set(LocalDateTime dateTime) {
        now = dateTime.atZone(ZONE).toInstant();
    }

    @Override
    public synchronized Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("El reloj de prueba trabaja siempre en " + ZONE);
    }
}
