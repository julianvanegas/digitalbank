# language: es
@HU21
Característica: HU21 Verificar transacción mediante 2FA
  Como sistema de banca digital
  Quiero solicitar un segundo factor en retiros y transferencias
  Para asegurar que la operación la autoriza el titular

  # Cada escenario prepara su propio cliente y sus cuentas: el saldo se consigna con el administrador (HU18).
  # El código de la transacción vence a los 3 minutos y admite 3 intentos.

  Antecedentes:
    Dado que el administrador inició sesión en el banco
    Y que existe un cliente activo
    Y el cliente inició sesión con doble factor
    Y tiene una cuenta con saldo de 100000 pesos
    Y tiene otra cuenta propia sin saldo

  @CA01
  Escenario: CA01 Transferencia autorizada con el código correcto
    Dado que el cliente tiene una transferencia pendiente de autorización
    Cuando confirma la transacción con el código correcto
    Entonces la transacción queda completada

  @CA01
  Escenario: CA01 Retiro autorizado con el código correcto
    Dado que el cliente tiene un retiro pendiente de autorización
    Cuando confirma la transacción con el código correcto
    Entonces la transacción queda completada

  @CA02
  Escenario: CA02 Código incorrecto: informa los intentos restantes y no procesa la transacción
    Dado que el cliente tiene una transferencia pendiente de autorización
    Cuando confirma la transacción con un código incorrecto
    Entonces ve un mensaje que contiene "Te quedan 2 intentos"
    Y la transacción permanece pendiente
    Y el saldo de la cuenta de origen es 100000 pesos

  @CA03
  Escenario: CA03 Tercer código incorrecto: cancela el retiro
    Dado que el cliente tiene un retiro pendiente de autorización
    Cuando envía tres códigos incorrectos para la transacción
    Entonces la transacción queda cancelada
    Y el saldo de la cuenta de origen es 100000 pesos
