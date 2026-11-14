---
status: accepted
date: 2026-11-14
decision-makers: [Julian Vanegas López]
consulted: Equipo de Backend
tags: [transacciones, topes, seguridad, base-de-datos]
---

# ADR-0005 — Topes transaccionales por tipo de movimiento y de cuenta, y transferencias de un administrador en nombre del cliente

## Contexto y planteamiento del problema

El negocio impone topes diarios a los clientes (no a los administradores): 4 retiros al día, $2.500.000 por retiro y $10.000.000 acumulados; y $80.000.000 acumulados al día en transferencias. Todo retiro y toda transferencia exigen segundo factor. Cualquier usuario autenticado debe poder consultarlos, y han de poder variar por tipo de cuenta aunque hoy valgan igual para todos.

Además, un administrador debe poder transferir en nombre de un cliente sin que le apliquen los topes. Eso choca con ADR-0004, donde el ordenante, el titular de la cuenta y el dueño del código de verificación eran siempre el mismo usuario.

Los retiros no tienen flujo todavía; las transferencias sí.

## Factores de decisión

- **Configuración visible y futura edición**: los topes son datos, no constantes del código.
- **Un tope por combinación**: tipo de movimiento × tipo de cuenta.
- **No acoplar `transactions` a las entidades de `accounts`** (ADR-0002).
- **Un código vigente por titular** (ADR-0004), aunque quien ordene sea otro usuario.
- **Fallar cerrado**: un olvido de configuración no puede dejar una cuenta sin tope.

## Decisión

### 1. Los topes viven en la tabla `transaction_limits`

Una fila por (tipo de movimiento, tipo de cuenta), con `max_daily_count`, `max_per_transaction` y `max_daily_amount`; un máximo `NULL` significa «sin ese tope». La consignación no tiene filas: «no tiene topes definidos». El tipo de cuenta se guarda por **código** (FK a `account_types.code`) y no por id, para que `transactions` no conozca los ids de `accounts`.

No hay columna de 2FA: toda operación monetaria lo exige (ADR-0004), así que es una constante del código que la consulta informa siempre como `true`.

Al arrancar, `TransactionLimitService` exige que exista fila para cada tipo limitado (`TRANSFER`, `WITHDRAWAL`) y cada valor de `AccountTypeEnum`; si falta alguna, la aplicación no inicia.

### 2. Consulta: `GET /api/v1/transactions/limits`

La pueden usar `CUSTOMER` y `ADMIN`. Sin filtros devuelve todas las combinaciones; `?type=` y `?accountType=` filtran. Un tipo de movimiento o de cuenta sin topes responde **404** con «No existen topes definidos para…». Los filtros se leen como texto: con un enum, un valor desconocido daría el 400 genérico de solicitud mal formada. La ruta se declara antes de `GET /*` en `TransactionSecurity`, que de otro modo la tomaría por una referencia.

### 3. Cumplimiento

- **Se mide por cuenta de origen**, por **día calendario** en la zona del `Clock`, contando solo transacciones `COMPLETED` del mismo tipo. El día se toma de `created_at`: el modelo no guarda el instante de la confirmación.
- **Solo a clientes**: el servicio no sabe de roles; quien llama no lo invoca si el ordenante es administrador. Los movimientos del administrador no están exentos de contar: consumen el cupo de la cuenta, porque el consumo se mide sobre la cuenta y no sobre quien ordena.
- **Dos veces en transferencias**: al iniciar (falla rápido) y al confirmar (definitivo; si ya no cabe, la transferencia queda `REJECTED`). Se responde **409**, como con fondos insuficientes, con el cupo disponible.
- No hace falta un bloqueo extra: solo hay una transferencia pendiente por titular, y iniciar otra espera el bloqueo de la anterior.

### 4. Un administrador transfiere en nombre del cliente

