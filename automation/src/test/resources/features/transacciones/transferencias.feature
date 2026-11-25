# language: es
@HU20
Característica: HU20 Realizar transferencia entre cuentas
  Como cliente autenticado
  Quiero transferir fondos desde mi cuenta hacia otra cuenta del mismo banco
  Para mover dinero de forma rápida y digital

  # El saldo se prepara con una consignación del administrador (HU18). Toda transferencia exige el código de
  # segundo factor (HU21), que la prueba lee de la base de QA.

  Antecedentes:
    Dado que el administrador inició sesión en el banco
    Y que existe un cliente activo
    Y el cliente inició sesión con doble factor
    Y tiene una cuenta con saldo de 500000 pesos
    Y tiene otra cuenta propia sin saldo

  @CA01
  Escenario: CA01 Transferencia exitosa entre cuentas del mismo banco
    Cuando transfiere 200000 pesos a su otra cuenta y confirma con el código de verificación
    Entonces recibe el comprobante de la operación
    Y la transacción queda en estado "COMPLETED"
    Y el saldo de la cuenta de origen es 300000 pesos
    Y el saldo de la cuenta de destino es 200000 pesos

  @CA02
  Escenario: CA02 Transferencia con saldo insuficiente
    Cuando solicita transferir 600000 pesos a su otra cuenta
    Entonces recibe el rechazo con código 409
    Y ve un mensaje que contiene "Fondos insuficientes"
    Y el saldo de la cuenta de origen es 500000 pesos

  @CA03
  Escenario: CA03 Transferencia a la misma cuenta de origen
    Cuando solicita transferir 100000 pesos a la misma cuenta
    Entonces recibe el rechazo con código 400
    Y ve un mensaje que contiene "misma cuenta"

  @CA04
  Escenario: CA04 Cuenta de destino inexistente
    Cuando solicita transferir 100000 pesos a una cuenta que no existe
    Entonces recibe el rechazo con código 400
    Y ve un mensaje que contiene "no existe"

  @CA04
  Escenario: CA04 Cuenta de destino bloqueada
    Dado el administrador bloquea la cuenta de destino
    Cuando solicita transferir 100000 pesos a su otra cuenta
    Entonces recibe el rechazo con código 400
    Y ve un mensaje que contiene "bloqueada"
    Y el saldo de la cuenta de origen es 500000 pesos

  # Las transferencias no tienen tope por operación, solo el acumulado diario de 80.000.000 (HU14). Una sola
  # transferencia de 80.000.001 lo supera.
  @CA07
  Escenario: CA07 Exceso del límite diario acumulado de transferencias
    Dado tiene una cuenta con saldo de 80000001 pesos
    Cuando solicita transferir 80000001 pesos a su otra cuenta
    Entonces recibe el rechazo con código 409
    Y ve un mensaje que contiene "acumulado diario"
