# DigitalBank QA: automatización de pruebas (Sprint 3)

Automatización de los criterios de aceptación del backend de DigitalBank y del flujo E2E. Como el backend no
tiene frontend, las pruebas se ejecutan **a nivel de API**. Usa el patrón **Screenplay** con **Serenity BDD**,
**Cucumber (Gherkin en español)** y **Rest Assured**, sobre un proyecto generado con el Scaffold
[ScreenplayArchitecture](https://github.com/jumosqu12/ScreenplayArchitecture).

## Requisitos

- JDK 17 a 22 (validado con JDK 21; Gradle Wrapper 8.9 no funciona con JDK 27).
- No hace falta instalar Gradle: se usa el wrapper (`gradlew` / `gradlew.bat`).
- PostgreSQL con la base de QA (`bank_qa`), la misma que usa el backend.
- El backend de DigitalBank en ejecución (por defecto en `http://localhost:8080`).

## Cómo ejecutar

1. **Base de datos de QA.** Crea la base `bank_qa` y levanta el backend contra ella (ver el README de la raíz;
   Flyway crea las tablas al iniciar).
2. **Admin de pruebas.** Con el backend ya iniciado, ejecuta una vez
   [`src/test/resources/sql/seed-admin.sql`](src/test/resources/sql/seed-admin.sql) sobre `bank_qa`. Crea el
   usuario `admin.qa@example.com` (contraseña `Admin#2026qa`). Es idempotente y **solo para la base local de
   QA**; nunca en un ambiente desplegado.
3. **Variables de entorno.** Las pruebas leen la configuración del entorno (no de archivos):

   | Variable | Para qué | Valor por defecto |
   |---|---|---|
   | `API_BASE_URL` | URL del backend | `http://localhost:<SERVER_PORT>` |
   | `SERVER_PORT` | Puerto del backend | `8080` |
   | `DB_HOST` | Host y puerto de PostgreSQL | `localhost:5432` |
   | `DB_NAME` | Base de datos | `bank_qa` |
   | `DB_USERNAME` | Usuario de la base | `postgres` |
   | `DB_PASSWORD` | Contraseña de la base | **obligatoria** |
   | `VERIFICATION_HMAC_SECRET` | Debe ser **el mismo** que usa el backend; sirve para recuperar los códigos 2FA | **obligatoria** |
   | `QA_ADMIN_EMAIL` | Correo del admin de pruebas | `admin.qa@example.com` |
   | `QA_ADMIN_PASSWORD` | Contraseña del admin de pruebas | `Admin#2026qa` |
   | `ACCEPTANCE_INACTIVITY_WAIT_MS` | Espera de HU04 CA02 en milisegundos | `301000` |

   En Windows, desde `automation/`, puedes cargar el `.env` de la raíz en la sesión actual con
   `.\cargar-env.ps1`.
4. **Correr las pruebas** desde `automation/`:

   ```bash
   # Toda la suite
   ./gradlew test

   # Solo una historia, un criterio o el E2E (por etiqueta)
   ./gradlew test -Dcucumber.filter.tags="@HU02"
   ./gradlew test -Dcucumber.filter.tags="@HU02 and @CA05"
   ./gradlew test -Dcucumber.filter.tags="@HU03"
   ./gradlew test -Dcucumber.filter.tags="@HU04"
   ./gradlew test -Dcucumber.filter.tags="@HU21"
   ./gradlew test -Dcucumber.filter.tags="@HU19 or @HU20"
   ./gradlew test --tests "*AutenticacionSesionTransaccionesRunner" -Dcucumber.execution.dry-run=true
   ./gradlew test -Dcucumber.filter.tags="@E2E"
   ```

   En Windows usa `gradlew.bat` y pon el filtro entre comillas simples, porque PowerShell corta el argumento en
   el punto: `.\gradlew.bat test '-Dcucumber.filter.tags=@HU19'`. Al terminar, la tarea `aggregate` genera el reporte automáticamente.

> Los códigos 2FA se recuperan leyendo `verification_challenges` en la base de QA y recalculando el HMAC.
> Eso solo funciona en el ambiente local de pruebas; no apuntes la suite a un ambiente real.
> HU19, HU20, HU21 y el E2E crean su propio cliente y sus cuentas, y el saldo lo consigna el administrador; no
> necesitan datos preparados a mano.
> HU04 CA02 requiere iniciar el backend con `SESSION_INACTIVITY_TIMEOUT_MINUTES=5`; el escenario espera cinco
> minutos y un segundo por defecto. No hagas peticiones protegidas durante esa espera.

## Reporte de resultados

Después de ejecutar, abre `target/site/serenity/index.html` en el navegador. Muestra el resumen, los
escenarios por historia (`@HUxx`) y por criterio (`@CAxx`), y cada petición HTTP con su respuesta.

## Convenciones

- **Un escenario por criterio de aceptación**, con las etiquetas de su historia y su criterio: `@HU02` a nivel
  de feature y `@CA05` a nivel de escenario. El flujo integrado usa `@E2E`.
- Los escenarios se escriben en español (`# language: es`), con la estructura Dado / Cuando / Entonces.
- **Cada tarea hace una sola acción de negocio**; las verificaciones van en **preguntas**, no dentro de las
  tareas. Las tareas compuestas solo encadenan otras tareas.
- Cada escenario crea sus propios datos (documento y correo únicos), por lo que no depende de otros.

## Estructura

```
automation/
├── build.gradle, serenity.properties, gradle.properties
├── cargar-env.ps1                      Carga el .env de la raíz (Windows)
└── src
    ├── main/java/co/edu/udea/certificacion/digitalbank
    │   ├── tasks/          Acciones de negocio: RegistrarCliente, ConfirmarCorreo, AprobarCliente,
    │   │                   CambiarEstadoDelCliente, IniciarSesion, ConfirmarSegundoFactor, CerrarSesion,
    │   │                   AutenticarseConDobleFactor, ConsultarCliente, ConsultarMiPerfil,
    │   │                   RetirarDinero, TransferirFondos, ConfirmarTransaccion, ConsultarTransaccion,
    │   │                   BloquearCuenta; compuestas: PrepararClienteActivo, RetirarConDobleFactor,
    │   │                   TransferirConDobleFactor
    │   │   └── provisional/ AbrirCuenta, RealizarConsignacion (se reemplazan por las de HU10 y HU18)
    │   ├── interactions/   Peticiones HTTP base: Get, Post, Patch (sobre PeticionHttp)
    │   ├── questions/      Verificaciones: CodigoDeRespuesta, MensajeDeError, EstadoDelCliente,
    │   │                   ResultadoDelLogin, CampoDeLaRespuesta, CodigoDeVerificacion,
    │   │                   EstadoDeLaTransaccion, ComprobanteDeLaTransaccion
    │   │   └── provisional/ SaldoDeLaCuenta (se reemplaza por la de HU12)
    │   ├── models/         DatosCliente (datos de prueba únicos)
    │   ├── integrations/   CodigosVerificacion (lee el código 2FA de la base de QA)
    │   └── utils/          ConfiguracionQa, Json, CodigoHmac
    └── test
        ├── java/.../runners/          Runners por etiqueta
        ├── java/.../stepdefinitions/  Actores, Hooks, pasos comunes y pasos por historia
        └── resources
            ├── features/identidad/    registro_cliente, cambio_estado_cliente, inicio_sesion
            ├── features/seguridad-transacciones/ autenticacion_2fa, sesion, 2fa_transaccional
            ├── features/transacciones/ retiros, transferencias, flujo_e2e
            └── sql/seed-admin.sql
```

### Actores y tareas principales

- **Cliente** y **Administrador**, cada uno con su propia sesión y token en la memoria del actor.
- "Cliente Activo listo para operar": `cliente.attemptsTo(PrepararClienteActivo.aprobadoPor(administrador))`
  registra al cliente, confirma su correo y lo aprueba. El administrador debe haber iniciado sesión antes.
- Login completo con 2FA: `AutenticarseConDobleFactor` (deja el token en el actor).
- Retiros y transferencias: `RetirarDinero` y `TransferirFondos` solo inician la operación y guardan su
  referencia (`referenciaTransaccion`); `ConfirmarTransaccion` la confirma con el código recibido o con uno dado.
  `RetirarConDobleFactor` y `TransferirConDobleFactor` hacen las dos cosas.
- Cierre de sesión: `CerrarSesion` guarda el token que se usaba como `tokenAnterior`, para comprobar que el
  backend lo rechaza.

## Cobertura actual

| Historia | Criterios automatizados | Feature |
|---|---|---|
| HU01 Registrar cliente | CA01, CA03, CA05, CA07, CA09, CA10 | `registro_cliente.feature` |
| HU08 Cambiar estado de cliente | CA01, CA02, CA05, CA06 | `cambio_estado_cliente.feature` |
| HU02 Iniciar sesión | CA01, CA02, CA04, CA05, CA06, CA07, CA12 | `inicio_sesion.feature` |
| HU03 Verificar identidad mediante 2FA | CA02, CA05, CA06 | `autenticacion_2fa.feature` |
| HU04 Gestionar sesión | CA01, CA02 | `sesion.feature` |
| HU19 Realizar retiro | CA01, CA02, CA04, CA05, CA07, CA08, CA09 | `retiros.feature` |
| HU20 Realizar transferencia | CA01, CA02, CA03, CA04, CA07 | `transferencias.feature` |
| HU21 2FA de transacciones | CA01, CA02, CA03 | `2fa_transaccional.feature` |
| Flujo E2E | HU01 → HU08 → HU02/HU03 → HU10 → HU18 → HU12 → HU19 → HU20 → HU12 → HU04 | `flujo_e2e.feature` |

## Resultados y defectos conocidos

Última ejecución de identidad y acceso (22-nov-2026): 17 escenarios, 21 casos de prueba; 20 pasan y 1 falla.

| Criterio | Resultado | Detalle |
|---|---|---|
| HU02 CA04 | Falla | Tras 3 contraseñas incorrectas, el login con la contraseña correcta responde 403. El backend bloquea al **tercer** intento fallido (`UserStatusService`, `MAX_FAILED_ATTEMPTS = 3` con `>=`); la historia pide bloquear al **cuarto**. Registrado en el informe de defectos como HU02 CA04 y CA05. |
