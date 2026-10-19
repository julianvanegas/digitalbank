---
status: accepted
date: 2026-10-17
decision-makers: [Julian Vanegas López]
consulted: Equipo de Backend
tags: [arquitectura, transacciones, seguridad, dependencias]
---

# ADR-0004 — Transferir en dos pasos con segundo factor y permitir que `transactions` use `auth.api`

## Contexto y planteamiento del problema

El módulo `transactions`, previsto en la ADR-0002, nace con las transferencias entre cuentas del banco y con la exigencia de un segundo factor en las operaciones monetarias sensibles: el código vale 3 minutos, el tercer fallo cancela la operación y un código vencido obliga a reiniciarla.

Esto plantea dos problemas que las ADR anteriores no resuelven:

1. **El dinero no puede moverse hasta que el código se verifique, pero la operación debe existir mientras tanto.** Hace falta un estado intermedio y un modo de cancelar sin dejar dinero a medias (ADR-0001).
2. **El segundo factor vive en `auth`**, y la ADR-0002 dice que cada módulo conoce únicamente al que tiene inmediatamente por encima (`transactions → accounts`). `accounts` no tiene nada que ver con códigos de verificación.

## Factores de decisión

- **Atomicidad**: debitar, acreditar y marcar la transacción como completada ocurren juntos o no ocurren.
- **Distinguir los resultados del código**: el negocio pide mensajes y efectos distintos para incorrecto, intentos agotados y vencido; el mecanismo de `auth` solo respondía «inválido o expirado».
- **No acoplar `accounts` a responsabilidades ajenas.**
- **Consistencia con el modelo de datos**: `transactions` ya tiene los estados PENDING, COMPLETED, REJECTED y REVERSED.

## Decisión

### 1. La transferencia es un flujo de dos pasos sobre estados del modelo

- `POST /api/v1/transactions/transfers` valida la operación y crea una transacción **PENDING**, sin mover dinero, y envía el código al correo del cliente.
- `POST /api/v1/transactions/{reference}/confirm` verifica el código. Solo si es correcto se mueve el dinero (con las dos cuentas bloqueadas en orden de id, para evitar interbloqueos) y la transacción pasa a **COMPLETED**.
- Un código agotado o vencido, o una transferencia reemplazada por otra, pasa a **REJECTED**. Las validaciones del primer paso (misma cuenta, cuenta inexistente o inactiva, fondos insuficientes) fallan sin dejar fila: una transferencia imposible no es una transacción.
- El saldo y el estado de las cuentas se **revalidan al confirmar**, con las filas bloqueadas, porque pueden cambiar entre los dos pasos.
- Un cliente tiene como máximo una transferencia pendiente: el código vigente es uno por usuario y propósito (`UNIQUE (user_id, purpose_id)`), así que iniciar otra cancela la anterior.
- El 2FA se exige en **toda** transferencia, no solo sobre un umbral de monto.

### 2. `transactions` depende de `auth.api`, además de `accounts.api`

Se crea la fachada `auth.api.VerificationApi` (emitir y enviar el código, verificarlo y consultar su vigencia), con la misma regla de las demás fachadas: solo viaja el id del usuario y no se comparten entidades ni repositorios. El grafo pasa de ser una cadena a:

```
auth  ←  customer  ←  accounts  ←  transactions
  ↑___________________________________|
```

Es una excepción acotada a «solo el inmediato superior»: la flecha sigue yendo de lo específico a lo general, no hay ciclos, y `auth` sigue sin conocer a nadie.

### 3. La verificación devuelve un resultado, no lanza

`VerificationApi.verifyTransactionCode` devuelve `VALID`, `WRONG` (con los intentos que quedan), `EXHAUSTED` o `EXPIRED`. Un código que ya no existe se trata como `EXPIRED`, porque solo desaparece por vencimiento, por agotarse o por reemplazo, y los dos últimos casos los conoce quien llama. Así `transactions` decide qué hacer con la operación sin interpretar mensajes, y no duplica la vigencia del código.

