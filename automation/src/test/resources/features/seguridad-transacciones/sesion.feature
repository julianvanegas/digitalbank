# language: es
@HU04
Característica: Gestionar la sesión autenticada

  Antecedentes:
    Dado que el administrador inició sesión en el banco
    Y que existe un cliente activo
    Y que el cliente activo completó la autenticación de doble factor

  @CA01
  Escenario: Rechazar el token después del cierre manual de sesión
    Cuando cierra la sesión
    Entonces el token anterior es rechazado

  @CA02 @lento
  Escenario: Rechazar el token después del período de inactividad
    Cuando permanece inactivo hasta superar el tiempo configurado de sesión
    Entonces el token anterior es rechazado
