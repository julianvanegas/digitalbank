# language: es
@HU12
Característica: HU12 Consultar saldo de una cuenta
  Como cliente autenticado
  Quiero consultar el saldo de una de mis cuentas
  Para conocer la cantidad de dinero disponible en ella

  Antecedentes:
    Dado que el administrador inició sesión en el banco

  @CA01
  Escenario: CA01 Consulta de saldo exitosa
    Dado que existe un cliente activo con una cuenta activa y saldo de 50000 pesos
    Cuando el cliente consulta el saldo de su cuenta
    Entonces ve un saldo de 50000 pesos

  @CA03
  Escenario: CA03 Cuenta perteneciente a otro cliente
    Dado que existe un cliente activo autenticado
    Y que otro cliente tiene una cuenta activa con saldo de 50000 pesos
    Cuando el cliente intenta consultar el saldo de la cuenta del otro cliente
    Entonces recibe el rechazo de la consulta de saldo
    Y no ve información financiera de la cuenta

  @CA05
  Escenario: CA05 Consulta no altera el saldo
    Dado que existe un cliente activo con una cuenta activa y saldo de 50000 pesos
    Y guarda el saldo actual de su cuenta
    Cuando el cliente consulta el saldo de su cuenta
    Entonces ve un saldo de 50000 pesos
    Y el saldo de la cuenta permanece sin modificaciones