### 4. Los rechazos no deshacen el registro del rechazo

`confirm` declara `noRollbackFor = BusinessException`: el contador de intentos fallidos y la marca REJECTED deben persistir aunque se lance la excepción que se le responde al cliente. Por la misma razón `AccountApi.applyTransfer` valida **ambas** cuentas antes de modificar cualquiera, y no marca la transacción del llamante para revertir cuando rechaza: si lo hiciera, el rechazo no podría registrarse.

### 5. Respuestas

| Situación | Código |
| --- | --- |
| Código incorrecto con intentos disponibles | 400, con los intentos que quedan |
| Intentos agotados (transferencia cancelada) | 429 |
| Código vencido (transferencia cancelada) | 409 |
| Fondos insuficientes | 409 |
| Cuenta de destino inexistente o no activa | 400, con su condición |
| Misma cuenta de origen y destino | 400 |
| Cuenta de origen ajena, o transferencia ajena | 404, igual que una inexistente |

## Alternativas consideradas

| Alternativa | Por qué se descartó |
| --- | --- |
| Que `accounts` exponga métodos de 2FA que solo reenvíen a `auth` | Respeta la letra de la ADR-0002 pero mete en `accounts` una responsabilidad que no es suya y añade una capa sin lógica. |
| Mover el dinero al iniciar y revertirlo si el código falla | Deja dinero comprometido mientras el cliente decide, exige un REVERSED por cada cancelación y rompe «no existe una lectura aceptable con el dinero a medio camino». |
| 2FA solo por encima de un umbral de monto | Dos caminos que probar y un umbral que el negocio no ha definido. Con un solo tipo de movimiento implementado, exigirlo siempre es lo más simple y seguro. |
| Vincular el código a la transacción en `verification_challenges` | Obligaría a `auth` a conocer a `transactions`, invirtiendo la dirección de las dependencias. |
| Registrar también las validaciones fallidas del primer paso como REJECTED | Llenaría la tabla de operaciones que nunca existieron y exigiría una columna de motivo que el modelo no tiene. |

## Consecuencias

### A favor

- El dinero solo se mueve con el código verificado, y el movimiento es atómico y sin interbloqueos.
- Cada resultado de la verificación del código (correcto, incorrecto, agotado, vencido) tiene su propio efecto y su propio mensaje.
- `transactions` no conoce entidades ni repositorios de `auth`.

### En contra y riesgos asumidos

- `transactions` conoce dos módulos en lugar de uno, y la regla sigue sin verificarse automáticamente (pendiente de ArchUnit o Spring Modulith).
- Solo puede haber una transferencia pendiente por cliente: iniciar otra cancela la primera.
- El motivo de un rechazo viaja en la respuesta, pero no se guarda: el modelo no tiene dónde.
- Quedan fuera de esta entrega los límites por tipo de cuenta (`limits`), el reenvío del código, el extracto de movimientos, depósitos y retiros.
- Las transferencias PENDING que nadie confirma se rechazan por un barrido que corre cada minuto; mientras tanto, una confirmación tardía ya se rechaza por código vencido.

## Evidencia en el repositorio

| Dónde | Qué muestra |
| --- | --- |
| `auth/api/VerificationApi.java`, `CodeCheckResult.java` | La fachada de 2FA y su resultado, sin excepciones. |
| `auth/service/VerificationService.java` (`check`) | La verificación que distingue incorrecto, agotado y vencido. |
| `transactions/service/TransferService.java` | El flujo en dos pasos, `noRollbackFor` y la revalidación al confirmar. |
| `accounts/api/AccountApi.java` (`applyTransfer`) | El movimiento atómico con las cuentas bloqueadas en orden. |
| `transactions/security/TransactionSecurity.java` | Quién puede iniciar, confirmar y consultar. |
| `db/migration/V5__transactions.sql` | El propósito `TRANSACTION` (3 minutos, 3 intentos) en `challenge_purposes`, y los catálogos y la tabla `transactions`. |
