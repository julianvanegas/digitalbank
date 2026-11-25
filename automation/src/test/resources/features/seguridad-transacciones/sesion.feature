# language: es
@HU04
Característica: HU04 Gestionar sesión
  Como cliente autenticado
  Quiero que mi sesión se cree, se mantenga y se cierre de forma segura
  Para que nadie use mi cuenta con una sesión que ya no es mía

  # Autor original: integrante B.

  Antecedentes:
    Dado que el administrador inició sesión en el banco
    Y que existe un cliente activo

  @CA01
  Escenario: CA01 Sesión creada al completar el segundo factor
    Dado que el cliente activo inició sesión y recibió el código 2FA
    Cuando verifica el código correcto de acceso
    Entonces su sesión es válida

  @CA02
  Escenario: CA02 Cerrar sesión manualmente
    Dado el cliente inició sesión con doble factor
    Cuando cierra la sesión
    Entonces el token anterior es rechazado

  # Espera el tiempo de inactividad configurado (5 minutos y 1 segundo por defecto).
  @CA06 @lento
  Escenario: CA06 Cierre automático por más de 5 minutos de inactividad
    Dado el cliente inició sesión con doble factor
    Cuando permanece inactivo hasta superar el tiempo configurado de sesión
    Entonces el token anterior es rechazado

  @CA07
  Escenario: CA07 Token de autenticación vigente
    Dado el cliente inició sesión con doble factor
    Cuando consulta su perfil con su sesión
    Entonces recibe los datos de su propio perfil

  @CA09
  Escenario: CA09 Acceso con sesión cerrada
    Dado el cliente inició sesión con doble factor
    Y cierra la sesión
    Cuando intenta consultar sus cuentas con la sesión anterior
    Entonces recibe el rechazo con código 401
    Y ve un mensaje que contiene "Autenticación requerida"

  @CA11
  Escenario: CA11 Nueva sesión después de cerrar la anterior
    Dado el cliente inició sesión con doble factor
    Y cierra la sesión
    Cuando inicia sesión de nuevo con doble factor
    Entonces su sesión es válida
    Y el token anterior es rechazado
