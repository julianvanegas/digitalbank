# language: es
@HU12
Característica: HU12 Consultar saldo de una cuenta
  Como cliente autenticado
  Quiero consultar el saldo de una de mis cuentas
  Para conocer la cantidad de dinero disponible en ella

  # Autor original: integrante C.

  Antecedentes:
    Dado que el administrador inició sesión en el banco
    Y que existe un cliente activo
    Y el cliente inició sesión con doble factor

  @CA01
  Escenario: CA01 Consulta de saldo exitosa
    Dado tiene una cuenta con saldo de 50000 pesos
    Cuando el cliente consulta el saldo de su cuenta
    Entonces ve un saldo de 50000 pesos en su cuenta

  # Una cuenta ajena responde igual que una inexistente, para no revelar que el número existe.
  @CA03
  Escenario: CA03 Cuenta perteneciente a otro cliente
    Dado que otro cliente tiene una cuenta con saldo de 50000 pesos
    Cuando el cliente intenta consultar el saldo de la cuenta del otro cliente
    Entonces recibe el rechazo con código 404
    Y ve un mensaje que contiene "Cuenta no encontrada"
    Y no ve información financiera de la cuenta

  @CA05
  Escenario: CA05 Consulta no altera el saldo
    Dado tiene una cuenta con saldo de 50000 pesos
    Y guarda el saldo actual de su cuenta
    Cuando el cliente consulta el saldo de su cuenta
    Entonces ve un saldo de 50000 pesos en su cuenta
    Y el saldo de la cuenta permanece sin modificaciones
