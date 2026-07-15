# Review de la libreria de notificaciones

## Objetivo

Este documento resume el review de la libreria desde dos perspectivas:

1. La del autor de la libreria, defendiendo su utilidad tecnica.
2. La del arquitecto senior, validando SOLID, Clean Architecture, Java puro y patrones de diseno.

Tambien incluye preguntas para escalar la solucion, cambios recomendados para control de threads y puntos concretos donde reforzar observabilidad y logs si la libreria se consume desde una cola, una aplicacion o un portal web.

## Validacion realizada

Se ejecuto `mvn test` y la rama actual compila y pasa pruebas.

## Resumen ejecutivo

La libreria tiene una base solida para evolucionar:

- La separacion conceptual entre `core`, `application`, `adapters` y `observability` es adecuada.
- Los contratos principales son simples e inmutables.
- La observabilidad esta desacoplada mediante un puerto propio y un adaptador OpenTelemetry opcional.
- Ya existe soporte para asincronia y fallback entre providers.

El principal punto de atencion es que la escalabilidad no depende solo de virtual threads. Hoy faltan limites explicitos de concurrencia, ownership claro del executor, mejor propagacion de contexto y mas controles de observabilidad y seguridad en logs.

## Perspectiva 1: como autor de la libreria

Si tuviera que defender la libreria frente a un arquitecto senior, diria lo siguiente:

- La libreria resuelve un problema real: enrutar notificaciones por canal sin acoplar a una tecnologia concreta.
- El nucleo expone pocas abstracciones: `NotificationRequest`, `NotificationResult`, `NotificationSender` y `NotificationTelemetryPort`.
- La composicion se hace con un builder, lo que facilita integrar o reemplazar providers sin tocar el dominio.
- La telemetria no obliga a usar OpenTelemetry; se puede enchufar o dejar en `noop`.
- El fallback entre providers agrega resiliencia sin mover esa decision al consumidor.
- El uso de virtual threads simplifica el manejo de I/O bloqueante cuando el consumidor lo necesita.

En resumen, la libreria ya ofrece un esqueleto razonable para integracion multi-canal, con una curva de adopcion baja.

## Perspectiva 2: mirada del arquitecto senior

### Lo que esta bien alineado

- Hay una separacion de responsabilidades entendible.
- Los objetos de entrada y salida son inmutables.
- El puerto `NotificationSender` favorece extensibilidad.
- La telemetria esta desacoplada del core.
- El fallback y la seleccion de provider son politicas configurables.

### Donde haria preguntas duras

Un arquitecto senior probablemente preguntaria:

- Cual es el volumen esperado por segundo y por canal.
- Cual es el SLA de latencia.
- El envio es best-effort, at-least-once o exactamente-once.
- Se acepta fallback automatico o la falla debe ser inmediata.
- Que timeout aplica por provider y por request.
- Quien controla la vida del executor.
- Como se limita la concurrencia en batch.
- Que contexto debe propagarse desde cola, app o portal web.
- Que campos son PII y no pueden ir en logs.
- Como se manejan reintentos, backpressure y saturacion.

## Hallazgos principales

### 1. El executor tiene ownership poco explicito

El builder crea por defecto un `Executors.newVirtualThreadPerTaskExecutor()`, pero la libreria no deja claro quien lo cierra ni en que momento.

Referencia:

