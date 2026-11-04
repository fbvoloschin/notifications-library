# Notifications Library

Librería Java 21+ para enviar notificaciones tipadas por Email, SMS y Push con proveedores intercambiables. Es una demo de arquitectura: modela contratos realistas en memoria, pero **no realiza llamadas HTTP ni envía mensajes reales**.

## Inicio rápido

Requiere JDK 21 y Maven. Desde la raíz del repositorio:

```bash
mvn -B clean verify
mvn -B clean install
```

`verify` compila y ejecuta las pruebas; `install` publica el JAR localmente para que lo use un proyecto consumidor. La demo independiente se ejecuta así en macOS/Linux:

```bash
java -cp examples/target/classes:core/target/classes dev.notifications.demo.Demo
```

La salida informa `ACCEPTED` para Email, SMS y Push. Significa que el proveedor simulado aceptó la solicitud, no que un destinatario recibió el mensaje.

## Consumir el JAR

Luego de ejecutar `mvn -B clean install`, agregá la dependencia a un consumidor Maven:

```xml
<dependency>
    <groupId>dev.notifications</groupId>
    <artifactId>notifications-library</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Para Gradle, configurá `mavenLocal()` y la misma coordenada:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'dev.notifications:notifications-library:1.0.0-SNAPSHOT'
}
```

El artefacto consumible es `core/target/notifications-library-1.0.0-SNAPSHOT.jar`. La clase ejecutable de la demo vive en el módulo separado `examples`; no forma parte del JAR de la librería.

## Configuración Java y envío

La librería no depende de Spring ni de otro framework y no busca configuración en variables de entorno, propiedades o archivos. El programa consumidor puede leer sus secretos y pasarlos explícitamente:

```java
import dev.notifications.EmailNotification;
import dev.notifications.NotificationClient;
import dev.notifications.NotificationValidators;
import dev.notifications.ProviderAcceptance;
import dev.notifications.SendException;
import dev.notifications.SendGridProvider;
import dev.notifications.SendRequest;

public final class EmailQuickStart {
    public static void main(String[] args) {
        var apiKey = "demo-sendgrid-key"; // fake value for the in-memory simulation

        var client = NotificationClient.builder()
                .registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                .registerProvider(EmailNotification.CHANNEL, "sendgrid",
                        new SendGridProvider(apiKey, "notifications@example.com"), true)
                .build();

        var email = new EmailNotification("reviewer@example.com", "Build complete", "Ready for review");
        try {
            ProviderAcceptance result = client.send(new SendRequest(email));
            System.out.printf("Accepted by %s; attempts=%d%n", result.provider(), result.attempts());
        } catch (SendException error) {
            System.err.printf("Send failed: %s; correlation=%s%n", error.reason(), error.correlationId());
        }
    }
}
```

El ejemplo usa valores de muestra: el adaptador no contacta a SendGrid ni valida credenciales contra una cuenta. Los nombres de proveedores son sensibles a mayúsculas/minúsculas. Una vez registrado Mailgun como se muestra más abajo, se puede elegir por envío con `new SendRequest(email, "mailgun")`; si no se indica, se usa el predeterminado del canal.

### Canales y proveedores incluidos

| Canal / proveedor | Configuración propia | Solicitud simulada | Éxito simulado |
|---|---|---|---|
| Email / SendGrid | API key y remitente | `POST /v3/mail/send`, Bearer y JSON | `202` vacío; `X-Message-ID` opcional |
| Email / Mailgun | API key, dominio y remitente | `POST /v3/{domain}/messages`, Basic `api:<key>` y multipart | `200` con `id` y `message` |
| SMS / Twilio | Account SID, token y número remitente | `POST /2010-04-01/Accounts/{AccountSid}/Messages.json`, Basic y form-urlencoded | `201` con `sid` y `status` |
| Push / FCM | Proyecto y token OAuth | `POST /v1/projects/{projectId}/messages:send`, Bearer y JSON | `200` con `name` |

La [demo completa](examples/src/main/java/dev/notifications/demo/Demo.java) muestra también cómo registrar y enviar SMS y Push.

La validación local es acumulativa y sucede antes de invocar al proveedor:

| Canal | Reglas principales |
|---|---|
| Email | Destinatario con formato de email; asunto y cuerpo no vacíos. El remitente pertenece a la configuración del proveedor. |
| SMS | Destinatario E.164 (`+` y 8–15 dígitos empezando por un dígito distinto de cero); cuerpo no vacío de hasta 1600 puntos de código Unicode. |
| Push | Token, título y cuerpo no vacíos; los datos opcionales son pares string/string no nulos. |

