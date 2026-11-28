# language: es
@HU10
Característica: HU10 Abrir cuenta de ahorros
  Como cliente registrado
  Quiero abrir una cuenta de ahorros
  Para disponer de un producto financiero desde el cual administrar mi dinero

  # Autor original: integrante C. CA01 y CA05 fallan por un defecto conocido: la historia exige un número de cuenta de
  # 11 dígitos y el backend genera 12. La llave también difiere: la historia la pide en minúsculas y el backend usa
  # mayúsculas (CA01, se ve cuando se corrija el número).

  Antecedentes:
    Dado que el administrador inició sesión en el banco

  @CA01
  Escenario: CA01 Apertura exitosa de una cuenta de ahorros
    Dado que existe un cliente activo
    Y el cliente inició sesión con doble factor
    Cuando solicita abrir una nueva cuenta de ahorros
    Entonces la cuenta es creada activa, de ahorros y con saldo cero
    Y el número de cuenta tiene exactamente 11 dígitos numéricos
    Y la llave de la primera cuenta sigue el formato de la historia

  @CA03
  Escenario: CA03 Cliente con el máximo de cuentas permitido
    Dado que existe un cliente activo
    Y el cliente inició sesión con doble factor
    Y que el cliente ya tiene 3 cuentas no cerradas
    Cuando solicita abrir una nueva cuenta de ahorros
    Entonces recibe el rechazo con código 409
    Y ve un mensaje que contiene "número máximo permitido"

  @CA05
  Escenario: CA05 Número de cuenta válido
    Dado que existe un cliente activo
    Y el cliente inició sesión con doble factor
    Cuando solicita abrir una nueva cuenta de ahorros
    Entonces el número de cuenta tiene exactamente 11 dígitos numéricos

  @CA06
  Escenario: CA06 Identificación única
    Dado que existe un cliente activo
    Y el cliente inició sesión con doble factor
    Cuando abre dos cuentas de ahorros para validar su unicidad
    Entonces los números de cuenta son diferentes
    Y las llaves de las cuentas son diferentes

  # Al bloquear al cliente, el backend cierra su sesión: el rechazo llega como 401 antes de llegar a la apertura.
  @CA07
  Escenario: CA07 Cliente que no está Activo
    Dado que existe un cliente que no está activo
    Cuando solicita abrir una nueva cuenta de ahorros
    Entonces recibe el rechazo con código 401
    Y ve un mensaje que contiene "Autenticación requerida"
    Y el cliente no tiene cuentas
