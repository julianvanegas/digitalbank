# language: es
@HU18
Característica: HU18 Realizar consignación
  Como administrador del sistema
  Quiero registrar una consignación de dinero en una cuenta bancaria
  Para acreditar fondos recibidos e incrementar el saldo disponible de la cuenta

  # Autor original: integrante C.

  Antecedentes:
    Dado que el administrador inició sesión en el banco

  @CA01
  Escenario: CA01 Consignación exitosa
    Dado que existe un cliente activo
    Y el cliente inició sesión con doble factor
    Y tiene una cuenta con saldo de 0 pesos
    Cuando el administrador consigna 50000 pesos en la cuenta de origen
    Entonces recibe el comprobante de la consignación por 50000 pesos
    Y el saldo de la cuenta de origen es 50000 pesos

  @CA02
  Escenario: CA02 Cuenta de destino inexistente
    Cuando el administrador consigna 50000 pesos en una cuenta que no existe
    Entonces recibe el rechazo con código 400
    Y ve un mensaje que contiene "La cuenta no existe"

  @CA03
  Escenario: CA03 Cuenta de destino no activa
    Dado que existe un cliente activo
    Y el cliente inició sesión con doble factor
    Y tiene una cuenta con saldo de 0 pesos
    Y el administrador bloquea la cuenta de origen
    Cuando el administrador consigna 50000 pesos en la cuenta de origen
    Entonces recibe el rechazo con código 400
    Y ve un mensaje que contiene "La cuenta no permite créditos"
    Y el saldo de la cuenta de origen es 0 pesos

  @CA05
  Escenario: CA05 Usuario sin rol de administrador
    Dado que existe un cliente activo
    Y el cliente inició sesión con doble factor
    Y tiene una cuenta con saldo de 0 pesos
    Cuando el cliente intenta consignar 50000 pesos en su cuenta
    Entonces recibe el rechazo con código 403
    Y ve un mensaje que contiene "No tienes permiso"
    Y el saldo de la cuenta de origen es 0 pesos

  @CA07
  Escenario: CA07 Comprobante único
    Dado que existe un cliente activo
    Y el cliente inició sesión con doble factor
    Y tiene una cuenta con saldo de 0 pesos
    Cuando el administrador realiza dos consignaciones de 10000 pesos
    Entonces los comprobantes de las consignaciones son diferentes
    Y el saldo de la cuenta de origen es 20000 pesos
