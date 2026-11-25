# language: es
@HU19
Característica: HU19 Realizar retiro
  Como cliente titular de la cuenta
  Quiero retirar dinero de mi cuenta
  Para disponer de mis fondos en efectivo

  # El saldo se prepara con una consignación del administrador (HU18). Todo retiro exige el código de segundo
  # factor (HU21), que la prueba lee de la base de QA. Topes de HU14: 4 retiros al día y 2.500.000 por retiro.

  Antecedentes:
    Dado que el administrador inició sesión en el banco
    Y que existe un cliente activo
    Y el cliente inició sesión con doble factor

  @CA01
  Escenario: CA01 Retiro exitoso
    Dado tiene una cuenta con saldo de 500000 pesos
    Cuando retira 100000 pesos y confirma con el código de verificación
    Entonces recibe el comprobante del retiro por 100000 pesos
    Y el saldo de la cuenta de origen es 400000 pesos

  @CA02
  Escenario: CA02 Fondos insuficientes
    Dado tiene una cuenta con saldo de 50000 pesos
    Cuando solicita retirar 100000 pesos
    Entonces recibe el rechazo con código 409
    Y ve un mensaje que contiene "Saldo insuficiente"
    Y el saldo de la cuenta de origen es 50000 pesos

  @CA04
  Escenario: CA04 Exceso de retiros diarios
    Dado tiene una cuenta con saldo de 500000 pesos
    Y ya realizó 4 retiros de 10000 pesos en el día
    Cuando solicita retirar 10000 pesos
    Entonces recibe el rechazo con código 409
    Y ve un mensaje que contiene "máximo de 4 operaciones diarias"
    Y el saldo de la cuenta de origen es 460000 pesos

  @CA05
  Escenario: CA05 Monto superior al máximo por retiro
    Dado tiene una cuenta con saldo de 3000000 pesos
    Cuando solicita retirar 2500001 pesos
    Entonces recibe el rechazo con código 409
    Y ve un mensaje que contiene "máximo por operación"
    Y el saldo de la cuenta de origen es 3000000 pesos

  @CA07
  Escenario: CA07 Cuenta no activa
    Dado tiene una cuenta con saldo de 100000 pesos
    Y el administrador bloquea la cuenta de origen
    Cuando solicita retirar 10000 pesos
    Entonces recibe el rechazo con código 403
    Y ve un mensaje que contiene "No se puede retirar de la cuenta"

  # Una cuenta ajena responde igual que una inexistente, para no revelar que el número existe.
  @CA08
  Escenario: CA08 Cuenta de otro cliente
    Dado que otro cliente tiene una cuenta con saldo de 100000 pesos
    Cuando intenta retirar 10000 pesos de la cuenta del otro cliente
    Entonces recibe el rechazo con código 404
    Y ve un mensaje que contiene "no encontrada"

  @CA09
  Escenario: CA09 Autorización 2FA en todo retiro
    Dado tiene una cuenta con saldo de 100000 pesos
    Cuando solicita retirar 30000 pesos
    Entonces la transacción queda en estado "PENDING"
    Y el saldo de la cuenta de origen es 100000 pesos
