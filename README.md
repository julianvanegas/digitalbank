# DigitalBank

[![Latest Release](https://img.shields.io/github/v/release/julianvanegas/digitalbank?sort=semver)](https://github.com/julianvanegas/digitalbank/releases/latest)
[![GHCR Package](https://img.shields.io/badge/GHCR-digitalbank-2496ED?logo=docker&logoColor=white)](https://github.com/julianvanegas/digitalbank/pkgs/container/digitalbank)

DigitalBank es una API stateless para gestionar clientes, cuentas y transacciones financieras de clientes y empleados con trazabilidad.

[![CI Pipeline](https://github.com/julianvanegas/digitalbank/actions/workflows/build.yml/badge.svg)](https://github.com/julianvanegas/digitalbank/actions/workflows/build.yml)
[![CodeQL](https://github.com/julianvanegas/digitalbank/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/julianvanegas/digitalbank/actions/workflows/github-code-scanning/codeql)
[![Quality gate status](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=coverage)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)

[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Security issues](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=software_quality_security_issues)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Maintainability issues](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=software_quality_maintainability_issues)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Reliability issues](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=software_quality_reliability_issues)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Lines of code](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)

## Contenido

- [Funcionalidades](#funcionalidades)
- [Tecnologías](#tecnologías)
- [Arquitectura](#arquitectura)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Requisitos previos](#requisitos-previos)
- [Configuración](#configuración)
- [Ejecución local](#ejecución-local)
- [Documentación de la API](#documentación-de-la-api)
- [Pruebas y calidad](#pruebas-y-calidad)
- [Observabilidad](#observabilidad)
- [Despliegue mediante contenedor](#despliegue-mediante-contenedor)
- [CI/CD](#cicd)
- [Contribuir](#contribuir)
- [Seguridad](#seguridad)
- [Licencia](#licencia)

## Funcionalidades

| Módulo | Funcionalidades |
| --- | --- |
| `auth` | Registro, inicio de sesión, segundo factor de autenticación (2FA), recuperación de contraseña, cierre de sesión por inactividad. |
| `customer` | Alta, consulta y actualización del perfil del cliente; consulta por documento. |
| `employee` | Gestión de empleados. |
| `accounts` | Apertura de cuentas, consulta de saldos, bloqueo, desbloqueo y cierre. |
| `transactions` | Transferencias y retiros en dos pasos (iniciar y confirmar con segundo factor), depósitos, consulta de transacciones por referencia y consulta de topes transaccionales. |

## Tecnologías

[![Java 25](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/technologies/javase/jdk25-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)

- Java 25 y Spring Boot 4.1.2 con Web MVC, Data JPA, Security, Validation, Actuator y Flyway.
- PostgreSQL como sistema gestor de base de datos, con migraciones versionadas mediante Flyway.
- JSON Web Tokens (JWT) con JJWT 0.13.0 para autenticación y autorización.
- MapStruct 1.6.3 y Lombok para el mapeo entre entidades y DTOs y la reducción de código repetitivo.
- springdoc-openapi 3.2.0 para generar la especificación OpenAPI y publicar Swagger UI.
- Resend Java 4.30.0 para el envío de notificaciones por correo electrónico.
- JUnit y Spring Boot Test para pruebas automatizadas.
- JaCoCo 0.8.16 para cobertura de código, con un umbral mínimo del 80%, y SonarCloud para análisis estático y calidad.
- Maven Wrapper para la compilación reproducible.
- Containerfile para empaquetar y ejecutar la aplicación dentro de contenedores según los estándares de la Open Container Initiative (OCI).

## Arquitectura

DigitalBank es un monolito modular organizado por dominios bajo la estrategia Domain-Driven Design (DDD). Cada módulo vive bajo `com.udea.digitalbank.<modulo>` siguiendo la misma estructura interna (`api`, `controller`, `domain`, `dto`, `mapper`, `repository`, `service`) y los módulos solo se comunican a través del paquete `api` de cada uno.

Las reglas de dependencia y el razonamiento detrás de cada decisión están documentados en los [Architecture Decision Record (ADR)](docs/adr/README.md).

## Estructura del repositorio

```
.
├── .github/              Workflows de CI/CD, plantilla de PR y CODEOWNERS
├── docs/adr/             Registros de decisiones de arquitectura
├── observability/        Prometheus y Grafana
├── src/main/java/com/udea/digitalbank/
│   ├── accounts/         Cuentas bancarias
│   ├── auth/             Identidad, sesiones y verificación
│   ├── customer/         Clientes
│   ├── employee/         Empleados
│   ├── transactions/     Transferencias
│   └── shared/           Seguridad, excepciones, OpenAPI y utilidades transversales
├── src/main/resources/
│   └── db/migration/     Migraciones Flyway
├── src/test/java/        Pruebas unitarias (*Test) y de integración (*IT) por módulo
├── Containerfile         Imagen OCI de la aplicación
└── .env.example          Plantilla de configuración
```

## Requisitos previos

- Git para clonar el repositorio.
- JDK 25 instalado y disponible en el `PATH`.
- PostgreSQL 14 o superior, en ejecución y con permisos para crear la base de datos `bank`.
- Una cuenta de [Resend](https://resend.com) y una API key válida para enviar notificaciones por correo.
- Docker o Podman, para las pruebas de integración (`./mvnw verify`) y para construir la imagen del contenedor.
- No necesitas instalar Maven, el repositorio incluye el Maven Wrapper (`mvnw`).

## Configuración

Toda la configuración llega por variables de entorno (ver [`.env.example`](.env.example)):

| Variable | Descripción |
| --- | --- |
| `SERVER_PORT` | Puerto HTTP |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos, separados por comas |
| `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Conexión a PostgreSQL |
| `DB_POOL_SIZE` | Máximo de conexiones simultáneas |
| `FLYWAY_ENABLED` | Activa las migraciones |
| `JWT_SECRET` | Secreto de firma de los JWT (mínimo 32 caracteres) |
| `JWT_EXPIRATION_MS` | Vigencia máxima del token |
| `SESSION_INACTIVITY_TIMEOUT_MINUTES` | Minutos de inactividad antes de cerrar la sesión |
| `VERIFICATION_HMAC_SECRET` | Clave HMAC con la que se almacenan los códigos 2FA (mínimo 32 caracteres) |
| `VERIFICATION_RESEND_INTERVAL_SECONDS` | Intervalo mínimo entre reenvíos de código |
| `ACCOUNT_NUMBER_SECRET` | Secreto que reparte la secuencia de números de cuenta (mínimo 32 caracteres) |
| `RESEND_API_KEY`, `RESEND_FROM` | Servicio de correo |
| `SWAGGER_ENABLED` | Publica la documentación de la API |
| `METRICS_USER`, `METRICS_PASSWORD` | Credenciales Basic con que se leen las métricas en `/actuator/prometheus`; vacías, el endpoint responde siempre 401 |

## Ejecución local

Sigue estos pasos para levantar la aplicación en un entorno local:

1. Clona el repositorio y entra en la carpeta del proyecto:

   ```bash
   git clone https://github.com/julianvanegas/digitalbank.git
   cd digitalbank
   ```

2. Crea la base de datos en PostgreSQL:

   ```sql
   CREATE DATABASE bank;
   ```

   Si utilizas otro nombre, actualiza `DB_NAME`. Si PostgreSQL requiere autenticación o se ejecuta en otro host, ajusta `DB_HOST`, `DB_USERNAME` y `DB_PASSWORD` en `.env`.

3. Copia la plantilla de variables de entorno y ajusta sus valores:

   ```bash
   cp .env.example .env
   ```
   
   Reemplaza los valores de desarrollo, especialmente las credenciales de PostgreSQL, los secretos criptográficos y `RESEND_API_KEY`.

   > El archivo `.env` está ignorado por Git. **Nunca lo subas al repositorio.**

4. Carga las variables de entorno y ejecuta la aplicación:

   ```bash
   set -a && source .env && set +a
   ./mvnw spring-boot:run
   ```

   Spring Boot no carga archivos `.env` automáticamente. Si ejecutas la aplicación desde un IDE, define las mismas variables en la configuración de ejecución.

5. Comprueba que el servicio esté disponible:

   ```bash
   curl http://localhost:8080/actuator/health
   ```

Una respuesta correcta indica que el servicio está activo. Al iniciar, Flyway aplica las migraciones ubicadas en `src/main/resources/db/migration`; Hibernate solo valida el esquema (`ddl-auto=validate`). Por ello, los cambios de estructura de la base de datos deben realizarse mediante nuevas migraciones.

## Documentación de la API

Con `SWAGGER_ENABLED=true`, la documentación interactiva queda disponible en:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Especificación OpenAPI: <http://localhost:8080/v3/api-docs>

Los endpoints protegidos esperan el JWT obtenido en el inicio de sesión en la cabecera:

```http
Authorization: Bearer <token>
```

> En producción establece `SWAGGER_ENABLED=false`.

## Pruebas y calidad

```bash
./mvnw verify
```

Ejecuta las pruebas unitarias y las de integración, genera el reporte de cobertura de ambas en `target/site/jacoco/index.html` y falla si la cobertura de líneas es inferior al 80%. El mismo comando corre en el pipeline de CI, seguido del análisis de SonarCloud.

| Tipo | Clases | Plugin | Qué prueba |
| --- | --- | --- | --- |
| Unitarias | `*Test` | Surefire (`test`) | Cada clase por separado, con dependencias simuladas |
| Integración | `*IT` | Failsafe (`integration-test`) | Repositorios, flujos HTTP completos, concurrencia y trabajos programados contra PostgreSQL real |

Las pruebas de integración levantan un PostgreSQL temporal con [Testcontainers](https://testcontainers.com), así que necesitan Docker o Podman en ejecución; no usan tu base local ni tu `.env`. Con Podman en macOS, basta con encender la máquina virtual, que expone el socket de Docker:

```bash
podman machine start
```

Para ejecutar solo las pruebas unitarias (no necesita Docker ni Podman):

```bash
./mvnw test
```

Para ejecutar solo una prueba de integración:

```bash
./mvnw verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=TransferFlowIT
```

## Observabilidad

La aplicación publica sus métricas (HTTP, JVM, pool de conexiones) en formato Prometheus en `/actuator/prometheus`, mediante Actuator y Micrometer. Esa ruta no usa el JWT de la API, la protege la cadena de [`MetricsSecurityConfig`](src/main/java/com/udea/digitalbank/shared/security/MetricsSecurityConfig.java) con autenticación Basic, con las credenciales `METRICS_USER` y `METRICS_PASSWORD`. Si no están definidas, el endpoint responde siempre 401, nunca queda abierto. Un token de la API no sirve para leer las métricas.

El código de la aplicación es el mismo en todos los entornos; solo cambia quién lee las métricas:

| Entorno | Quién las lee | Dónde se configura |
| --- | --- | --- |
| Local | Prometheus y Grafana en contenedores | [`observability/`](observability) |
| Producción | Grafana Cloud, con su Prometheus gestionado | Grafana Cloud y variables de entorno del servicio |

### Local con Prometheus y Grafana

[`observability/compose.yaml`](observability/compose.yaml) levanta Prometheus `v3.13.5` (versión LTS) y Grafana `13.3.1`, con el data source de Prometheus y el dashboard [JVM (Micrometer)](observability/grafana/dashboards/jvm-micrometer-4701.json) (ID 4701 de grafana.com) ya provisionados. Solo escuchan en `127.0.0.1`.

1. Define `METRICS_USER` y `METRICS_PASSWORD` en tu `.env` (la plantilla trae valores de desarrollo) y levanta la aplicación como se indica en [Ejecución local](#ejecución-local). El usuario debe coincidir con el de [`prometheus.yml`](observability/prometheus/prometheus.yml) (`prometheus-local`).

2. Guarda la contraseña en el archivo que lee Prometheus (está ignorado por Git, porque Prometheus no expande variables de entorno en su configuración):

   ```bash
   set -a && source .env && set +a
   printf '%s' "$METRICS_PASSWORD" > observability/prometheus/metrics-password
   ```

3. Levanta Prometheus y Grafana:

   ```bash
   cd observability
   podman compose up -d
   ```

   Con Docker, usa `docker compose up -d` y cambia el destino de `prometheus.yml` de `host.containers.internal` a `host.docker.internal`.

4. Comprueba cada tramo:

   | Qué | Cómo | Resultado esperado |
   | --- | --- | --- |
   | La aplicación publica las métricas | `curl -u "$METRICS_USER:$METRICS_PASSWORD" http://localhost:8080/actuator/prometheus` | Líneas como `jvm_memory_used_bytes{...}` |
   | La ruta está protegida | `curl -i http://localhost:8080/actuator/prometheus` | `401` |
   | Prometheus las recolecta | [http://localhost:9090/targets](http://localhost:9090/targets) | El destino `digitalbank` en estado `UP` |
   | Grafana las muestra | [http://localhost:3000](http://localhost:3000) (`admin` / `admin`), Dashboards > JVM (Micrometer), con `application = digitalbank` | Panel de la JVM con datos |

5. Para detenerlos: `podman compose down`. No guardan datos entre ejecuciones.

### Producción con Grafana Cloud

Grafana Cloud incluye un Prometheus gestionado, así que no hay ningún servidor que mantener, lee `/actuator/prometheus` de la aplicación mediante HTTPS.

1. En la plataforma donde se despliega la aplicación, define `METRICS_USER` y `METRICS_PASSWORD` en el entorno del servicio. La contraseña debe ser larga y aleatoria, y guardarse como secreto. Despliega la versión con este cambio.
2. En Grafana Cloud, crea un *scrape job* con la integración **Metrics Endpoint** (*Connections*; los nombres de los menús pueden variar) con la URL `https://<dominio-de-la-app>/actuator/prometheus` y autenticación Basic con esas credenciales.
3. Comprueba en *Explore*, con el data source de Prometheus de tu stack, que `up` devuelve `1` para tu job, e importa el dashboard `4701`.

## Despliegue mediante contenedor

El [`Containerfile`](Containerfile) utiliza una construcción multi-etapa, que primero compila la aplicación con Maven y Java 25, y genera una imagen final ligera basada en JRE 25 Alpine. La aplicación se ejecuta con un usuario sin privilegios para reducir la superficie de ataque.

Con Podman (recomendado), ejecuta:

```bash
podman build -f Containerfile -t digitalbank .
podman run --rm -p 8080:8080 --env-file .env digitalbank
```

Con Docker, ejecuta:

```bash
docker build -f Containerfile -t digitalbank .
docker run --rm -p 8080:8080 --env-file .env digitalbank
```

## CI/CD

Los workflows de [GitHub Actions](.github/workflows) ejecutan las pruebas automatizadas y el análisis de SonarCloud como validación previa a la publicación de imágenes.

### Validación en cada push y pull request

El workflow [`build.yml`](.github/workflows/build.yml) corre en cada push a `main`, `develop`, `quality`, `feature/**`, `task/**`, `bugfix/**` y `hotfix/**`, y en cada pull request hacia `main`, `develop` o `quality`:

1. **Automated tests**:
   1. Levanta el servicio de Podman con un socket propio y lo expone a Testcontainers con `DOCKER_HOST`. Si el runner no trae Podman, primero lo instala.
   2. Ejecuta `mvn -B verify`: pruebas unitarias, pruebas de integración contra PostgreSQL y control de cobertura.
   3. Si alguna prueba falla, publica el artefacto `test-reports` con los reportes de Surefire y Failsafe, que se descarga desde la página de la ejecución.
   4. Publica el artefacto `build-artifacts` (la carpeta `target`) para el siguiente job.
2. **SonarCloud analysis** descarga `build-artifacts` y analiza el código con la cobertura combinada de JaCoCo y los resultados de ambos tipos de prueba.

### Publicación y despliegue

El flujo completo es:

1. Crea un pull request desde `quality` hacia `main`.
2. Fusiona el pull request después de que las validaciones finalicen correctamente.
3. GitHub Actions construye y publica en GHCR las imágenes:

   ```text
   ghcr.io/julianvanegas/digitalbank:latest
   ghcr.io/julianvanegas/digitalbank:<commit-sha>
   ```

4. Espera a que termine correctamente el workflow y confirma que la imagen asociada al commit esté disponible en [GHCR](https://github.com/julianvanegas/digitalbank/pkgs/container/digitalbank).
5. En GitHub, crea una nueva Release con un tag como `v1.0.0`, seleccionando como destino el mismo commit de `main` que fue publicado en el paso anterior. Completa el título y las notas, guarda la Release como borrador si lo necesitas y publica la release cuando esté lista. El workflow escucha el evento `release.published`.
6. GitHub Actions vuelve a ejecutar las pruebas y SonarCloud, descarga la imagen asociada al SHA del commit de la Release y publica el tag de versión:

   ```text
   ghcr.io/julianvanegas/digitalbank:v1.0.0
   ```

7. El workflow invoca el despliegue y Render recibe la URL de la imagen con el tag de la Release mediante el deploy hook.
8. Revisa el resultado en [GitHub Actions](https://github.com/julianvanegas/digitalbank/actions), la imagen en GHCR y el entorno `Production` de Render. El secreto `RENDER_DEPLOY_HOOK_URL` debe estar configurado en GitHub Actions y nunca debe incluirse en el repositorio ni en la documentación.

## Contribuir

Las contribuciones son bienvenidas. Antes de abrir un PR lee la [guía de contribución](CONTRIBUTING.md) y el [código de conducta](CODE_OF_CONDUCT.md). El flujo de ramas completo está en la [Wiki](https://github.com/julianvanegas/digitalbank/wiki).

[![Contributors](https://img.shields.io/github/contributors/julianvanegas/digitalbank)](https://github.com/julianvanegas/digitalbank/graphs/contributors)

<a href="https://github.com/julianvanegas/digitalbank/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=julianvanegas/digitalbank" alt="Contributors de DigitalBank" />
</a>

Consulta la [gráfica de contributors](https://github.com/julianvanegas/digitalbank/graphs/contributors) para ver la actividad y las contribuciones más recientes.

## Seguridad

Si encuentras una vulnerabilidad, **no abras un issue público**. Sigue el procedimiento de la [política de seguridad](SECURITY.md).

## Licencia

Software propietario: todos los derechos reservados. El código se publica para su consulta y para proponer cambios mediante pull request; cualquier otro uso requiere permiso previo y por escrito. Consulta el archivo [LICENSE](LICENSE).
