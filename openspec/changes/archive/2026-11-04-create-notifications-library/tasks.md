# Tasks: Create Notifications Library

## Review Workload Forecast

Estimated changed lines: 4,000–7,000.
Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

### Suggested Work Units

Líneas/fase: 1) 500–800; 2) 600–1.000; 3) 1.000–1.700; 4) 600–1.000; 5) 900–1.600; 6) 400–900.
`auto-chain`: cortes verificados ≤400 líneas con tests; excepción solo para el PR final desde rama limpia a `main`.

**TDD:** RED observado, GREEN mínimo, REFACTOR verde. `mvn -q -B clean verify` por fase desde 1.3.

## Fase 1: Fundación y configuración

- [x] 1.1 Alinear diseño/specs (UUID por envío, módulos `core`/`examples`) y README (límites y motivos).
- [x] 1.2 Configurar OpenSpec strict TDD (`mvn -q -B clean verify`) y state DAG.
- [x] 1.3 JDK 21/Maven reactor: JAR en `core`, demo en `examples`, release 21 y JUnit 5.
- [x] 1.4 Core: modelos inmutables, API tipada y `SendException` segura.
- [x] 1.5 Builder Java-only con routing inmutable; rechazar duplicados y segundos defaults.

## Fase 2: Validación y routing síncrono

- [x] 2.1 Validación acumulada Email/SMS/Push antes del proveedor; validador custom inesperado seguro, cero intentos.
- [x] 2.2 `send`: fallos tipados de tipo/canal/proveedor/default, sin fallback; aceptación con ID opcional.
- [x] 2.3 `ExtensionTest`: canal/proveedor custom sin editar core ni built-ins.

## Fase 3: Adaptadores offline

- [x] 3.1 SendGrid/Mailgun: contrato exacto y sender propio; `202` con ID opcional, `200 id/message`.
- [x] 3.2 Twilio/FCM: `201 sid/status`, `200 name`; 429/503 transitorios, timeout ambiguo no reintentable.
- [x] 3.3 Preaceptación transitoria; respuestas inesperadas seguras, sin retry/ID inventado; secretos redactados.
- [x] 3.4 Validación de configuración al build; Twilio-only no requiere config Email/Push.
- [x] 3.5 Probar cambio SendGrid→Mailgun variando solo el default.

## Fase 4: Async y lotes

- [x] 4.1 `sendAsync`: executor host sin inline/descarte; ausencia o rechazo = fallo tipado; preservar causa en `join/get`.
- [x] 4.2 `sendAll`: futures ordenados y aislados; lote vacío sin tareas, llamadas ni eventos.
- [x] 4.3 `cancel(true)` solo cancela observación; envío y otros ítems continúan; cliente no cierra executor.

## Fase 5: Retry y eventos

- [x] 5.1 Retry opt-in: `maxAttempts` positivo incluye el inicial; solo transitorios, mismo proveedor, intentos reales, sin fallback.
- [x] 5.2 Backoff con espera fake: defaults 1/30s, serie 1/2/4/4; delay externo prevalece; validar config e interrupción.
- [x] 5.3 Eventos con Clock fijo, UUID por envío y listeners ordenados/aislados; `FAILED` seguro antes del proveedor.
- [x] 5.4 Secuencia `RETRYING(1)`→`ACCEPTED(2)`; interrumpir/cancelar/lote vacío sin eventos terminales falsos.

## Fase 6: Ejemplos y entrega

- [x] 6.1 `examples/.../Demo.java` y smoke test Email/SMS/Push, sin frameworks, cuentas ni red.
- [x] 6.2 README/DESIGN en español, Javadocs en inglés; instalación, API/extensión, providers, secretos, límites y disclosure IA.
- [x] 6.3 Dockerfile/.dockerignore; JDK 21, `mvn clean verify` y runtime `--network none`.
- [x] 6.4 Checkout limpio: seguir README, consumir JAR independiente, cambiar default y agregar canal custom; ejecutar offline.
