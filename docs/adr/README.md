# Registros de Decisiones de Arquitectura (ADR)

DigitalBank — Backend · Universidad de Antioquia

Este directorio reúne las decisiones de arquitectura significativas del backend de DigitalBank: por qué se tomaron, qué alternativas se descartaron y qué consecuencias aceptó el equipo al tomarlas.

**Cada ADR es inmutable.** Si una decisión cambia, no se edita el registro existente: se añade uno nuevo que lo reemplaza y el anterior pasa a estado `superseded`. Lo único que se modifica sobre un ADR ya publicado es su estado.

## Índice

| ID | Título | Estado | Fecha |
| --- | --- | --- | --- |
| [ADR-0001](0001-adoptar-monolito-modular-por-dominios.md) | Adoptar un monolito modular organizado por dominios | `accepted` | 2026-09-05 |
| [ADR-0002](0002-dividir-dominio-identidad-y-fijar-dependencias.md) | Dividir el dominio de identidad y fijar reglas de dependencia entre módulos | `accepted` | 2026-09-10 |
| [ADR-0003](0003-usar-neon-como-plataforma-postgresql.md) | Usar Neon como plataforma gestionada de PostgreSQL | `accepted` | 2026-09-17 |

## Estados posibles

| Estado | Significado |
| --- | --- |
| `proposed` | La decisión está planteada y argumentada, pero el equipo aún no la ha cerrado. |
| `accepted` | La decisión está vigente y el código la refleja. |
| `superseded` | Otra ADR posterior la sustituye; se conserva por trazabilidad. |
| `deprecated` | El contexto que la motivó desapareció y ya no aplica. |

## Formato

Cada registro usa encabezado YAML al estilo MADR (`status`, `date`, `decision-makers`, `tags`) y un cuerpo con esta estructura:

1. **Contexto y planteamiento del problema** — qué situación obligó a decidir.
2. **Factores de decisión** — qué se optimizó.
3. **Decisión** — qué se hizo.
4. **Alternativas consideradas** — qué se descartó y por qué.
5. **Consecuencias** — qué se gana y qué se paga.
6. **Evidencia en el repositorio** — dónde se ve la decisión en el código.

## Alcance del sistema al momento de este registro

DigitalBank es el backend de una banca digital en construcción. Los módulos implementados son `auth` (identidad de acceso) y `customer` (ciclo de vida del cliente), sobre un módulo transversal `shared`. Los módulos de cuentas y transacciones están previstos y no se han implementado todavía: las decisiones que los mencionan definen el lugar que ocuparán, no código existente.

## Cómo añadir una ADR

1. Copiar el número siguiente de la secuencia y nombrar el archivo `NNNN-titulo-en-imperativo.md`.
2. Escribir el título como una decisión, no como una descripción: «Adoptar…», «Usar…», «Dividir…».
3. Registrar la fecha real de la decisión, no la de redacción del documento.
4. Añadir la fila correspondiente al índice de este archivo.
