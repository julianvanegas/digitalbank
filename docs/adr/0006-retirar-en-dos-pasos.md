---
status: accepted
date: 2026-11-15
decision-makers: [Yuli Karina Jiménez Paredes, Julian Vanegas Lopez]
consulted: Equipo de Backend
tags: [transacciones, retiros, seguridad, topes]
---

# ADR-0006 — Retirar en dos pasos con el código del titular y los topes del cliente

## Contexto y planteamiento del problema

La HU19 pide retirar dinero de una cuenta: con la cuenta activa, saldo suficiente y el código correcto se debita el monto (CA01); un monto mayor que el saldo se rechaza (CA02); un retiro que supera el límite diario se rechaza (CA03); y un código incorrecto se rechaza y se registra el intento fallido (CA04).

Las ADR anteriores dejaron casi todo el terreno preparado. La ADR-0005 ya exige segundo factor a todo retiro, define sus topes (4 retiros al día, $2.500.000 por retiro y $10.000.000 acumulados) y anticipa que el flujo de retiro invocará `requireWithinLimits` con `WITHDRAWAL`. El modelo de datos de la V5 ya contempla el tipo `WITHDRAWAL`, con cuenta de origen y sin cuenta de destino.

Lo que faltaba decidir es cómo encaja un movimiento de un solo lado en un flujo pensado para transferencias: qué endpoint lo confirma, quién lo confirma cuando lo inicia un administrador, qué topes se aplican y qué devuelve.

## Factores de decisión

- **Coherencia con la transferencia**: mismo flujo de dos pasos, mismos estados y mismas reglas de rol (ADR-0004 y ADR-0005), salvo donde el retiro sea distinto.
- **Un solo contrato de confirmación**: lo que se autoriza con un código se confirma de una sola manera, y el cliente no necesita saber de antemano qué ruta corresponde a cada tipo.
- **No mezclar las reglas de un tipo con las de otro**: un retiro no debe pasar por la lógica de las transferencias, y al revés. La separación se hace dentro del servicio, no duplicando la ruta.
- **El código es del titular**: la verificación la hace siempre el cliente, aunque inicie un administrador.
- **Tocar lo mínimo de lo existente**: el retiro se construye junto al flujo de transferencias, no reescribiéndolo desde cero.

## Decisión

### 1. Dos pasos, con una sola ruta de confirmación

- `POST /api/v1/transactions/withdrawals` valida la operación, crea una transacción **PENDING** sin mover dinero y envía el código al titular de la cuenta. Responde 201, como `POST /transfers`.
- `POST /api/v1/transactions/{reference}/confirm` confirma **tanto transferencias como retiros**: la referencia (`TRF-…` o `RET-…`) dice cuál. Verifica el código y, solo si es correcto, mueve el dinero y la transacción pasa a **COMPLETED**. Para un retiro, el movimiento es el débito de la cuenta con la fila bloqueada (`AccountApi.applyWithdrawal`).
- El retiro no tiene una ruta de confirmación propia: usa la misma que la transferencia. Por qué no la tiene, en «Alternativas consideradas».

La confirmación es un único servicio, `TransactionConfirmationService`, que hace lo que es igual para todos los tipos: buscar la transacción con la fila bloqueada, comprobar que la ordenó el usuario autenticado, devolver el comprobante si ya está completada, verificar el código y rechazar la transacción al agotar los intentos o al vencer el código. Lo que cambia por tipo vive en una `ConfirmableOperation`:

| Pieza | Qué hace |
| --- | --- |
| `TransferConfirmable` | Revalida los topes, mueve el dinero entre las dos cuentas y publica el comprobante por correo |
| `WithdrawalConfirmable` | Revalida los topes (si aplican), debita la cuenta y no envía correo |

El servicio recibe todas las operaciones y elige la de la transacción por su **tipo guardado**, no por el prefijo de la referencia. Si el tipo no tiene operación (el depósito, que se hace en un paso y nace `COMPLETED`), responde 400 antes de verificar el código, así que un cliente no gasta un intento por mandar una referencia equivocada. La comprobación de que la transacción es del usuario va antes que la del tipo: la de otro usuario responde 404 sin revelar de qué tipo es.

Los textos de error (código incorrecto, intentos agotados, código vencido) los aporta cada operación, de modo que un retiro sigue diciendo «el retiro fue cancelado». Un tipo nuevo que se autorice con código solo necesita su propia `ConfirmableOperation`.

El comprobante se consulta con el `GET /{reference}` existente, sin cambios: lo ven el ordenante, el titular de la cuenta de origen y cualquier administrador.

