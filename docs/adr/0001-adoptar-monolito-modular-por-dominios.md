---
status: accepted
date: 2026-09-05
decision-makers: [Julian Vanegas López, Isaac Cano Orozco, Yuli Karina Jiménez Paredes]
consulted: Equipo de Backend
tags: [arquitectura, estilo, transaccionalidad]
---

# ADR-0001 — Adoptar un monolito modular organizado por dominios

## Contexto y planteamiento del problema

DigitalBank debe entregar un MVP funcional de banca digital con un equipo reducido y un alcance acotado. El sistema maneja dinero: una transferencia debe debitar una cuenta y acreditar otra, o no ocurrir en absoluto; un retiro debe validar saldo y topes y descontar el importe de forma atómica. No existe una lectura aceptable en la que el dinero quede a medio camino.

Al mismo tiempo, el equipo no tiene capacidad operativa para desplegar, observar y versionar varios servicios independientes, y el modelo de dominio todavía está cambiando: en esta etapa el costo de mover una frontera entre dominios debe ser bajo.

La decisión que había que tomar era qué estilo arquitectónico adoptar como base, sabiendo que el sistema debe poder crecer después.

## Factores de decisión

- **Consistencia transaccional**: las operaciones de dinero exigen garantías ACID, no consistencia eventual.
- **Costo de operación del MVP**: una sola unidad desplegable y una sola base de datos que operar.
- **Velocidad de entrega**: la arquitectura no debe imponer trabajo de infraestructura que no aporte valor al MVP.
- **Capacidad de evolución**: poder extraer servicios más adelante sin una refactorización extensa.
- **Testabilidad**: la lógica de negocio debe poder probarse sin levantar el framework web ni la base de datos.

## Decisión

Se adopta un **monolito modular organizado por dominios**. El sistema se despliega como un único artefacto Spring Boot sobre Java 21, con una única base de datos PostgreSQL y un único gestor transaccional. Dentro de ese artefacto, cada dominio es un módulo autónomo estructurado en capas siguiendo los principios de la arquitectura limpia o hexagonal: las dependencias apuntan hacia el dominio y nunca al revés.

### Qué implica en concreto

- **Un desplegable, un paquete raíz por dominio**: `com.udea.digitalbank` contiene `auth`, `customer` y `shared`; cada dominio futuro añade su propio paquete raíz.
- **La transacción es de proceso, no distribuida**: `@Transactional` abarca el caso de uso completo aunque cruce módulos. El alta de un cliente crea el usuario y su perfil en una sola transacción, de modo que si el perfil falla el usuario se revierte y no quedan usuarios sin perfil ni perfiles huérfanos.
- **Las invariantes críticas viven también en el esquema**: la base de datos no confía en el código. Una clave foránea compuesta sobre `(id, role_id)` más una restricción `CHECK` impiden que un usuario ADMIN tenga fila de cliente, y un `CHECK` sobre el correo obliga a guardarlo siempre en minúsculas.
- **El esquema se versiona con migraciones**: Flyway es la única fuente de verdad del esquema y Hibernate se configura en modo `validate`, de forma que el arranque falla si el código y la base de datos se desincronizan.
- **Las capas aíslan el dominio**: controladores y repositorios son adaptadores; los servicios y las entidades no conocen HTTP.

## Alternativas consideradas

| Alternativa | Por qué se descartó |
| --- | --- |
| Microservicios desde el inicio | Mover dinero entre servicios obliga a sagas y compensaciones en lugar de transacciones ACID, y exige gateway, descubrimiento de servicios, trazabilidad distribuida y una cadena de CI/CD por servicio. Es sobreingeniería para un MVP y añade el riesgo funcional de la consistencia eventual justo donde menos se tolera. |
| Monolito en capas técnicas (controllers / services / repositories globales) | Agrupa por tecnología en vez de por dominio: la lógica de un mismo negocio queda repartida en tres paquetes y todo termina acoplado con todo. No deja fronteras por las que cortar si después hay que extraer un servicio. |
| Arquitectura hexagonal estricta con puertos y adaptadores en cada módulo | El nivel de indirección que exige no se justifica con un solo adaptador de persistencia y uno de entrada. Se toma el principio de aislar el dominio de la infraestructura, sin la ceremonia completa. |
| Arquitectura serverless por función | Reintroduce el problema de la transacción distribuida, complica el manejo del pool de conexiones a PostgreSQL y los arranques en frío castigan la latencia de operaciones que el usuario percibe como inmediatas. |

## Consecuencias

### A favor

- Las transferencias, los retiros y la aplicación de topes se resuelven con una transacción local ACID, sin coordinación distribuida.
- Un solo despliegue, un solo conjunto de credenciales y un solo log que revisar: la operación del MVP es asumible por el equipo.
- Reproducir y depurar el sistema completo en local es cuestión de un `docker compose up`.
- Mientras el modelo se estabiliza, mover una frontera entre dominios es un refactor de paquetes, no un cambio de contratos entre servicios.
- Los límites de módulo son candidatos directos a límites de servicio: la migración futura es una extracción, no una reescritura.

### En contra y riesgos asumidos

- El aislamiento depende de disciplina, no de la red: nada impide técnicamente que un módulo importe el repositorio de otro. Se mitiga con las reglas de la ADR-0002 y su verificación en revisión de código.
- Radio de impacto compartido: un fallo grave o un despliegue defectuoso afecta a todo el sistema, no a un servicio.
- Escalado de grano grueso: sólo se puede escalar el monolito completo, aunque la carga se concentre en un dominio.
- La base de datos compartida puede convertirse en acoplamiento oculto: si un módulo consulta tablas de otro, la frontera desaparece sin que nadie lo note. Queda prohibido y se revisa en cada PR.

## Evidencia en el repositorio

| Dónde | Qué muestra |
| --- | --- |
| `pom.xml` | Un único artefacto: Spring Boot 4.0.8 sobre Java 21, con Spring Web MVC, Data JPA, Security, Validation, Flyway y PostgreSQL. |
| `Dockerfile`, `docker-compose.yaml` | Un solo servicio backend junto a un único contenedor de PostgreSQL 15. |
| `src/main/resources/application.properties` | Gestión transaccional centralizada (tiempo límite de 120 s, reversión ante fallo de commit) y `spring.jpa.hibernate.ddl-auto=validate`. |
| `src/main/resources/db/migration/` | Migraciones Flyway `V1__auth.sql` y `V2__customers.sql` como fuente de verdad del esquema. |
| `customer/service/CustomerService.java` | Caso de uso transaccional que atraviesa dos módulos en una sola transacción. |
| `src/main/java/com/udea/digitalbank/` | Paquetes raíz por dominio (`auth`, `customer`) más `shared`, en lugar de paquetes por capa técnica. |
