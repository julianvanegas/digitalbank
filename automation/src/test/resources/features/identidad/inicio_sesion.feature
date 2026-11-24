# language: es
@HU02
Característica: HU02 Iniciar sesión
  Como cliente registrado
  Quiero iniciar sesión con mi correo y contraseña
  Para acceder de forma segura a mis servicios bancarios

  # Nota: con un cliente en PENDING_VERIFICATION no se puede probar "credenciales correctas" (CA12), porque
  # su contraseña es aleatoria hasta que confirma el correo. Ese caso se prueba con PENDING_REVIEW.

  Antecedentes:
    Dado que el administrador inició sesión en el banco

  @CA01
  Escenario: CA01 Inicio de sesión con credenciales válidas
    Dado que existe un cliente activo
    Cuando inicia sesión con sus credenciales válidas
    Entonces el sistema le solicita el segundo factor

  @CA02
  Escenario: CA02 Contraseña incorrecta
    Dado que existe un cliente activo
    Cuando inicia sesión con una contraseña incorrecta
    Entonces recibe el rechazo con código 401
    Y ve un mensaje que contiene "incorrectos"

  @CA03
  Escenario: CA03 Correo no registrado
    Cuando inicia sesión con un correo no registrado
    Entonces recibe el rechazo con código 401
    Y ve un mensaje que contiene "incorrectos"

  @CA04
  Escenario: CA04 Credenciales válidas después de 3 intentos fallidos
    Dado que existe un cliente activo
    Cuando se equivoca de contraseña 3 veces seguidas
    Y inicia sesión con sus credenciales válidas
    Entonces el sistema le solicita el segundo factor

  @CA05
  Escenario: CA05 Bloqueo al cuarto intento fallido
    Dado que existe un cliente activo
    Cuando se equivoca de contraseña 4 veces seguidas
    Entonces el estado del cliente es "BLOCKED"
    Y no puede iniciar sesión aunque use la contraseña correcta

  @CA06
  Escenario: CA06 Cliente bloqueado no puede iniciar sesión
    Dado que existe un cliente bloqueado
    Cuando inicia sesión con sus credenciales válidas
    Entonces recibe el rechazo con código 403
    Y ve un mensaje que contiene "bloqueado"

  @CA07
  Escenario: CA07 Solo se permite una sesión abierta por usuario
    Dado que existe un cliente activo
    Y que el cliente tiene una sesión iniciada
    Cuando inicia sesión nuevamente desde otro dispositivo
    Entonces la sesión anterior es rechazada
    Y la nueva sesión sigue vigente

  @CA11
  Escenario: CA11 El mensaje no revela si el correo existe
    Dado que existe un cliente activo
    Cuando inicia sesión con una contraseña incorrecta
    Y guarda el mensaje recibido
    Y inicia sesión con un correo no registrado
    Entonces recibe el rechazo con código 401
    Y el mensaje recibido es el mismo que el anterior

  @CA12
  Escenario: CA12 Cliente pendiente de revisión no puede iniciar sesión
    Dado que existe un cliente pendiente de revisión
    Cuando inicia sesión con sus credenciales válidas
    Entonces recibe el rechazo con código 403
    Y ve un mensaje que contiene "revisión"
