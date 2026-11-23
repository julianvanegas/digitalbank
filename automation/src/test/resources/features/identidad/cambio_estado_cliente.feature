# language: es
@HU08
Característica: HU08 Cambiar estado de un cliente
  Como administrador
  Quiero cambiar el estado de un cliente
  Para controlar quién puede usar la plataforma

  Antecedentes:
    Dado que el administrador inició sesión en el banco

  @CA01
  Escenario: CA01 El administrador cambia el estado de un cliente activo
    Dado que existe un cliente activo
    Cuando el administrador cambia el estado del cliente a "BLOCKED"
    Entonces el cambio de estado es aceptado

  @CA02
  Escenario: CA02 Un usuario sin rol de administrador no puede cambiar estados
    Dado que existe un cliente activo
    Cuando un usuario sin rol de administrador intenta cambiar el estado del cliente a "BLOCKED"
    Entonces recibe el rechazo con código 403
    Y el estado del cliente es "ACTIVE"

  @CA05
  Escenario: CA05 El nuevo estado queda reflejado al consultar al cliente
    Dado que existe un cliente activo
    Cuando el administrador cambia el estado del cliente a "BLOCKED"
    Entonces el estado del cliente es "BLOCKED"

  @CA06
  Escenario: CA06 El administrador activa a un cliente pendiente de revisión
    Dado que existe un cliente pendiente de revisión
    Cuando el administrador cambia el estado del cliente a "ACTIVE"
    Entonces el cambio de estado es aceptado
    Y el estado del cliente es "ACTIVE"
    Y el cliente puede iniciar sesión
