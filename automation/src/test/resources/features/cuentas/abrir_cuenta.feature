# language: es
@HU10
Característica: HU10 Abrir cuenta de ahorros
  Como cliente registrado
  Quiero abrir una cuenta de ahorros
  Para disponer de un producto financiero desde el cual administrar mi dinero

  Antecedentes:
    Dado que el administrador inició sesión en el banco

  @CA01
  Escenario: CA01 Apertura exitosa de una cuenta de ahorros
    Dado que existe un cliente activo
    Y que el cliente tiene menos de 3 cuentas no cerradas
    Cuando solicita abrir una nueva cuenta de ahorros
    Entonces la cuenta es creada correctamente
    Y el número de cuenta tiene 11 dígitos
    Y la cuenta tiene una llave única

  @CA03
  Escenario: CA03 Cliente con el máximo de cuentas permitido
    Dado que existe un cliente activo
    Y que el cliente ya tiene 3 cuentas no cerradas
    Cuando solicita abrir una cuenta adicional
    Entonces recibe el rechazo de la apertura
    Y ve un mensaje que indica que alcanzó el máximo de cuentas

  @CA05
  Escenario: CA05 Número de cuenta válido
    Dado que existe un cliente activo
    Cuando solicita abrir una nueva cuenta de ahorros
    Entonces el número de cuenta tiene exactamente 11 dígitos numéricos

  @CA06
  Escenario: CA06 Identificación única
    Dado que existe un cliente activo
    Cuando abre dos cuentas de ahorros para validar su unicidad
    Entonces los números de cuenta son diferentes
    Y las llaves de las cuentas son diferentes

  @CA07
  Escenario: CA07 Cliente que no está Activo
    Dado que existe un cliente que no está activo
    Cuando solicita abrir una nueva cuenta de ahorros
    Entonces recibe el rechazo de la apertura
    Y no obtiene una cuenta nueva