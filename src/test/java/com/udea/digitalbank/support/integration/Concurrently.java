package com.udea.digitalbank.support.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Lanza varias tareas a la vez para provocar carreras reales contra la base. Todos los hilos esperan en una misma
 * barrera y salen juntos, así las peticiones llegan a PostgreSQL prácticamente al mismo tiempo. Si alguna no termina
 * en el plazo (por ejemplo, por un interbloqueo que nadie resuelve), la prueba falla en lugar de quedarse colgada.
 */
public final class Concurrently {

    private static final long TIMEOUT_SECONDS = 30;

    private Concurrently() {
    }

    /** Ejecuta las tareas en paralelo y devuelve sus resultados en el mismo orden. */
    public static <T> List<T> run(List<Callable<T>> tasks) {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } catch (TimeoutException e) {
            throw new AssertionError("Una tarea concurrente no terminó en " + TIMEOUT_SECONDS + " s", e);
        } catch (Exception e) {
            throw new IllegalStateException("Falló una tarea concurrente", e);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Ejecuta la misma tarea {@code times} veces en paralelo. */
    public static <T> List<T> times(int times, Callable<T> task) {
        return run(java.util.Collections.nCopies(times, task));
    }
}
