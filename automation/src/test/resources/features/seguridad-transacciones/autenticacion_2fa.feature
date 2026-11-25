# language: es
@HU03
Característica: HU03 Verificar identidad mediante 2FA
  Como cliente registrado
  Quiero verificar mi identidad con un código enviado a mi correo
  Para que nadie más pueda entrar con mi contraseña

  # Autor original: integrante B.

  Antecedentes:
    Dado que el administrador inició sesión en el banco
    Y que existe un cliente activo
    Y que el cliente activo inició sesión y recibió el código 2FA

  @CA02
  Escenario: CA02 Código válido
    Cuando verifica el código correcto de acceso
    Entonces el cliente puede acceder a sus cuentas

  @CA05
  Escenario: CA05 Código incorrecto dentro del límite de intentos
    Cuando ingresa un código incorrecto de acceso
    Entonces el código de acceso es rechazado y no se crea una sesión

  # Si el código quedó invalidado, ni siquiera el correcto sirve después del tercer error.
  @CA06
  Escenario: CA06 El tercer intento incorrecto invalida el código
    Cuando ingresa tres códigos incorrectos de acceso
    Y intenta verificar con el código correcto
    Entonces el código de acceso es rechazado y no se crea una sesión

  @CA09
  Escenario: CA09 Reutilización de un código ya usado
    Cuando verifica el código correcto de acceso
    Y intenta utilizar por segunda vez el mismo código de acceso
    Entonces el segundo uso del código es rechazado sin invalidar la sesión ya creada

  @CA13
  Escenario: CA13 No conceder acceso sin completar 2FA
    Cuando intenta consultar su perfil sin completar el segundo factor
    Entonces recibe el rechazo con código 401
    Y ve un mensaje que contiene "Autenticación requerida"
