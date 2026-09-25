---
status: accepted
date: 2026-09-17
decision-makers: [Julian Vanegas López, Isaac Cano Orozco, Yuli Karina Jiménez Paredes]
consulted: Equipo de Backend
tags: [infraestructura, base-de-datos, despliegue]
---

# ADR-0003 — Usar Neon como plataforma gestionada de PostgreSQL

## Contexto y planteamiento del problema

En desarrollo la base de datos se levanta en local con Docker Compose sobre una imagen de PostgreSQL 15. Para desplegar el backend hace falta una instancia gestionada, y la elección se redujo a dos plataformas: Neon y Supabase.

La aplicación no habla con un servicio: habla con PostgreSQL directamente, por JDBC, con Hibernate configurado sobre el dialecto de PostgreSQL, con Flyway aplicando las migraciones al arrancar y con el esquema verificado en modo `validate`. Además, las decisiones anteriores descansan sobre garantías del motor: la ADR-0001 apoya la atomicidad de las operaciones de dinero en la transacción local, y la ADR-0002 apoya invariantes como «un usuario ADMIN no puede tener perfil de cliente» en claves foráneas compuestas y restricciones `CHECK`.

La plataforma elegida debe ser PostgreSQL real y no un motor meramente compatible.

## Factores de decisión

- **ACID estricto**: cumplimiento riguroso de atomicidad, consistencia, aislamiento y durabilidad, con niveles de aislamiento configurables para transferencias y aplicación de topes.
- **Compatibilidad sin cambios en la aplicación**: debe bastar con cambiar las variables de entorno de conexión; el driver JDBC, Flyway, las claves foráneas compuestas y los `CHECK` deben funcionar tal cual.
- **Modo de agrupación de conexiones**: el agrupador debe ser compatible con transacciones que abarcan varias sentencias y con las sentencias preparadas de JDBC.
- **Respaldo y recuperación**: copias automáticas y recuperación a un punto en el tiempo, requisito no negociable en un sistema financiero.
- **Latencia y región**: cercanía a la región donde se despliega el backend.
- **Costo en la etapa de MVP**: que la capa gratuita o inicial cubra el proyecto sin límites que obliguen a rediseñar.
- **Servicios adicionales que no dupliquen lo ya construido**: el proyecto ya implementa su propia autenticación, por lo que un servicio de identidad externo aportaría poco y podría inducir a duplicar responsabilidades.

## Decisión

Se elige **Neon** como plataforma gestionada de PostgreSQL para el despliegue, con respaldos automáticos y recuperación a un punto en el tiempo. Se descarta Supabase.

La aplicación no incorpora ningún SDK propietario de la plataforma: se conecta únicamente por JDBC a partir de las variables de entorno de conexión, de modo que Neon queda como un detalle de infraestructura y no como una dependencia del código.

### Por qué Neon y no Supabase

Las dos plataformas ofrecen PostgreSQL real y, por tanto, las mismas garantías ACID, así que el criterio decisivo no fue el motor sino lo que rodea al motor.

El grueso del valor diferencial de Supabase está en autenticación, almacenamiento y tiempo real. Este proyecto ya resolvió la autenticación por su cuenta, con tokens JWT, sesión única, segundo factor y confirmación de correo, ajustados a las reglas del documento de alcance. Adoptar Supabase habría dejado esa parte de la plataforma sin usar y habría creado una ambigüedad indeseable sobre dónde vive la identidad, justo lo que la ADR-0002 trabajó para evitar.

Neon, en cambio, aporta algo que el equipo sí usa de inmediato: una copia de la base de datos por rama de trabajo, que permite validar cada migración de Flyway antes de integrarla sin tocar la base compartida.

La decisión se reconsiderará si aparece la necesidad de almacenar archivos (por ejemplo, soportes de documentos de identidad) o de enviar notificaciones en tiempo real: en ese escenario, tener esas piezas integradas en Supabase pesaría más que la ramificación de la base de datos, y correspondería abrir una nueva ADR que reemplace a esta.

## Alternativas consideradas

