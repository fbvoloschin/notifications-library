# Tasks: Create Notifications Library

## Review Workload Forecast

Estimated changed lines: 4,000–7,000.
Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

### Suggested Work Units

Líneas/fase: 1:500–800; 2:600–1.000; 3:1.000–1.700; 4:600–1.000; 5:900–1.600; 6:400–900.
`auto-chain`: subdividir fases en cortes verificables ≤400 líneas, tests incluidos. `size:exception` sólo para el PR final verificado desde una rama de entrega limpia hacia `main`.

**TDD:** RED observado, GREEN mínimo, REFACTOR verde. `mvn -q -B clean verify` por fase desde 1.3.

## Fase 1: Fundación y configuración

- [ ] 1.1 Alinear `design.md`/specs: correlation ID único por envío, layout `core`/`examples`; README inicial con limitaciones/faltantes/porqués.
- [x] 1.2 Crear `openspec/config.yaml` (`apply.tdd: true`, `testing.test_command: mvn -q -B clean verify`) e inicializar `openspec/changes/create-notifications-library/state.yaml`.
- [x] 1.3 JDK 21 (`JAVA_HOME`, `mvn -version`); crear `pom.xml` agregador, `core/pom.xml` (JAR), `examples/pom.xml` (demo); Java 21/JUnit.
- [ ] 1.4 Crear en `core/src/{main,test}/java/dev/notifications/`: modelos inmutables, API pública y `SendException` tipado/seguro.
- [ ] 1.5 `NotificationClientBuilder`: config Java sin carga externa; routing inmutable; duplicados de canal/proveedor (case-sensitive) y segundo default rechazados sin reemplazo.

## Fase 2: Validación y routing síncrono

- [ ] 2.1 validators: agregar violaciones Email/SMS/Push (Unicode) antes del proveedor; excepción custom = validación inesperada segura, cero intentos/selección.
- [ ] 2.2 `NotificationClient.send`: mismatch de tipo, canal/proveedor desconocido, default ausente, explícito sin default, sin fallback; aceptación con canal/proveedor/intentos/ID opcional.
- [ ] 2.3 `ExtensionTest`: routing con dobles, canal/notificación/validator/providers custom sin editar core/built-ins.

## Fase 3: Adaptadores offline

- [ ] 3.1 SendGrid/Mailgun: request/auth/payload exactos, sender propio para mismo Email; `202`+`X-Message-ID`, `200`(`id`,`message`).
- [ ] 3.2 Twilio/FCM: contratos exactos; `201`(`sid`,`status`), `200`(`name`); `429`/`503` transitorios con delay; timeout=aceptación desconocida; rechazo/excepción diferenciados.
- [ ] 3.3 Conexión preaceptación transitoria; `2xx` inesperado/body malformado sin retry ni ID inventado; fixtures deterministas, secretos/bodies/causas no expuestos.
- [ ] 3.4 Construcción: credencial obligatoria ausente/sender inválido→error seguro sin invocaciones; sólo Twilio→construye sin credenciales Email/Push.
- [ ] 3.5 Verificar SendGrid→Mailgun variando sólo default (mismo consumidor/notificación).

## Fase 4: Async y lotes

- [ ] 4.1 `sendAsync`: executor controlado del host ejecuta fuera del caller; ausencia o `RejectedExecutionException` = error tipado sin llamadas ni inline; `join/get` conservan causa. Documentar que el executor no puede usar caller-runs ni descarte silencioso.
- [ ] 4.2 `sendAll`: orden de entrada con ejecución desordenada, resultados mixtos aislados; vacío sin tareas/llamadas, incluso sin executor.
- [ ] 4.3 cancelación: `cancel(true)` sólo afecta observación; proveedor pausado termina, ítem independiente sigue, executor nunca se cierra.

## Fase 5: Retry y eventos

- [ ] 5.1 retry opt-in: `maxAttempts` positivo/default uno, sólo transient, mismo proveedor, conteo real; límite/otras categorías sin fallback.
- [ ] 5.2 `RetryPolicy`+fake wait: defaults 1/30s, waits 1/2/4/4 saturados, delay 10s supera cap 4s; config inválida rechazada; interrupción restaura flag sin nuevo intento.
- [ ] 5.3 eventos con `Clock` fijo: ID único por envío, timestamps, listeners ordenados/aislados, terminal seguro único; validador custom/rechazo de admisión emiten `FAILED`, cero intentos/sin proveedor.
- [ ] 5.4 secuencias: `RETRYING(1)`→`ACCEPTED(2)`, mismo ID; espera interrumpida sin `RETRYING`; cancelación conserva un `ACCEPTED`, sin `FAILED` sintético; vacío sin eventos.

## Fase 6: Ejemplos y entrega

- [ ] 6.1 Crear `examples/src/main/java/dev/notifications/demo/Demo.java` y smoke test: Email/SMS/Push simulados, sin framework/cuentas/red.
- [ ] 6.2 Completar `README.md` español y Javadoc inglés: Maven/Gradle, quickstart, API/extensión, proveedores/referencias, credenciales/rotación y disclosure real de IA.
- [ ] 6.3 `Dockerfile`/`.dockerignore`: build, demo `--network none`; JDK 21 (`mvn -version`), `mvn -q -B clean verify`.
- [ ] 6.4 Desde un clon limpio, seguir el README: instalar y usar el JAR en un consumidor Java independiente; cambiar SendGrid por Mailgun sólo por configuración y registrar un canal propio sin editar el core. Ejecutar los ejemplos sin cuentas ni red en runtime.
