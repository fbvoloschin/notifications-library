# Diseño: un contrato de envío, proveedores intercambiables

## Flujo

`solicitud → canal y tipo → validación → proveedor explícito/default → intento(s) → resultado y eventos`

El flujo síncrono es la fuente de verdad. Async cambia el hilo de ejecución, no el routing, las reglas de validación ni la semántica de los errores. El lote compone envíos asíncronos independientes.

## API y extensibilidad

- `Notification` expone su `Channel<? extends Notification>`. `Channel<N>` combina un identificador abierto con `Class<N>`. Email, SMS y Push son tipos separados. Una notificación custom puede definir su canal en código consumidor.
- El builder registra `Channel<N>` con un `NotificationValidator<N>` y asocia proveedores `NotificationProvider<N>` por nombre y default. Copia el registro al construir el cliente. Rechaza ids de canal duplicados y protege el despacho con el tipo registrado, sin casts en el consumidor ni un `switch` de canales.
- `send(SendRequest)` devuelve `ProviderAcceptance` o lanza `SendException`. `sendAsync(SendRequest)` devuelve `CompletableFuture<ProviderAcceptance>`; `sendAll(List<SendRequest>)`, `List<CompletableFuture<ProviderAcceptance>>`. La solicitud contiene una notificación y un nombre de proveedor opcional. Los errores distinguen validación, routing, ejecución, interrupción y fallos del proveedor; nunca exponen respuestas ni excepciones crudas del adaptador. `join()` y `get()` conservan el `SendException` como causa del wrapper estándar de Java.
- Resolver canal y verificar tipo primero; validar el contenido antes de seleccionar proveedor. Sin selección no hay proveedor en el resultado de error ni en el evento. Un nombre explícito desconocido no cae al default.
- Una excepción de un validador custom se normaliza como fallo inesperado de validación, distinto de los datos inválidos: cero intentos, sin proveedor, un evento `FAILED` seguro y sin afectar otros ítems. El lote vacío retorna una lista vacía sin tareas ni eventos, incluso sin executor.

## Decisiones y límites

| Decisión | Motivo y tradeoff |
| --- | --- |
| Strategy por proveedor y registro tipado, no un dispatcher por canal | Agregar canales o adaptadores no modifica el core; el builder concentra las reglas de registro. |
| Adaptadores con requests estructuradas y fixtures en memoria | Se prueban método, ruta, esquema de auth, campos y respuestas sin implementar codificación HTTP real ni usar SDKs. Validación local de credenciales y remitentes no demuestra autorización remota. |
| Clasificación en el adaptador, política en el cliente | El adaptador devuelve aceptación o fallo clasificado con espera mínima opcional. Para el reintento `n >= 1`, el backoff es `min(maxBackoff, initialDelay * 2^(n-1))`, saturado al tope sin overflow. Se espera el mayor valor entre backoff y la indicación del proveedor, que puede superar ese tope. Defaults: 1 segundo inicial, tope de 30 segundos y un solo intento (sin retry). No hay fallback, deduplicación ni garantía de entrega final. |
| Executor del consumidor, sin pool interno | Permite controlar el ciclo de vida y el tamaño del pool. Debe encolar fuera del hilo llamador y notificar rechazos; no se admite ejecución inline ni descarte silencioso. El backoff espera en el hilo del envío. Cancelar el future sólo cancela la observación, no el trabajo: la ejecución y su evento terminal siguen su curso, aunque el future permanezca cancelado. |
| Listeners locales, sin broker | Se invocan en el hilo del envío (en el llamador para fallos de admisión), antes de completar el resultado; deben ser breves y soportar concurrencia. El cliente asigna un id por envío, también presente en el resultado o error, y usa un reloj inyectable. Las excepciones de listeners no cambian el resultado. |

Las notificaciones incorporadas son inmutables. Los validadores y proveedores custom deben soportar llamadas concurrentes si se usan con async. Las secuencias de fixtures para retries se aíslan por envío para que el lote no dependa del orden de ejecución.

## Verificación

- **Core:** un consumidor Java usa el JAR, cambia el default de SendGrid a Mailgun sin tocar el envío y registra un canal y un proveedor propios. Los cuatro adaptadores se prueban con fixtures de éxito y error; usar credenciales ficticias y verificar redacción.
- **Opcionales:** executor controlado para completar un lote fuera de orden; probar lote vacío, cancelación durante el envío y validador custom que lanza. Registrar esperas para comprobar crecimiento exponencial, tope, prioridad de la espera del proveedor e interrupción. Usar reloj fijo y listeners de prueba, sin sleeps reales ni depender de la velocidad de un hilo.
- **Demo:** mismos ejemplos en local y en Docker sin red de ejecución. El punto de entrada de la demo no forma parte del JAR de librería.

## Fuentes de los contratos simulados

[SendGrid Mail Send](https://www.twilio.com/docs/sendgrid/api-reference/mail-send/mail-send) y [X-Message-ID](https://www.twilio.com/docs/sendgrid/glossary/x-message-id) · [Mailgun Send](https://documentation.mailgun.com/docs/mailgun/api-reference/send/mailgun/messages/post-v3--domain-name--messages) · [Twilio Messages](https://www.twilio.com/docs/messaging/api/message-resource) y [error 429](https://www.twilio.com/docs/api/errors/20429) · [FCM HTTP v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api) y [errores](https://firebase.google.com/docs/cloud-messaging/error-codes).