| Criterio | Neon (elegida) | Supabase (descartada) |
| --- | --- | --- |
| Motor | PostgreSQL nativo, sin capa intermedia. | PostgreSQL nativo, sin capa intermedia. |
| Garantías ACID | Completas, las del propio motor. | Completas, las del propio motor. |
| Compatibilidad con JDBC, Hibernate y Flyway | Directa; sólo requiere conexión cifrada. | Directa; sólo requiere conexión cifrada. |
| Enfoque del producto | Base de datos y nada más: cómputo separado del almacenamiento, escalado a cero y ramificación de la base de datos. | Plataforma completa: base de datos más autenticación, almacenamiento de archivos, tiempo real y API generada automáticamente. |
| Ventaja específica para este proyecto | La ramificación permite crear una copia de la base por rama de trabajo y probar allí las migraciones de Flyway antes de integrar. | Si más adelante se necesitan archivos o notificaciones en tiempo real, ya vienen incluidos y no hay que integrar otro proveedor. |
| Solapamiento con lo ya construido | Ninguno. | Su servicio de autenticación y su seguridad a nivel de fila se solapan con el módulo `auth` y con la autorización ya implementada. |
| Puntos de atención | El escalado a cero introduce un arranque en frío en la primera conexión tras un periodo de inactividad; hay que verificar el modo del agrupador de conexiones frente a las sentencias preparadas. | Conviene distinguir el puerto de conexión directa del puerto agrupado: el modo de agrupación por transacción no admite sentencias preparadas de sesión. |

## Acciones derivadas de la decisión

La decisión queda tomada, pero su puesta en operación exige verificar en Neon los siguientes puntos antes de considerar el entorno de despliegue listo para uso real:

- [ ] Crear el proyecto en la región más cercana a la del backend y medir la latencia de ida y vuelta desde la aplicación.
- [ ] Verificar el modo del agrupador de conexiones con una transacción de varias sentencias y con sentencias preparadas de JDBC, que es el patrón real de la aplicación.
- [ ] Medir el arranque en frío tras un periodo de inactividad y decidir si el escalado a cero es aceptable o si conviene mantener la instancia activa.
- [ ] Confirmar la política de respaldos y la ventana de recuperación a un punto en el tiempo del plan contratado.
- [ ] Comprobar los límites del plan (conexiones simultáneas, almacenamiento, horas de cómputo) frente a la carga esperada del MVP y dimensionar el pool de la aplicación en consecuencia.
- [ ] Ejecutar las migraciones `V1` y `V2` sobre Neon y confirmar que el arranque pasa la validación del esquema.
- [ ] Definir el uso de ramas de base de datos en el flujo de trabajo: una rama por pull request para probar allí las migraciones de Flyway antes de integrar.

## Consecuencias

### A favor

- Las garantías sobre las que descansan la ADR-0001 y la ADR-0002 se mantienen intactas: Neon es PostgreSQL real, con transacciones ACID, claves foráneas compuestas y restricciones `CHECK`.
- Las migraciones de Flyway pueden probarse en una copia de la base por rama de trabajo, sin tocar la base compartida ni depender de datos de desarrollo desactualizados.
- Al no usar ningún SDK propietario, la decisión es reversible: migrar a otra plataforma consiste en un volcado, una restauración y un cambio de variables de entorno.
- El equipo se ahorra administrar copias de seguridad, parches y alta disponibilidad.
- No se introduce ningún servicio que solape al módulo `auth`: la identidad sigue viviendo en un solo lugar.

### En contra y riesgos asumidos

- Dependencia de un proveedor externo: la disponibilidad de Neon pasa a ser parte de la disponibilidad del sistema.
- Arranque en frío por el escalado a cero: tras un periodo de inactividad, la primera conexión paga un retardo adicional que puede notarse en el primer acceso del día o en una demostración.
- Comportamiento del agrupador de conexiones: el modo agrupado impone restricciones a las sentencias preparadas de sesión, por lo que hay que elegir conscientemente entre la cadena de conexión directa y la agrupada.
- Latencia de red: la base deja de estar en el mismo host que la aplicación; hay que desplegar ambos en la misma región.
- Límite de conexiones simultáneas: los planes gestionados suelen ser más restrictivos que una instancia propia, lo que obliga a dimensionar el pool de la aplicación.

## Evidencia en el repositorio

| Dónde | Qué muestra |
| --- | --- |
| `application.properties` | La conexión se arma con variables de entorno (URL JDBC, usuario y contraseña); cambiar de plataforma no toca el código. |
| `example.env` | Las únicas variables de base de datos son host, nombre, usuario y contraseña: no hay nada atado a un proveedor. |
| `pom.xml` | Driver de PostgreSQL y Flyway para PostgreSQL: la aplicación depende del motor, no de una plataforma. |
| `docker-compose.yaml` | Entorno local con PostgreSQL 15, equivalente al que debe ofrecer la plataforma gestionada. |
| `db/migration/` | Migraciones que deben ejecutarse íntegras en Neon, incluidas las claves foráneas compuestas y las restricciones `CHECK`. |
