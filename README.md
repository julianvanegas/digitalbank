# DigitalBank

DigitalBank es una API stateless para gestionar clientes, cuentas y transacciones financieras de clientes y empleados con trazabilidad.

[![Latest Release](https://img.shields.io/github/v/release/julianvanegas/digitalbank?sort=semver)](https://github.com/julianvanegas/digitalbank/releases/latest)
[![GHCR Package](https://img.shields.io/badge/GHCR-digitalbank-2496ED?logo=docker&logoColor=white)](https://github.com/julianvanegas/digitalbank/pkgs/container/digitalbank)

[![CI Pipeline](https://github.com/julianvanegas/digitalbank/actions/workflows/build.yml/badge.svg)](https://github.com/julianvanegas/digitalbank/actions/workflows/build.yml)
[![CodeQL](https://github.com/julianvanegas/digitalbank/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/julianvanegas/digitalbank/actions/workflows/github-code-scanning/codeql)
[![Quality gate status](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=coverage)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)

[![Lines of code](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Security issues](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=software_quality_security_issues)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Maintainability issues](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=software_quality_maintainability_issues)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)
[![Reliability issues](https://sonarcloud.io/api/project_badges/measure?project=julianvanegas_digitalbank&metric=software_quality_reliability_issues)](https://sonarcloud.io/summary/new_code?id=julianvanegas_digitalbank)

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
| `transactions` | Transferencias en dos pasos: iniciar y confirmar con segundo factor. |

## Tecnologías

[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.8-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)

- Java 21 y Spring Boot 4.0.8 con Web MVC, Data JPA, Security, Validation, Actuator y Flyway.
- PostgreSQL como sistema gestor de base de datos, con migraciones versionadas mediante Flyway.
- JSON Web Tokens (JWT) con JJWT 0.13.0 para autenticación y autorización.
- MapStruct 1.6.3 y Lombok para el mapeo entre entidades y DTOs y la reducción de código repetitivo.
- springdoc-openapi 3.1.1 para generar la especificación OpenAPI y publicar Swagger UI.
- Resend Java 4.26.0 para el envío de notificaciones por correo electrónico.
- JUnit y Spring Boot Test para pruebas automatizadas.
- JaCoCo 0.8.12 para cobertura de código, con un umbral mínimo del 65%, y SonarCloud para análisis estático y calidad.
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
├── src/main/java/com/udea/digitalbank/
│   ├── accounts/         Cuentas bancarias
│   ├── auth/             Identidad, sesiones y verificación
│   ├── customer/         Clientes
│   ├── employee/         Empleados
│   ├── transactions/     Transferencias
│   └── shared/           Seguridad, excepciones, OpenAPI y utilidades transversales
├── src/main/resources/
│   └── db/migration/     Migraciones Flyway
├── src/test/java/        Pruebas unitarias por módulo
├── Containerfile         Imagen OCI de la aplicación
└── .env.example          Plantilla de configuración
```

## Requisitos previos

- Git para clonar el repositorio.
- JDK 21 instalado y disponible en el `PATH`.
- PostgreSQL 14 o superior, en ejecución y con permisos para crear la base de datos `bank`.
- Una cuenta de [Resend](https://resend.com) y una API key válida para enviar notificaciones por correo.
- Docker o Podman, únicamente si vas a construir y ejecutar la imagen del contenedor localmente.
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

Ejecuta las pruebas automatizadas, genera el reporte de cobertura en `target/site/jacoco/index.html` y falla si la cobertura de líneas es inferior al 65%. El mismo comando corre en el pipeline de CI, seguido del análisis de SonarCloud.

Para ejecutar solo las pruebas:

```bash
./mvnw test
```

## Despliegue mediante contenedor

El [`Containerfile`](Containerfile) utiliza una construcción multi-etapa, que primero compila la aplicación con Maven y Java 21, y genera una imagen final ligera basada en JRE 21 Alpine. La aplicación se ejecuta con un usuario sin privilegios para reducir la superficie de ataque.

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

Los workflows de [GitHub Actions](.github/workflows) ejecutan las pruebas automatizadas y el análisis de SonarCloud como validación previa a la publicación de imágenes. El flujo completo es:

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
