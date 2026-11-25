# language: es
@HU18
Característica: HU18 Realizar consignación
  Como administrador del sistema
  Quiero registrar una consignación de dinero en una cuenta bancaria
  Para acreditar fondos recibidos e incrementar el saldo disponible de la cuenta

  Antecedentes:
    Dado que el administrador inició sesión en el banco

  @CA01
  Escenario: CA01 Consignación exitosa
    Dado que existe un cliente activo con una cuenta activa
    Y la cuenta tiene saldo inicial de 0 pesos
    Cuando el administrador consigna 50000 pesos en la cuenta
    Entonces la consignación es registrada correctamente
    Y recibe un comprobante de consignación
    Y el saldo de la cuenta queda en 50000 pesos

  @CA02
  Escenario: CA02 Cuenta de destino inexistente
    Dado que se tiene un número de cuenta que no existe
    Cuando el administrador intenta consignar 50000 pesos en esa cuenta
    Entonces recibe el rechazo de la consignación
    Y ve un mensaje que indica que la cuenta no existe

  @CA03
  Escenario: CA03 Cuenta de destino no activa
    Dado que existe un cliente activo con una cuenta bloqueada
    Cuando el administrador intenta consignar 50000 pesos en la cuenta bloqueada
    Entonces recibe el rechazo de la consignación
    Y ve un mensaje que indica que la cuenta no permite créditos

  @CA05
  Escenario: CA05 Usuario sin rol de administrador
    Dado que existe un cliente activo con una cuenta activa
    Y el saldo actual de la cuenta es guardado
    Cuando el cliente intenta consignar 50000 pesos en su cuenta
    Entonces recibe el rechazo de la consignación por falta de permisos
    Y el saldo de la cuenta no cambia

  @CA07
  Escenario: CA07 Comprobante único
    Dado que existe un cliente activo con una cuenta activa
    Cuando el administrador realiza dos consignaciones de 10000 pesos
    Entonces los comprobantes de las consignaciones son diferentes