- [NotificationServiceBuilder.java](C:/bin/workspace/DevOps/Agnostic_lib_message/src/main/java/com/demo/notifications/services/NotificationServiceBuilder.java#L27)

Impacto:

- En una app web o en un consumidor de cola, esto puede derivar en recursos no cerrados o en decisiones ocultas de infraestructura.

### 2. `sendBatchAsync` no tiene control de paralelismo

El envio por lotes dispara una tarea asincrona por request sin limitar concurrencia ni capacidad de cola.

Referencia:

- [NotificationService.java](C:/bin/workspace/DevOps/Agnostic_lib_message/src/main/java/com/demo/notifications/services/NotificationService.java#L86)

Impacto:

- Con cargas grandes, el problema deja de ser el codigo del servicio y pasa a ser presion sobre el executor, los providers externos y el sistema que consume la libreria.

### 3. La telemetria existe, pero el contexto de negocio aun es escaso

`NotificationService` crea observaciones utiles, pero todavia no recibe un contexto rico del consumidor.

Referencias:

- [NotificationService.java](C:/bin/workspace/DevOps/Agnostic_lib_message/src/main/java/com/demo/notifications/services/NotificationService.java#L192)
- [OpenTelemetryNotificationTelemetryAdapter.java](C:/bin/workspace/DevOps/Agnostic_lib_message/src/main/java/com/demo/notifications/observability/otel/OpenTelemetryNotificationTelemetryAdapter.java#L40)

Impacto:

- Si el request viene de una cola, un portal o una API, se pierde trazabilidad cruzada si no se agregan `correlationId`, `tenantId`, `requestId` y metadata del transport.

### 4. Los providers de ejemplo exponen datos sensibles en logs

Los adaptadores de ejemplo registran destinatario y mensaje completo.

Referencia:

- [GridEmailSender.java](C:/bin/workspace/DevOps/Agnostic_lib_message/src/main/java/com/demo/notifications/providers/email/impl/GridEmailSender.java#L37)

Impacto:

- En produccion esto puede violar politicas de seguridad, privacidad o compliance.

### 5. La frontera fisica de modulos esta debilitada por el source tree compartido

Aunque conceptualmente hay modulos, el codigo fuente vive en un arbol comun en `src`, y cada modulo filtra lo que compila con `includes`.

Referencias:

- [pom.xml](C:/bin/workspace/DevOps/Agnostic_lib_message/pom.xml#L14)
- [notifications-core/pom.xml](C:/bin/workspace/DevOps/Agnostic_lib_message/notifications-core/pom.xml#L18)
- [notifications-application/pom.xml](C:/bin/workspace/DevOps/Agnostic_lib_message/notifications-application/pom.xml#L18)

Impacto:

- La separacion logica existe, pero la separacion fisica es menos estricta de lo que aparenta.

## Evaluacion por principios

### SOLID

- SRP: `NotificationService` concentra validacion, routing, fallback, telemetria y async.
- OCP: `NotificationValidator` usa `switch` por canal; es correcto hoy, pero crece peor si aparecen mas canales o reglas especiales.
- LSP: la interfaz `NotificationSender` es simple y consistente, aunque los providers de ejemplo devuelven resultados simulados.
- ISP: la interfaz es pequena, lo cual es bueno.
- DIP: el uso de puertos esta bien, pero el executor por defecto creado dentro del builder rompe un poco la inversion de dependencias a nivel de infraestructura.

### Clean Architecture

- Bien: el dominio y los puertos estan relativamente aislados.
- A mejorar: la orquestacion todavia mezcla varias responsabilidades en una sola capa de aplicacion.
- A mejorar: la configuracion de infraestructura debe vivir mas claramente fuera del core.

### Java puro

- Bien: se usa `record`, `Map.copyOf`, `List.copyOf`, `EnumMap`, `CompletableFuture` y `AutoCloseable`.
- A revisar: la asincronia se apoya en virtual threads, pero sin politicas explicitas de capacidad.

### Patrones de diseno

- Ya existe `Builder`.
- Ya existe `Adapter`.
- Falta consolidar `Strategy` para routing, validacion y politicas de fallback.
- Seria util un `Decorator` para observabilidad, masking, retry y metrics.
- Para entornos de alta carga, convendria un `Bulkhead` y, segun el caso, un `Circuit Breaker`.

## Preguntas que haria para escalar la libreria

- Cual es el throughput objetivo por canal.
- Cual es la latencia maxima aceptable por request.
- Se requiere entrega confiable o solo intento best-effort.
- Que politica de retry se espera por provider.
- Los fallbacks deben ejecutarse en serie o en paralelo.
- Hay limites por tenant o por canal.
- Se espera idempotencia a nivel de mensaje.
- Que tamaño maximo de batch es aceptable.
- Quien maneja reintentos: la libreria o el consumidor.
- Que metadatos deben viajar entre capas.
- Que datos nunca pueden aparecer en logs.
- El executor lo provee el consumidor o la libreria debe administrarlo.

## Cambios que realizaria para controlar threads

### 1. Hacer explicito el ownership del executor

Recomendacion:

- Inyectar siempre el `Executor` desde afuera.
- Si la libreria crea un executor, el servicio debe implementar `AutoCloseable`.
- Documentar claramente quien lo cierra.

### 2. Agregar limites de concurrencia

Recomendacion:

- Incluir `maxConcurrentRequests`.
- Incluir `queueCapacity`.
- Definir una politica de rechazo cuando el sistema se sature.
- Agregar `timeout` por request y por provider.

### 3. Limitar el paralelismo del batch

Recomendacion:

- No lanzar todos los envios de un batch al mismo tiempo.
- Usar un limite configurable por batch.
- Si el batch es grande, procesarlo por ventanas.

### 4. Separar pools por tipo de carga

Recomendacion:

- Un pool para requests web.
- Otro para consumidores de cola.
- Otro para procesos batch.

### 5. Incorporar cancelacion y backpressure

Recomendacion:

- Cancelar envios que superen timeout.
- Propagar cancelacion al contexto asincrono.
- Evitar que una saturacion de provider bloquee toda la libreria.

### 6. Mantener virtual threads como optimizacion, no como politica

Recomendacion:

- Las virtual threads son una buena opcion para I/O bloqueante.
- Aun asi, la libreria debe permitir ejecutores alternativos y limites de capacidad.

## Donde reforzaria observabilidad y logs

### En `NotificationService`

Agregar:

- inicio y fin de request,
- duracion total,
- canal,
- provider elegido,
- cantidad de candidatos,
- cantidad de intentos,
- resultado final,
- error final normalizado.

### En `sendWithCandidates`

Agregar:

- intento actual,
- provider,
- tiempo por intento,
- razon de fallo,
- si hubo fallback,
- provider que finalmente resolvio o fallo.

### En el adaptador OpenTelemetry

Agregar atributos como:

- `notifications.correlation_id`,
- `notifications.request_id`,
- `notifications.tenant_id`,
- `notifications.source`,
- `notifications.transport`,
- `notifications.retry.count`,
- `notifications.redelivery.count`.

Referencia:

- [OpenTelemetryNotificationTelemetryAdapter.java](C:/bin/workspace/DevOps/Agnostic_lib_message/src/main/java/com/demo/notifications/observability/otel/OpenTelemetryNotificationTelemetryAdapter.java#L43)

### En los providers

Agregar:

- latencia del proveedor externo,
- status de la integracion,
- timeout,
- excepciones tecnicas,
- codigo de error del proveedor.

Evitar:

- mensaje completo,
- destinatario en claro,
- tokens, api keys o secretos,
- payloads completos.

### Si se consume desde una cola

Registrar:

- `messageId`,
- `queueName`,
- `partition`,
- `offset`,
- `deliveryCount`,
- `redeliveryCount`,
- `ack` / `nack`,
- tiempo de procesamiento.

### Si se consume desde una app

Registrar:

- `correlationId`,
- `requestId`,
- `tenantId`,
- `userId` o su equivalente,
- canal solicitado,
- provider activo,
- fallback usado.

### Si se consume desde un portal web

Registrar:

- ruta HTTP,
- metodo,
- status,
- `traceparent`,
- `correlationId`,
- usuario o tenant enmascarado,
- tiempo total de respuesta.

## Sugerencia de evolucion por fases

### Fase 1: endurecimiento rapido

- Inyectar el executor desde afuera.
- Agregar masking en logs de providers.
- Introducir contexto de negocio en telemetria.
- Definir limites basicos para batch.

### Fase 2: escalabilidad controlada

- Separar routing, validacion y fallback en estrategias.
- Agregar timeouts y politicas de rechazo.
- Medir latencia y tasa de error por provider.
- Fortalecer propagacion de contexto entre hilos.

### Fase 3: resiliencia avanzada

- Bulkhead por canal o por proveedor.
- Circuit breaker por integracion externa.
- Reintentos con backoff.
- Correlacion completa entre cola, app, web y provider.

## Conclusiones

La libreria ya tiene una forma correcta para una solucion agnostica de mensajeria, y su propuesta tecnica se puede defender bien. El valor principal esta en el desacoplamiento, la composicion por builder y la abstraccion de observabilidad.

Sin embargo, si la meta es escalarla a escenarios serios de produccion, faltan tres cosas clave:

1. Control explicito de threads y capacidad.
2. Mejor contexto de observabilidad desde el consumidor.
3. Logs seguros y estructurados, sin PII sensible.

Si se corrigen esos puntos, la libreria puede evolucionar de un buen ejemplo arquitectonico a una base mucho mas robusta para integraciones reales.
