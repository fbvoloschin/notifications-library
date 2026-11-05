# Diseño de Notifications Library

## Objetivo y límites

La librería ofrece una API común para Email, SMS y Push, y permite cambiar o extender proveedores mediante configuración Java. El reto prioriza arquitectura, contratos verificables y pruebas reproducibles; por eso los adaptadores simulan solicitudes y respuestas en memoria, sin SDKs ni red.

No confirma entrega al destinatario. Una aceptación significa únicamente que el proveedor simulado aceptó procesar la solicitud. Tampoco incluye colas durables, idempotencia, plantillas, métricas ni publicación a un repositorio Maven.

## Flujo de envío

```text
SendRequest → resolver canal/tipo → validar → elegir proveedor → intento(s)
            → ProviderAcceptance o SendException → eventos seguros
```

La operación sincrónica es la fuente de verdad. La selección explícita de proveedor prevalece sobre el default y nunca cae silenciosamente a otro. La validación ocurre antes de seleccionar o invocar el proveedor, para no gastar intentos con datos inválidos.

## Extensibilidad y módulos

- `core` produce `dev.notifications:notifications-library`, un JAR de librería Java 21 sin frameworks.
- `examples` depende de `core` y contiene `Demo`; el punto de entrada no se empaqueta dentro del JAR consumible.
- `Channel<N>`, `Notification`, `NotificationValidator<N>` y `NotificationProvider<N>` permiten que una aplicación defina un canal tipado sin editar el núcleo.
- `NotificationClientBuilder` concentra los registros. El cliente copia el registro al construirse, rechaza nombres duplicados y resuelve proveedores por nombre y canal.

Las dependencias van hacia el núcleo y las abstracciones, no desde el núcleo hacia proveedores concretos. Cada adaptador implementa el mismo contrato tipado; no hay un `switch` central por proveedor.

Dentro del mismo JAR de `core`, la API común y los contratos de extensión viven en `dev.notifications`, los tipos y validadores incorporados en `dev.notifications.channels`, y los adaptadores y sus fixtures offline en `dev.notifications.providers`. Los proveedores dependen de los canales incorporados y de los contratos del núcleo, nunca al revés. Reutilizan las comprobaciones públicas de sintaxis de email y teléfono de `NotificationValidators`.

## Adaptadores simulados

Cada proveedor modela endpoint, autenticación, payload y respuesta de éxito según su documentación oficial. Los `SimulatedTransport` de prueba permiten capturar requests y suministrar fixtures sin implementar el protocolo HTTP real. FCM cubre el núcleo token/notificación/datos; no modela claves reservadas ni opciones específicas de cada plataforma. Las credenciales usadas en ejemplos son ficticias: validarlas localmente no prueba que una cuenta exista o tenga permisos.

## Fallos y reintentos

`SendException` normaliza errores de validación, routing, configuración de ejecución y proveedor sin propagar cuerpos o causas internas del proveedor ni excepciones de validadores. Las violaciones devueltas por un validador se conservan tal como llegan: los validadores propios deben informar solo identificadores seguros, sin datos personales ni secretos. El conteo representa invocaciones efectivas al proveedor; un error previo a la selección informa cero intentos.

El retry es opt-in y solo opera sobre fallos marcados explícitamente como transitorios. La espera usa backoff acotado y puede respetar un `Retry-After` mayor. Un timeout ambiguo o una respuesta de servidor con aceptación desconocida no se reintenta automáticamente: sin idempotencia eso puede duplicar el mensaje.

## Async y eventos

El consumidor provee el `Executor` y conserva su ciclo de vida. `sendAsync` y `sendAll` ejecutan el mismo flujo de envío; los futures de un lote mantienen el orden de entrada, aunque terminen en otro orden. Cancelar un future cancela la observación, no interrumpe el proveedor ni inventa un resultado terminal.

Cada envío obtiene un UUID propio. Los listeners reciben eventos inmutables `RETRYING`, `ACCEPTED` y `FAILED` con reloj inyectable. Se notifican en el hilo del envío, en orden de registro; sus excepciones no alteran el resultado. Los callbacks deben ser cortos y seguros ante concurrencia.

## Trade-offs

| Decisión | Beneficio | Costo o límite |
|---|---|---|
| Simular proveedores en vez de hacer HTTP | Tests deterministas, sin cuentas y sin efectos externos. | No demuestra interoperabilidad en vivo ni entrega final. |
| Configuración Java, sin framework | Integración en aplicaciones Java diversas y dependencias mínimas. | La aplicación debe cargar y rotar sus propios secretos. |
| Registro tipado y abierto | Extensión sin modificar el core. | El consumidor debe implementar su canal, validador y proveedor. |
| Executor propiedad del host | El consumidor controla concurrencia y apagado. | La configuración del executor debe evitar ejecución inline y descarte silencioso. |
| Reintentos conservadores | Reduce duplicados ante aceptación incierta. | Algunos fallos recuperables no se reintentan si el proveedor no los clasifica explícitamente. |

Para los pasos de uso, ejemplos y omisiones intencionales, ver [README.md](README.md).

## Uso de IA


Se usaron OpenCode (incluyendo Kimi), Codex y DeepSeek para explorar y revisar
requisitos, proponer y refinar especificaciones, diseño y tareas, y asistir en
la implementación. La persona autora definió el alcance, evaluó las propuestas
y tomó las decisiones finales.

Se ejecutaron tests automatizados y, con asistencia de IA, se contrastaron
aspectos básicos de los adaptadores simulados con la documentación oficial de
los proveedores. Esta revisión no constituye una prueba de integración real
ni una verificación exhaustiva de sus APIs. No se enviaron notificaciones reales.
