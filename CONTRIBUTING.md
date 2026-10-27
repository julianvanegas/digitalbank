# Guía de contribución

[![Contributors](https://img.shields.io/github/contributors/julianvanegas/digitalbank)](https://github.com/julianvanegas/digitalbank/graphs/contributors)

Gracias por tu interés en mejorar DigitalBank. Este documento explica cómo proponer cambios para que la revisión sea ágil. Al participar aceptas cumplir el [Código de conducta](CODE_OF_CONDUCT.md).

El flujo de trabajo detallado del equipo está en la Wiki: [Flujo de trabajo en Digital Bank: guía para desarrolladores](https://github.com/julianvanegas/digitalbank/wiki/Flujo-de-trabajo-en-Digital-Bank:-gu%C3%ADa-para-desarrolladores).

## Formas de contribuir

- Reportar errores o proponer mejoras abriendo un [issue](https://github.com/julianvanegas/digitalbank/issues). Incluye pasos para reproducir, resultado esperado y obtenido, y la versión o rama.
- Corregir errores o implementar funcionalidades mediante un pull request.
- Mejorar la documentación, incluidos los [ADR](docs/adr/README.md).
- Reportar vulnerabilidades: no uses issues públicos; sigue la [política de seguridad](SECURITY.md).

Si el cambio es grande o toca la arquitectura, abre primero un issue para discutirlo antes de escribir código.

## Preparar el entorno

Sigue las secciones [Requisitos previos](README.md#requisitos-previos), [Configuración](README.md#configuración) y [Ejecución local](README.md#ejecución-local) del README. Necesitas JDK 21, PostgreSQL y las variables definidas en `.env.example`. No incluyas el archivo `.env` en los commits.

## Ramas

Las ramas `develop`, `quality` y `main` están protegidas, no se hace push directo; todo cambio entra por medio de un Pull Request.

| Rama | Propósito |
| --- | --- |
| `develop` | Integración del desarrollo. Las funcionalidades salen de aquí y vuelven aquí. |
| `quality` | Pruebas de calidad (QA). Las correcciones salen de aquí y vuelven aquí. |
| `main` | Producción. Solo recibe versiones aprobadas desde `quality`. |

Tu rama de trabajo debe llevar uno de estos prefijos:

| Prefijo | Uso |
| --- | --- |
| `feature/` | Nueva funcionalidad |
| `bugfix/` | Corrección de un error |
| `hotfix/` | Corrección urgente (su PR apunta a `quality`) |
| `task/` | Tareas técnicas, de infraestructura o documentación |
| `work/` | Trabajo exploratorio o en curso |

## Paso a paso

1. Actualiza y parte de la rama base (`develop`, o `quality` si es una corrección de QA):

   ```bash
   git switch develop
   git pull --ff-only origin develop
   ```

2. Crea tu rama:

   ```bash
   git switch -c prefijo/nombre-de-tu-rama
   ```

3. Haz tus cambios en commits pequeños y con sentido propio.

4. Antes de subir, ejecuta las validaciones:

   ```bash
   ./mvnw verify
   ```

5. Sube la rama:

   ```bash
   git push origin prefijo/nombre-de-tu-rama
   ```

6. Al hacer push, el workflow de automatización crea el Pull Request cuando la rama usa uno de estos prefijos: `feature/*`, `task/*` o `bugfix/*` hacia `develop`; `hotfix/*` hacia `quality`. Para otros casos, crea el Pull Request manualmente. Completa la [plantilla de PR](.github/pull_request_template.md) y marca el checklist.

## Mensajes de commit

Escribe mensajes breves, en español, en modo imperativo y que expliquen el *qué* y el *porqué*. Se recomienda [Conventional Commits](https://www.conventionalcommits.org/es/).

## Estándares de código

- Respeta la estructura de módulos (`api`, `controller`, `domain`, `dto`, `mapper`, `repository`, `service`) y las reglas de dependencia entre módulos definidas en los [ADR](docs/adr/README.md). Un módulo solo usa el paquete `api` de otro.
- Usa DTO para la entrada y salida de los controladores y MapStruct para mapear; no expongas entidades JPA.
- Los cambios de esquema se hacen con una **nueva** migración Flyway (`V<n>__descripcion.sql`). No edites migraciones ya aplicadas.
- Valida la entrada con Bean Validation y lanza `BusinessException` (o sus derivadas) para errores de negocio, que gestiona `GlobalExceptionHandler`.
- Nunca incluyas secretos, contraseñas ni datos personales reales en el código, las pruebas o los logs.
- Comenta el *porqué* de lo que no sea evidente, no lo que el código ya dice.

## Pruebas

- Todo cambio de comportamiento debe incluir pruebas automatizadas en `src/test/java`, en el mismo paquete que la clase probada.
- `./mvnw verify` debe pasar. La cobertura de líneas mínima es del 65%, y SonarCloud analiza cada PR.
- Si corriges un error, añade una prueba que falle sin la corrección.
- No dependas de credenciales, servicios externos o datos personales reales en las pruebas.

## Pull requests

Un buen PR:

- Hace una cosa y es lo bastante pequeño para revisarse con calma.
- Explica en el resumen qué cambia y por qué, y enlaza el issue o historia de usuario relacionada.
- Actualiza la documentación (README, API/OpenAPI, ADR) cuando corresponde.
- Tiene el CI en verde y el checklist de la [plantilla](.github/pull_request_template.md) completo.
- Indica explícitamente qué comandos ejecutaste y si alguna validación no pudo realizarse.

Los revisores se asignan según [CODEOWNERS](.github/CODEOWNERS). Responde a los comentarios con nuevos commits en la misma rama; el PR se fusiona cuando está aprobado y el CI pasa.

### Conflictos de fusión

Si tu rama entra en conflicto con la base, actualízala antes de pedir revisión:

```bash
git fetch origin
git merge origin/develop
```

Resuelve los conflictos conservando la intención de ambos cambios, vuelve a ejecutar `./mvnw verify` y sube el resultado.

## Decisiones de arquitectura

Si tu cambio introduce una decisión significativa, documéntala con un ADR siguiendo [la guía de `docs/adr`](docs/adr/README.md#cómo-añadir-una-adr). Los ADR son inmutables: para cambiar una decisión se crea uno nuevo que sustituye al anterior.

## Licencia de las contribuciones

DigitalBank es software propietario (ver [LICENSE](LICENSE)). Al enviar un PR declaras que eres autor de tu contribución y aceptas que pase a formar parte del proyecto y se use bajo esos mismos términos.
