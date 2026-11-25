# language: es
@HU21
Característica: Autorizar retiros y transferencias con segundo factor

  @CA01
  Escenario: Confirmar transferencia con el código correcto
    Dado que el cliente con cuentas de prueba inició sesión
    Y que el cliente tiene una transferencia pendiente de autorización
    Cuando confirma la transacción con el código correcto
    Entonces la transacción queda completada

  @CA01
  Escenario: Confirmar retiro con el código correcto
    Dado que el cliente con cuentas de prueba inició sesión
    Y que el cliente tiene un retiro pendiente de autorización
    Cuando confirma la transacción con el código correcto
    Entonces la transacción queda completada

  @CA02
  Escenario: Mantener pendiente la transacción con un código incorrecto
    Dado que el cliente con cuentas de prueba inició sesión
    Y que el cliente tiene una transferencia pendiente de autorización
    Cuando confirma la transacción con un código incorrecto
    Entonces la transacción permanece pendiente

  @CA03
  Escenario: Cancelar el retiro al agotar intentos del segundo factor
    Dado que el cliente con cuentas de prueba inició sesión
    Y que el cliente tiene un retiro pendiente de autorización
    Cuando envía tres códigos incorrectos para la transacción
    Entonces la transacción queda cancelada
