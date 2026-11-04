# Notificaciones multicanal

Este vocabulario separa el contenido que se quiere comunicar, el medio elegido y el resultado que confirma un proveedor.

## Lenguaje

**Canal (Channel)**:
Medio de comunicación por el que se envía una notificación, como email, SMS o push.
_Avoid_: Proveedor.

**Proveedor (Provider)**:
Servicio que acepta y procesa solicitudes de envío para uno o más canales, como SendGrid, Mailgun, Twilio o FCM.
_Avoid_: Canal, integración (cuando se usa como sinónimo del servicio).

**Notificación (Notification)**:
Contenido dirigido a un destinatario por un canal. Incluye los datos propios de ese canal, como el asunto de un email o el título de un push.
_Avoid_: Intento de envío, resultado.

**Proveedor predeterminado (Default provider)**:
Proveedor seleccionado para un canal cuando quien envía no elige uno explícitamente.
_Avoid_: Proveedor único (puede haber otros proveedores registrados para el mismo canal).

**Intento de envío (Delivery attempt)**:
Una invocación a un proveedor para procesar una notificación. Un reintento es un nuevo intento de la misma notificación.
_Avoid_: Notificación.

**Aceptación del proveedor (Provider acceptance)**:
Confirmación de que el proveedor recibió la solicitud para procesarla. No confirma que el destinatario la haya recibido o leído.
_Avoid_: Entrega, envío entregado.

**Entrega final (End-user delivery)**:
Recepción de la notificación por el destino final. Solo se conoce si el proveedor ofrece y comunica esa confirmación.
_Avoid_: Aceptación del proveedor.

**Fallo transitorio (Transient failure)**:
Fallo para el que existe evidencia de que corresponde volver a intentar con el mismo proveedor; no incluye resultados cuya aceptación es desconocida.
_Avoid_: Error (sin clasificación), fallo permanente.

**Aceptación desconocida (Unknown acceptance)**:
Resultado de un intento que no permite determinar si el proveedor aceptó la notificación, por ejemplo ante un timeout ambiguo.
_Avoid_: Rechazo confirmado, fallo transitorio.
