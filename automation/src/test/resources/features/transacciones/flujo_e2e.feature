# language: es
@E2E
Característica: Flujo E2E del cliente del banco digital
  Como equipo de calidad
  Quiero ejecutar el recorrido completo de un cliente sobre la API
  Para verificar que las historias funcionan juntas, de punta a punta

  # Flujo: HU01 → HU08 → HU02 + HU03 → HU10 → HU18 → HU12 → HU19 (+HU21) → HU20 (+HU21) → HU12 → HU04

  Escenario: Desde el registro hasta el cierre de sesión
    # HU01: registro
    Dado que el administrador inició sesión en el banco
    Y que un usuario mayor de edad con documento y correo no registrados
    Cuando solicita registrarse
    Entonces recibe el registro aceptado con código 201
    Y el estado del cliente es "PENDING_VERIFICATION"
    # HU01 y HU08: confirmación del correo y aprobación del administrador
    Cuando confirma su correo con la contraseña "Segura#2026"
    Entonces el estado del cliente es "PENDING_REVIEW"
    Cuando el administrador cambia el estado del cliente a "ACTIVE"
    Entonces el estado del cliente es "ACTIVE"
    # HU02 y HU03: login con segundo factor
    Cuando el cliente inició sesión con doble factor
    # HU10: apertura de dos cuentas
    Y abre dos cuentas de ahorros
    # HU18 y HU12: consignación del administrador y verificación del saldo
    Y el administrador consigna 1000000 pesos en la cuenta de origen
    Entonces el saldo de la cuenta de origen es 1000000 pesos
    # HU19 y HU21: retiro con segundo factor
    Cuando retira 200000 pesos y confirma con el código de verificación
    Entonces la transacción queda en estado "COMPLETED"
    Y el saldo de la cuenta de origen es 800000 pesos
    # HU20 y HU21: transferencia con segundo factor
    Cuando transfiere 300000 pesos a su otra cuenta y confirma con el código de verificación
    Entonces la transacción queda en estado "COMPLETED"
    # HU12: saldos finales
    Y el saldo de la cuenta de origen es 500000 pesos
    Y el saldo de la cuenta de destino es 300000 pesos
    # HU04: cierre de sesión
    Cuando cierra la sesión
    Entonces su token anterior es rechazado
    Y para volver a entrar debe autenticarse de nuevo