Las solicitudes simuladas reproducen endpoint, autenticación y estructura de datos de las APIs documentadas, sin serializar ni transmitir HTTP:

- [SendGrid Mail Send](https://www.twilio.com/docs/sendgrid/api-reference/mail-send/mail-send)
- [Mailgun: enviar mensajes](https://documentation.mailgun.com/docs/mailgun/api-reference/send/mailgun/messages/post-v3--domain-name--messages)
- [Twilio Messages](https://www.twilio.com/docs/messaging/api/message-resource)
- [FCM HTTP v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api)

### Cambiar el proveedor predeterminado

Configurá ambos proveedores con sus propios remitentes y cambiá solo `preferMailgun`. La llamada de envío no cambia:

```java
import dev.notifications.EmailNotification;
import dev.notifications.MailgunProvider;
import dev.notifications.NotificationClient;
import dev.notifications.NotificationValidators;
import dev.notifications.SendGridProvider;
import dev.notifications.SendRequest;

public final class ProviderSwitchDemo {
    public static void main(String[] args) {
        var sendGrid = new SendGridProvider("demo-sendgrid-key", "grid@example.com");
        var mailgun = new MailgunProvider("demo-mailgun-key", "mg.example.com", "mailgun@example.com");
        boolean preferMailgun = true;

        var client = NotificationClient.builder()
                .registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                .registerProvider(EmailNotification.CHANNEL, "sendgrid", sendGrid, !preferMailgun)
                .registerProvider(EmailNotification.CHANNEL, "mailgun", mailgun, preferMailgun)
                .build();

        var email = new EmailNotification("reviewer@example.com", "Build complete", "Ready for review");
        var result = client.send(new SendRequest(email));
        System.out.printf("Accepted by %s%n", result.provider());
    }
}
```

## Extender con un canal propio

Un consumidor puede declarar una notificación tipada, validarla y registrar su proveedor sin modificar la librería:

```java
import dev.notifications.Channel;
import dev.notifications.Notification;
import dev.notifications.NotificationClient;
import dev.notifications.SendRequest;
import java.util.List;
import java.util.Optional;

public final class InboxDemo {
    private record InboxNotification(String text) implements Notification {
        static final Channel<InboxNotification> CHANNEL = new Channel<>("inbox", InboxNotification.class);

        @Override
        public Channel<InboxNotification> channel() {
            return CHANNEL;
        }
    }

    public static void main(String[] args) {
        var client = NotificationClient.builder()
                .registerChannel(InboxNotification.CHANNEL,
                        inbox -> inbox.text().isBlank() ? List.of("text is required") : List.of())
                .registerProvider(InboxNotification.CHANNEL, "local",
                        inbox -> Optional.of("accepted"), true)
                .build();

        var result = client.send(new SendRequest(new InboxNotification("Hello")));
        System.out.printf("Accepted by %s: %s%n", result.provider(), result.providerMessageId().orElse("no id"));
    }
}
```

## API y manejo de fallos

- `send(SendRequest)` ejecuta sincrónicamente y devuelve `ProviderAcceptance` o lanza `SendException` tipada.
- `sendAsync` devuelve un `CompletableFuture<ProviderAcceptance>`; `sendAll` devuelve una lista de futures, uno por solicitud y en el orden de entrada.
- Ambos requieren un `Executor` administrado por la aplicación: debe ejecutar fuera del hilo llamador y señalizar los rechazos con `RejectedExecutionException`, sin ejecutar inline ni descartar tareas silenciosamente. La librería no lo crea ni lo cierra.
- Los reintentos se configuran con `RetryPolicy`; por defecto se realiza un intento. Solo se reintentan fallos clasificados explícitamente como transitorios. Ante una aceptación desconocida no se reintenta, para evitar duplicados.
- Los listeners reciben `RETRYING`, `ACCEPTED` o `FAILED`. No hay evento `SENT`: la aceptación no acredita entrega final. Los listeners son síncronos, deben ser breves y tolerar concurrencia.
- Los fallos de proveedor y las excepciones de validadores no exponen cuerpos ni causas internas. Los validadores incorporados devuelven nombres de campos, no sus valores.
- Un validador propio debe devolver únicamente identificadores de campos seguros: la librería incluye esas violaciones tal como se reciben en `SendException.violations()`. No incluyas secretos ni datos personales en ellas.

| API | Responsabilidad |
|---|---|
| `NotificationClient.builder()` | Crear el registro Java y construir un cliente inmutable. |
| `registerChannel(channel, validator)` | Registrar un canal tipado y su validador. |
| `registerProvider(channel, name, provider, useAsDefault)` | Agregar un proveedor; el nombre es único por canal. |
| `send(request)` | Envío sincrónico; devuelve `ProviderAcceptance`. |
| `sendAsync(request)` / `sendAll(requests)` | Envío async y lote ordenado; requieren executor del consumidor. |
| `RetryPolicy` / `RetryWait` | Configurar reintentos acotados y una espera sustituible en tests. |
| `SendEvent` / `addListener(listener)` | Observar reintentos y resultados terminales seguros. |
| `SendException` | Error tipado con razón, canal, proveedor opcional, correlación e intentos. |

Para async, pasá un executor que administre la aplicación. Java 21 permite crear uno por tarea virtual; el cliente no lo cierra:

```java
import dev.notifications.EmailNotification;
import dev.notifications.NotificationClient;
import dev.notifications.NotificationValidators;
import dev.notifications.RetryPolicy;
import dev.notifications.SendGridProvider;
import dev.notifications.SendRequest;
import java.time.Duration;
import java.util.concurrent.Executors;

public final class AsyncQuickStart {
    public static void main(String[] args) {
        var email = new EmailNotification("reviewer@example.com", "Build complete", "Ready for review");
        var sendGrid = new SendGridProvider("demo-sendgrid-key", "notifications@example.com");
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try (executor) {
            var asyncClient = NotificationClient.builder()
                    .executor(executor)
                    .registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                    .registerProvider(EmailNotification.CHANNEL, "sendgrid", sendGrid, true)
                    .retryPolicy(new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(30)))
                    .addListener(event -> System.out.println(event.type()))
                    .build();

            var accepted = asyncClient.sendAsync(new SendRequest(email)).join();
            System.out.printf("Accepted by %s%n", accepted.provider());
        }
    }
}
```

`maxAttempts` incluye el primer intento; `3` permite hasta dos reintentos. La espera predeterminada usa backoff exponencial acotado.

## Seguridad y rotación de credenciales

No guardes credenciales reales en el repositorio. En una integración real, la aplicación consumidora es responsable de leerlas desde su gestor de secretos o entorno y pasarlas por la API Java; la librería no las descubre por su cuenta. Evitá registrarlas en logs. Los valores de `Demo` son ficticios y no habilitan acceso a cuentas.

Al rotar una credencial real, actualizá primero el origen seguro en la aplicación y construí un cliente con el nuevo valor; la librería no lee ni recarga secretos automáticamente. Esta versión no establece conexiones reales, por lo que su validación local solo comprueba presencia y formato, no autorización remota.

## Límites deliberados

| No incluido | Motivo |
|---|---|
| HTTP real y SDKs de proveedores | Mantener la prueba reproducible, offline y sin cuentas externas. |
| Confirmación de entrega, plantillas y webhooks | El alcance modela envío y aceptación, no el ciclo de vida posterior del mensaje. |
| Opciones específicas de plataformas y claves reservadas de datos FCM | Se modela el contrato HTTP v1 mínimo de token, notificación y datos string/string. |
| Colas durables, idempotencia y rate limiting | Requieren estado operativo y políticas externas; exceden el objetivo de esta librería didáctica. |
| Slack y publicación a Maven Central | Se priorizan los tres canales requeridos y la verificación local del artefacto. |

Por estas limitaciones, el proyecto es una base técnica para evaluar diseño y extensibilidad, **no un cliente de producción para enviar notificaciones reales**. Ver [DESIGN.md](DESIGN.md) para las decisiones y sus trade-offs.

## Docker offline

```bash
docker build -t notifications-demo .
docker run --rm --network none notifications-demo
```

La imagen ejecuta la misma demo sin cuentas ni red durante el runtime. El build sí necesita acceso inicial a las imágenes base y dependencias Maven, salvo que ya estén disponibles localmente.

## Uso de IA

Se usaron OpenCode (incluyendo Kimi), Codex y DeepSeek para explorar y revisar requisitos, proponer y refinar especificaciones/diseño/tareas, y asistir en la implementación. La persona autora definió el alcance, evaluó las propuestas, tomó las decisiones finales y verificó el comportamiento con tests y documentación oficial de proveedores. La IA fue una herramienta de apoyo; los proveedores de este repositorio son simulaciones y no se enviaron notificaciones reales.
