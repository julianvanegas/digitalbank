---
status: accepted
date: 2026-09-10
decision-makers: [Julian Vanegas López, Isaac Cano Orozco, Yuli Karina Jiménez Paredes]
consulted: Equipo de Backend
tags: [modularidad, dominios, dependencias]
---

# ADR-0002 — Dividir el dominio de identidad y fijar reglas de dependencia entre módulos

## Contexto y planteamiento del problema

La primera versión del sistema tenía un único módulo de identidad y acceso que concentraba demasiadas responsabilidades: roles y permisos, emisión y validación de tokens JWT, segundo factor de autenticación, confirmación de correo y recuperación de contraseña y, además, todo el ciclo de vida del cliente: datos personales, tipo de documento, actualización de perfil y cambios de estado.

El resultado era un módulo con dos motivos de cambio independientes mezclados. Un ajuste en la política de tokens tocaba archivos de negocio del cliente, y un cambio en las reglas de perfil obligaba a leer código de seguridad para entender el impacto. Probar cualquiera de las dos cosas por separado era difícil, y no existía ninguna frontera por la que extraer un servicio en el futuro.

Hacía falta definir cómo se dividen los dominios y, sobre todo, cómo pueden hablarse entre sí.

## Factores de decisión

- **Una responsabilidad por módulo**: cada dominio debe tener un único motivo de cambio.
- **Ausencia de ciclos**: un grafo de dependencias dirigido y acíclico es condición para poder extraer servicios después.
- **Superficie de contacto mínima**: cuanto menos expone un módulo, menos cuesta cambiarlo por dentro.
- **Coherencia entre código y esquema**: la dirección de las dependencias debe verse igual en los paquetes y en las tablas.

## Decisión

Se divide el módulo original en `auth` y `customer`, se fija un flujo de dependencias unidireccional entre dominios y se obliga a que toda comunicación entre módulos pase por una fachada explícita. Internamente, cada dominio se estructura en las mismas cuatro capas.

### 1. División de dominios y dirección de las dependencias

Las dependencias fluyen en un solo sentido, de lo más general a lo más específico. Cada módulo conoce únicamente al que tiene inmediatamente por encima:

```
auth  ←  customer  ←  accounts  ←  transactions
```

`auth` gestiona roles y tokens sin conocer al resto de la aplicación; `customer` se apoya en `auth` para validar y crear la identidad del usuario; `accounts` se apoyará en `customer` para vincular cada cuenta a su titular; y `transactions` se apoyará en `accounts` para operar sobre saldos. Ninguna flecha va en sentido contrario.

| Módulo | Responsabilidad | De quién depende | Estado |
| --- | --- | --- | --- |
| `auth` | Identidad de acceso: usuarios, roles, estados, tokens JWT, sesión única, segundo factor, confirmación de correo y recuperación de contraseña. | De nadie | Implementado |
| `customer` | Ciclo de vida del cliente: datos personales, tipo y número de documento, perfil, vigencia y consulta de clientes. | `auth` | Implementado |
| `accounts` | Apertura de cuentas, estados y saldos; vinculación de la cuenta con su titular. | `customer` | Previsto |
| `transactions` | Movimientos de dinero, validación de saldo y aplicación de topes. | `accounts` | Previsto |
| `shared` | Soporte transversal: configuración, manejo uniforme de errores, utilidades de validación, envío de correo y composición de la seguridad. | De ningún dominio | Implementado |

### 2. Toda comunicación entre módulos pasa por una fachada

- **Una única puerta de entrada**: cada dominio publica un paquete `api` con su fachada y sus tipos públicos. Es lo único que los demás módulos pueden importar.
- **Nunca se comparten entidades ni repositorios**: `customer` no importa la entidad `User` ni `UserRepository`. Recibe una vista de solo lectura del usuario (identificador, correo, rol y estado) que deja fuera el hash de la contraseña y cualquier dato de seguridad.
- **Los módulos sólo guardan el identificador ajeno**: `customer` persiste el `user_id`, no una copia de los datos de `auth`.
- **Las fachadas participan en la transacción de quien las llama**: el alta de cliente crea el usuario a través de la fachada de `auth` dentro de la misma transacción, preservando la garantía ACID de la ADR-0001 a través de la frontera del módulo.
- **Lo que no debe bloquear la transacción viaja como evento**: la creación de un usuario publica un evento de dominio y el envío del reto de confirmación de correo ocurre después de confirmarse la transacción, de forma asíncrona.

### 3. `shared` no conoce a los dominios: la seguridad se compone

Un módulo transversal que conociera las rutas de cada dominio invertiría la dirección de las dependencias. En su lugar, `shared` define un contrato que cada módulo implementa para aportar sus rutas públicas y, si lo necesita, su propio mecanismo de seguridad (filtros, política de sesión). La configuración central recoge todas las implementaciones sin saber cuáles existen: añadir un dominio no obliga a tocar `shared`.

