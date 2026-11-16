---
status: proposed
date: 2026-11-15
decision-makers: [Julian Vanegas López]
consulted: Equipo de Backend
tags: [plataforma, java, dependencias, build, contenedor]
---

# ADR-0007 — Actualizar la plataforma de ejecución a Java 25 (LTS) y las dependencias a sus últimas versiones estables

## Contexto y planteamiento del problema

La ADR-0001 fijó el sistema sobre Java 21. Desde entonces el entorno de desarrollo pasó a JDK 25, de modo que el proyecto se compilaba con `release 21` pero se ejecutaba y probaba en JVM 25, mientras que la integración continua y la imagen de producción seguían en 21. Los tests locales corrían en una versión distinta de la que se despliega.

Java 25 es una versión de soporte a largo plazo (LTS) y Spring Boot 4.0.x la soporta oficialmente. Hacía falta decidir si se alineaba todo en 25 o si se volvía a 21 en los entornos locales.

## Factores de decisión

- **Un único Java en todos los entornos**: local, CI y producción deben ejecutar la misma versión.
- **Soporte a largo plazo**: elegir una LTS evita una nueva migración a corto plazo.
- **Compatibilidad de las herramientas de build**: JaCoCo, Lombok y MapStruct deben funcionar con el bytecode de la versión elegida.
- **Cambio mínimo**: no reescribir código de negocio para adoptar la nueva versión.

## Decisión

Se actualiza el proyecto a Java 25 en todos los entornos:

- `pom.xml`: `java.version` pasa de 21 a 25.
- `jacoco-maven-plugin` pasa de 0.8.12 a 0.8.16. Desde la 0.8.14 hay soporte oficial de Java 25; con la 0.8.12, compilar para 25 puede fallar al instrumentar con `Unsupported class file major version 69`.
- Spring Boot pasa de 4.0.8 a 4.1.2. Arrastra Hibernate 7.4.13 y Flyway 12.4.0, que Boot gestiona.
- `resend-java` pasa de 4.26.0 a 4.30.0 y `springdoc-openapi` de 3.1.1 a 3.2.0.
- MapStruct se queda en 1.6.3, porque la única versión posterior es beta (1.7.0.Beta2).
- `Containerfile`: imagen de compilación `maven:3.9-eclipse-temurin-25` e imagen final `eclipse-temurin:25-jre-alpine`.
- `.github/workflows/build.yml`: `setup-java` con Temurin 25 en los trabajos de pruebas y de SonarCloud.
- `README.md` y `CONTRIBUTING.md`: el requisito previo pasa a ser JDK 25 y se actualizan las versiones citadas.

Esta decisión **modifica únicamente las versiones de Java y de las dependencias** citadas en la ADR-0001; el resto de esa ADR (monolito modular, una base de datos, un gestor transaccional) sigue vigente. Como una ADR es inmutable, la ADR-0001 no se edita: este registro prevalece en lo referente a la versión de Java.

## Alternativas consideradas

- **Quedarse en Java 21 y fijar JDK 21 en el entorno local.** Es válido y no exige cambios, pero deja pendiente una migración que habrá que hacer de todos modos y aplaza las mejoras de la plataforma.
- **Compilar con `release 21` y ejecutar en 25.** Es la situación previa: funciona, pero obliga a mantener dos versiones y no prueba en local lo que se despliega.
- **Pasar a una versión no LTS posterior.** Descartada: el ciclo de soporte es de seis meses y obligaría a migrar de nuevo pronto.

## Consecuencias

**Se gana**

- Local, CI y producción ejecutan la misma versión de Java.
- Soporte a largo plazo con una versión LTS.
- Mejoras de la plataforma sin tocar código, como el uso de hilos virtuales sin bloqueos por `synchronized` y un arranque y un recolector de basura más afinados en contenedor.

**Se paga**

- Quien desarrolle necesita instalar JDK 25.
- Lombok emite en JDK 24 o superior un aviso de `sun.misc.Unsafe` durante la compilación. Es ruido, no un fallo, y desaparecerá cuando Lombok deje de usar esa API.
- Mockito puede avisar de la carga dinámica de agentes durante los tests; se resolvería declarándolo como agente en el `argLine`.
- Spring Boot 4.1 es una versión menor: puede cambiar valores por defecto o deprecar propiedades. Se comprobó que la aplicación arranca contra PostgreSQL 18, aplica las 6 migraciones de Flyway, publica Swagger y responde login y alta de cliente; no se recorrieron los flujos con código por correo (verificación, 2FA, transferencias).
- Hay que verificar que `eclipse-temurin:25-jre-alpine` está disponible en el registro de imágenes usado por el pipeline.

## Evidencia en el repositorio

| Archivo | Qué refleja |
| --- | --- |
| `pom.xml` | `java.version` en 25, Spring Boot 4.1.2, JaCoCo 0.8.16, resend-java 4.30.0 y springdoc 3.2.0. |
| `Containerfile` | Imágenes de compilación y ejecución sobre Temurin 25. |
| `.github/workflows/build.yml` | `java-version: '25'` en los trabajos de pruebas y SonarCloud. |
| `README.md`, `CONTRIBUTING.md` | JDK 25 como requisito previo. |
