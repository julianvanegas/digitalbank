# language: es
@HU03
Característica: Verificar identidad mediante segundo factor

  Antecedentes:
    Dado que el administrador inició sesión en el banco

  @CA02
  Escenario: Completar autenticación con un código vigente
    Dado que existe un cliente activo
    Y que el cliente activo inició sesión y recibió el código 2FA
    Cuando verifica el código correcto de acceso
    Entonces el cliente puede acceder a sus cuentas

  @CA05
  Escenario: Invalidar el código después de tres intentos incorrectos
    Dado que existe un cliente activo
    Y que el cliente activo inició sesión y recibió el código 2FA
    Cuando ingresa tres códigos incorrectos y luego intenta verificar de nuevo
    Entonces el código de acceso queda invalidado y no se crea una sesión

  @CA06
  Escenario: Rechazar el segundo uso del código
    Dado que existe un cliente activo
    Y que el cliente activo inició sesión y recibió el código 2FA
    Cuando verifica el código correcto de acceso
    Y intenta utilizar por segunda vez el mismo código de acceso
    Entonces el segundo uso del código es rechazado sin invalidar la sesión ya creada