### 4. Estructura interna de cada dominio

| Capa | Contenido | Reglas |
| --- | --- | --- |
| Interfaz | Controladores REST para el exterior y la fachada `api` para los demás módulos. | Traduce protocolo a casos de uso. No contiene reglas de negocio. |
| Servicios | Lógica de los casos de uso y orquestación de la transacción. | Es el único lugar donde se declara el alcance transaccional. No conoce HTTP. |
| Dominio | Entidades, objetos de valor, enumeraciones, DTO y mappers. | Concentra las reglas e invariantes del negocio. |
| Infraestructura | Repositorios de persistencia y adaptadores a servicios externos. | Detalle sustituible: se accede a él a través de interfaces. |

Las migraciones respetan la misma dirección que los paquetes: la del módulo `auth` se aplica primero y no referencia a ningún otro módulo; la de `customer` depende de las tablas de `auth`. El orden de las migraciones es, en la práctica, una segunda comprobación de que el grafo de dependencias no tiene ciclos.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
| --- | --- |
| Mantener un único módulo de identidad y acceso | Es el problema que originó esta decisión: dos motivos de cambio mezclados, pruebas acopladas y ninguna frontera aprovechable. |
| Separar por capas técnicas en lugar de por dominios | Reparte cada negocio entre tres paquetes globales y deja el sistema sin fronteras verticales; contradice la ADR-0001. |
| Un módulo por entidad (usuario, rol, sesión, cliente, documento…) | Fragmenta en exceso: multiplica fachadas y mappers para entidades que siempre cambian juntas, sin ganar aislamiento real. |
| Comunicación entre módulos por HTTP interno desde ya | Paga el costo de los microservicios (serialización, latencia, fallos parciales) sin obtener sus beneficios, y rompería la transacción única del alta de cliente. |
| Comunicación por eventos para todas las interacciones | Desacopla, pero rompe la traza lineal de cada caso de uso y saca de la transacción operaciones que deben ser atómicas. Se reserva para lo que sí puede ocurrir después del commit, como el envío de correo. |

## Consecuencias

### A favor

- Cada cambio tiene una ubicación evidente: la seguridad se toca en `auth` y las reglas de perfil en `customer`.
- Cambiar el interior de un módulo no rompe a los demás mientras su fachada se mantenga estable.
- Cada fachada es ya el contrato de un futuro microservicio; la extracción puede hacerse desde las hojas del grafo hacia la raíz.
- La vista de solo lectura del usuario impide que datos sensibles como el hash de la contraseña se filtren a otros módulos.
- Añadir un dominio no obliga a modificar el módulo transversal.

### En contra y riesgos asumidos

- Capa de traducción adicional: cada frontera exige DTO y mappers, con duplicación aparente entre la entidad y la vista que se expone.
- Indirección extra: leer un caso de uso que cruza módulos obliga a pasar por la fachada en lugar de ir directo al repositorio.
- La regla no está verificada automáticamente: hoy sólo la revisión de código impide que alguien importe el repositorio de otro módulo. Queda pendiente automatizarla con pruebas de arquitectura (por ejemplo ArchUnit) o con Spring Modulith.
- Los eventos asíncronos rompen la traza lineal: lo que ocurre tras el commit no forma parte de la transacción, así que sus fallos necesitan su propio tratamiento y reintento.

## Evidencia en el repositorio

| Dónde | Qué muestra |
| --- | --- |
| `auth/api/AuthFacade.java` | Única puerta de entrada a `auth`; su documentación fija la regla de que los demás módulos sólo guardan el identificador del usuario. |
| `auth/api/UserView.java` | Vista de solo lectura del usuario, sin hash ni datos de seguridad. |
| `customer/service/CustomerService.java` | `customer` usa exclusivamente el paquete `api` de `auth`; no importa entidades ni repositorios ajenos. |
| `shared/security/SecurityModule.java` | Contrato por el que cada módulo aporta su seguridad sin que `shared` lo conozca. |
| `customer/security/CustomerSecurity.java` | Implementación del contrato anterior en el módulo `customer`. |
| `auth/service/UserCreatedEvent.java`, `UserCreatedListener.java` | Comunicación asíncrona por eventos tras confirmarse la transacción. |
| `db/migration/V1__auth.sql`, `V2__customers.sql` | Las migraciones documentan la dirección de las dependencias: `auth` no depende de nadie, `customer` depende de `auth`. |
| Historial de Git (commit `a64b4fa`) | «Creación de módulo auth independiente para seguridad y módulo para customers»: el momento en que se aplicó la división. |