### 2. Confirma quien inició, con el código del titular

Como en la transferencia (ADR-0005), confirma el ordenante. Si inició un administrador, el código llega al correo del titular y el administrador lo ingresa al confirmar; el titular no puede confirmar un retiro que no inició y recibe 404. La frase «la verificación la hace siempre el cliente» se cumple porque el código es suyo: sin él no hay retiro.

Encaja con un retiro en ventanilla: el cajero necesita saber que el retiro quedó confirmado antes de entregar el efectivo, y lo sabe porque es él quien confirma.

### 3. Cliente y administrador; los topes, solo al cliente

Un cliente retira de sus propias cuentas (una ajena responde como inexistente); un administrador, de la de cualquier cliente. Lo que cambia según el rol (dueño del código, si se aplican los topes, aviso de empleado en el correo) se resuelve con `TransferParties.operationFor`, el mismo lugar que lo decide en las transferencias. Lo confirmó el equipo de Backend.

### 4. Se validan los tres topes de retiro

`requireWithinLimits(cuenta, WITHDRAWAL, monto)` aplica el máximo por retiro, la cantidad diaria y el acumulado diario, no solo el acumulado que nombra CA03. Se aplica al iniciar (para fallar rápido) y al confirmar (definitivo; si ya no cabe, el retiro queda REJECTED), como en la transferencia. Los movimientos de un administrador no los respetan, pero cuentan en el consumo de la cuenta (ADR-0005).

### 5. El comprobante no lleva el saldo

`TransactionResponse` no cambia. «Actualizar el saldo» de CA01 se cumple porque la cuenta queda debitada; el saldo se consulta con la HU12.

### 6. La referencia empieza por `RET-`

Sigue la convención de tres letras de `TRF-` y `DEP-`. Se eligió `RET-` (retiro) y no `WDR-` (*withdrawal*, el nombre del enum) porque la referencia es el comprobante que ve el cliente, y todo lo que ve el cliente en esta API está en español.

### 7. Respuestas

| Situación | Código |
| --- | --- |
| Retiro iniciado (PENDING) | 201 |
| Retiro confirmado (COMPLETED) | 200 |
| Monto igual o menor que cero, con más de dos decimales, o solicitud mal formada | 400 |
| Código incorrecto con intentos disponibles | 400, con los intentos que quedan |
| Referencia de un tipo que no se confirma con código (un depósito) | 400 |
| Cuenta no activa | 403 |
| Cuenta ajena o inexistente, o retiro ajeno | 404, igual que uno inexistente |
| Saldo insuficiente, tope superado, código vencido o retiro que ya no está pendiente | 409 |
| Intentos agotados (retiro cancelado) | 429 |

## Alternativas consideradas

| Alternativa | Por qué se descartó |
| --- | --- |
| Una ruta propia para confirmar retiros (`POST /withdrawals/{reference}/confirm`), con su servicio y una guarda de tipo en cada ruta | Duplicaría casi entero el servicio de confirmación (buscar, comprobar al ordenante, verificar el código, rechazar), exigiría dos guardas que solo existirían para que cada ruta no confirmara lo de la otra, y obligaría a mantener dos DTO idénticos y a documentar dos rutas por lo mismo. Cada tipo nuevo añadiría otra ruta, otro servicio y otra guarda. |
| Exponer además `POST /withdrawals/{reference}/confirm` como alias de la ruta única | Mantendría dos rutas para lo mismo y la documentación de ambas. El equipo prefirió un único contrato. |
| Despachar dentro del servicio con un `if` o un `switch` por tipo | Reúne el flujo común pero deja la lógica de cada tipo en un solo método que crece con cada tipo nuevo. Una `ConfirmableOperation` por tipo acota lo que cambia y deja el servicio común intacto. |
| Que el titular confirme desde su sesión el retiro que inició un administrador | La ADR-0005 ya lo descartó para las transferencias: exige que el cliente vea operaciones que no inició y cambia el modelo de permisos de la confirmación. Además, el cajero no sabría cuándo entregar el efectivo. |
| Validar solo el acumulado diario, que es el único que nombra CA03 | `requireWithinLimits` aplica todos los topes definidos; validar solo uno exigiría otro camino en el servicio de topes y dejaría sin efecto dos topes que la HU14 declara. |
| Añadir el saldo resultante a `TransactionResponse` | Cambia el contrato JSON también de transferencias y depósitos, que tendrían el campo vacío o habría que rellenarlo, y lo que pide CA01 ya se cumple con el débito. |
| Prefijo `WDR-` | Coherente con el nombre del enum, pero no le dice nada al cliente que lee el comprobante. |
| Retirar en un solo paso, como la consignación | La ADR-0005 exige segundo factor a todo retiro. En un paso, además, la comprobación de los topes tendría que hacerse con la cuenta bloqueada para que dos retiros simultáneos no pasaran el acumulado. |