- `POST /transfers` y `POST /{reference}/confirm` admiten también `ADMIN`. El administrador opera sobre la cuenta de **cualquier** cliente y no se le aplican los topes.
- **El segundo factor es del titular de la cuenta de origen**: el código llega a su correo (que dice que lo inició un empleado) y el administrador lo ingresa al confirmar. Para eso `AccountApi.getOwnerUserId` entrega el usuario titular y `AccountView` informa el tipo de cuenta.
- `user_originator_id` sigue siendo quien ordena (el administrador), lo que deja la trazabilidad. Solo quien inició la transferencia puede confirmarla.
- Como el código es del titular, **la transferencia pendiente se busca por cuentas de origen** y no por ordenante: iniciar una transferencia sobre las cuentas del cliente cancela cualquier otra pendiente de él, la haya ordenado el cliente o un administrador. Un administrador puede tener pendientes de varios clientes a la vez.
- El comprobante por correo va al titular, y el titular puede consultar el comprobante de una transferencia que un administrador ordenó a su nombre.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
| --- | --- |
| Topes en `application.properties` | Cambiarlos exige redesplegar y no admite variar por tipo de cuenta sin una estructura propia; la tabla es consistente con los catálogos y deja lista su edición futura. |
| Columna `requires_2fa` | Sería un dato configurable que nada respeta: el flujo ya lo exige siempre. Un valor `false` mentiría. |
| Acumular por cliente (todas sus cuentas) | Con topes por tipo de cuenta, el tope de dos cuentas distintas haría ambiguo el acumulado. |
| Ventana móvil de 24 horas | El cliente no sabría cuándo se libera su cupo. |
| Que el cliente confirme las transferencias de un administrador desde su sesión | Exige que el cliente vea transacciones que no inició y cambia el modelo de permisos de `/confirm`. |
| Guardar el titular del código en la transacción | Añade una columna y una migración; el titular se deriva de la cuenta de origen. |

## Consecuencias

### A favor

- Los topes se leen de la base y pueden cambiar por tipo de cuenta sin tocar el código.
- La configuración incompleta impide arrancar en vez de permitir una cuenta sin tope.
- Un administrador puede operar en nombre del cliente con el segundo factor del titular, con trazabilidad del ordenante.

### En contra y riesgos asumidos

- Un cliente con varias cuentas dispone de un cupo diario por cada una.
- El día se cuenta desde la creación de la transferencia: una iniciada antes de medianoche y confirmada después cuenta en el día anterior.
- Los retiros solo informan sus topes: se aplicarán cuando exista el flujo de retiro. Entonces se invocará `requireWithinLimits` con `WITHDRAWAL`.
- Los topes son de solo lectura desde la API: no hay endpoint para editarlos.
- Los movimientos de un administrador consumen el cupo del cliente aunque no lo respeten, de modo que pueden agotarlo.

## Evidencia en el repositorio

| Dónde | Qué muestra |
| --- | --- |
| `db/migration/V6__transaction_limits.sql` | La tabla, sus restricciones y la semilla. |
| `transactions/service/TransactionLimitQueryService.java` | La consulta de los topes vigentes, de solo lectura. |
| `transactions/service/TransactionLimitService.java`, `DailyUsage.java` | El cumplimiento (reglas sobre el consumo del día) y la validación de arranque. |
| `transactions/service/TransactionAccess.java` | Quién puede ver una transacción, en un solo lugar. |
| `transactions/controller/TransactionLimitController.java` | El endpoint de consulta. |
| `transactions/service/LimitRule.java` y sus implementaciones | Cada tope es una regla (por operación, cantidad diaria, acumulado diario): agregar uno es una clase, no otra rama. |
| `transactions/service/TransferParties.java` (`operationFor`, `enforceLimits`) | Lo que cambia entre cliente y administrador (dueño del código, topes, aviso del correo) se resuelve en un solo lugar, sin preguntar por el rol en cada paso. |
| `transactions/service/TransferInitiationService.java`, `TransferConfirmationService.java` | Topes al iniciar y al confirmar, y el flujo del administrador. |
| `transactions/security/TransactionSecurity.java` | Roles de cada ruta y el orden de `/limits`. |
| `accounts/api/AccountApi.java` (`getOwnerUserId`), `AccountView` | Titular y tipo de cuenta para otros módulos. |
