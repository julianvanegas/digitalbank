package com.udea.digitalbank.support.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Reloj que la prueba controla: arranca en un instante fijo y solo avanza cuando la prueba lo pide. Sirve para
 * comprobar vencimientos (códigos, sesiones, transacciones pendientes) y cambios de día sin esperar.
 */
public final class MutableClock extends Clock {

    public static final ZoneId ZONE = ZoneOffset.UTC;
    public static final LocalDateTime START = LocalDateTime.of(2026, 11, 23, 10, 0);

    private Instant now = START.atZone(ZONE).toInstant();

    public synchronized void reset() {
        now = START.atZone(ZONE).toInstant();
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