## Consecuencias

### A favor

- El retiro reutiliza el modelo de la transferencia: estados, segundo factor, topes, reglas de rol y cancelación de pendientes.
- La confirmación existe una sola vez: un arreglo en cómo se verifica el código, se rechaza o se registra vale para todos los tipos.
- Un tipo nuevo que se autorice con código se añade con una `ConfirmableOperation`, sin ruta, DTO ni servicio nuevos.
- El cliente usa una sola ruta de confirmación y no puede equivocarse de ruta, así que una referencia de retiro no puede llegar a la lógica de las transferencias, ni al revés.
- El rechazo de un retiro al confirmar queda registrado como REJECTED aunque se responda con error (`noRollbackFor`, como en la ADR-0004).

### En contra y riesgos asumidos

- **La ruta no dice qué se confirma.** Solo lo dice la referencia (`TRF-` o `RET-`) y el tipo guardado en la transacción. La documentación OpenAPI de la confirmación es genérica («transferencia o retiro»), aunque los mensajes de error sí son específicos de cada tipo.
- **El despacho depende del tipo guardado.** Una transacción de un tipo que se confirme con código pero que no tenga su `ConfirmableOperation` responde 400 como un depósito. Un tipo nuevo sin su operación falla en la primera confirmación, no al arrancar.
- **El registro del intento fallido de CA04 es efímero.** El contador vive en la fila del reto de verificación (`verification_challenges.failed_attempts`), que se borra al acertar, al agotar los intentos o al vencerse el código. Lo único que perdura es la transacción REJECTED cuando se agotan. Una auditoría duradera de intentos exigiría una tabla nueva.
- **El retiro no envía correo de comprobante**, aunque la transferencia sí. Fue una decisión: ningún criterio de la HU19 lo pide.
- **El código es uno por usuario**: iniciar un retiro invalida una transferencia pendiente del mismo titular, y al revés, porque las dos operaciones comparten el propósito `TRANSACTION`.
- **El retiro depende de piezas con nombre de transferencia**: `TransferParties`, `TransferOperation`, `PendingTransfers` y los textos neutros de `TransferMessages` los usan ahora los dos flujos. Se dejaron sin renombrar para no tocar medio módulo; queda como deuda conocida, y un cambio en ellos afecta a los dos flujos.
- **Los mensajes de los topes no usan las palabras de CA03** («excedió el límite permitido»), porque son compartidos con las transferencias y con la HU14. Informan el tope superado: el máximo por operación, la cantidad máxima diaria, o el acumulado diario con el cupo que queda.
- Como en la transferencia, el día del tope se cuenta desde la creación del retiro, no desde su confirmación.

## Evidencia en el repositorio

| Dónde | Qué muestra |
| --- | --- |
| `transactions/service/TransactionConfirmationService.java` | El flujo común de confirmación, el despacho por tipo, el rechazo de lo que no tiene operación antes de verificar el código y el `noRollbackFor`. |
| `transactions/service/ConfirmableOperation.java`, `TransferConfirmable.java`, `WithdrawalConfirmable.java` | Lo que cambia por tipo: cómo se mueve el dinero y los textos de error. |
| `transactions/service/WithdrawalInitiationService.java` | El primer paso del retiro; los topes al iniciar. |
| `transactions/controller/TransactionController.java` | La ruta única `POST /{reference}/confirm`. |
| `transactions/controller/WithdrawalController.java` | `POST /withdrawals`, la única ruta propia del retiro. |
| `transactions/dto/ConfirmTransactionRequest.java` | El cuerpo de la confirmación, común a los dos tipos. |
| `accounts/api/AccountApi.java` (`applyWithdrawal`) | El débito con la fila bloqueada. |
| `transactions/domain/Transaction.java` (`withdrawal`) | El retiro nace PENDING, con cuenta de origen y sin destino. |
| `transactions/service/TransactionReferenceGenerator.java` (`forWithdrawal`) | El prefijo `RET-`. |
| `transactions/dto/WithdrawalRequest.java` | El monto: positivo y con máximo dos decimales, como en la transferencia. |
| `transactions/security/TransactionSecurity.java` | Cliente y administrador en `/withdrawals` y en la confirmación común. |
| `transactions/service/TransactionConfirmationTypeDispatchHuTest.java` | El despacho por tipo, los textos de cada uno y el rechazo del depósito. |
