# language: es
@HU01
Característica: HU01 Registrar cliente
  Como usuario
  Quiero registrarme en la plataforma con mis datos personales
  Para crear mi perfil de cliente del banco digital

  # Nota: en el backend la contraseña no viaja en el registro; se define al confirmar el correo
  # (POST /auth/verify-email). Por eso las reglas de contraseña (CA09 y CA10) se verifican en ese paso.

  @CA01
  Escenario: CA01 Registro exitoso
    Dado que un usuario mayor de edad con documento y correo no registrados
    Cuando solicita registrarse
    Entonces recibe el registro aceptado con código 201
    Y el cliente queda en estado "PENDING_VERIFICATION"

  @CA02
  Escenario: CA02 Registro aceptado para quien cumple 18 años el día del registro
    Dado que un usuario cumple exactamente 18 años el día del registro
    Cuando solicita registrarse
    Entonces recibe el registro aceptado con código 201
    Y el cliente queda en estado "PENDING_VERIFICATION"

  @CA03
  Escenario: CA03 Registro rechazado para un menor de 18 años
    Dado que el administrador inició sesión en el banco
    Y un usuario menor de 18 años
    Cuando solicita registrarse
    Entonces recibe el rechazo con código 400
    Y ve un mensaje que contiene "18 años"
    Y el administrador no encuentra al cliente registrado

  @CA05
  Escenario: CA05 Registro rechazado por documento duplicado
    Dado que ya existe un cliente registrado
    Y un usuario nuevo intenta registrarse con el mismo número de documento
    Cuando solicita registrarse
    Entonces recibe el rechazo con código 409
    Y ve un mensaje que contiene "número de documento"

  @CA07
  Escenario: CA07 Registro rechazado por correo duplicado
    Dado que ya existe un cliente registrado
    Y un usuario nuevo intenta registrarse con el mismo correo
    Cuando solicita registrarse
    Entonces recibe el rechazo con código 409
    Y ve un mensaje que contiene "email"

  @CA08
  Esquema del escenario: CA08 Contraseña válida en los límites de longitud
    Dado que el administrador inició sesión en el banco
    Y que un cliente se registró y tiene su correo pendiente de confirmar
    Cuando confirma su correo con la contraseña "<contraseña>"
    Entonces la confirmación del correo es aceptada
    Y el estado del cliente es "PENDING_REVIEW"

    Ejemplos:
      | contraseña       | caso                        |
      | Abcd12#x         | 8 caracteres (mínimo)       |
      | Abcdefghij12#xyz | 16 caracteres (máximo)      |

  @CA09
  Esquema del escenario: CA09 Contraseña con longitud inválida
    Dado que un cliente se registró y tiene su correo pendiente de confirmar
    Cuando confirma su correo con la contraseña "<contraseña>"
    Entonces recibe el rechazo con código 400
    Y ve un mensaje que contiene "8-16 caracteres"

    Ejemplos:
      | contraseña          | caso                    |
      | Ab1@xyz             | 7 caracteres (muy corta) |
      | Abcdefghij1@klmno   | 17 caracteres (límite superior + 1) |
      | Abcdefghij1@klmnopq | 19 caracteres (muy larga) |

  @CA10
  Esquema del escenario: CA10 Contraseña sin todos los tipos de carácter exigidos
    Dado que un cliente se registró y tiene su correo pendiente de confirmar
    Cuando confirma su correo con la contraseña "<contraseña>"
    Entonces recibe el rechazo con código 400
    Y ve un mensaje que contiene "mayúscula, minúscula, número y carácter especial"

    Ejemplos:
      | contraseña   | caso              |
      | segura#2026  | sin mayúscula     |
      | SEGURA#2026  | sin minúscula     |
      | Segura#Clave | sin número        |
      | Segura2026a  | sin especial      |

  @CA11
  Esquema del escenario: CA11 Registro rechazado cuando falta un dato obligatorio
    Dado que un usuario mayor de edad con documento y correo no registrados
    Cuando solicita registrarse sin el dato "<dato>"
    Entonces recibe el rechazo con código 400
    Y ve un mensaje que contiene "<campo>"

    Ejemplos:
      | dato                | campo          |
      | nombres             | firstNames     |
      | apellidos           | lastNames      |
      | tipo de documento   | documentTypeId |
      | número de documento | documentNumber |
      | teléfono            | phone          |
      | fecha de nacimiento | birthDate      |
      | correo              | email          |
