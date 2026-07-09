# Notifications Library

Libreria Java 21 multi-modulo para enrutar notificaciones por canal: `EMAIL`, `SMS` y `PUSH`.

Incluye:
- `notifications-core` con modelos de entrada/salida y el puerto `NotificationSender`.
- `notifications-application` con el servicio central de envio y el builder.
- `notifications-adapters` con providers de ejemplo por canal.
- `notifications-observability-otel` con el adaptador OpenTelemetry opcional.
- `notifications-demo` con las dos variantes de ejecucion:
  - `com.demo.notifications.examples.NotificationExamples` para el flujo sin optimizacion.
  - `com.demo.notifications.examples.NotificationOptimizedMain` para el flujo optimizado con virtual threads.
  - `com.demo.notifications.examples.NotificationObservabilityMain` para el flujo con telemetria conectada.

## Arquitectura hexagonal

La siguiente vista resume la libreria, separando la API publica que un consumidor puede usar de los detalles internos de implementacion.

```mermaid
flowchart LR
    App[Aplicacion consumidora]

    subgraph PublicAPI["API publica / puertos accesibles"]
        NSB["NotificationServiceBuilder"]
        NS["NotificationService"]
        NSender["NotificationSender\n(puerto de salida de envio)"]
        NTelemetryPort["NotificationTelemetryPort\n(puerto de salida de observabilidad)"]
        NTelemetryScope["NotificationTelemetryScope"]
        NRequest["NotificationRequest"]
        NResult["NotificationResult"]
        NChannel["NotificationChannel"]
        NObservation["NotificationTelemetryObservation"]
    end

    subgraph Internal["Interno / no exponer"]
        NValidator["NotificationValidator"]
        Noop["NoopNotificationTelemetryPort"]
        OTel["OpenTelemetryNotificationTelemetryAdapter"]
    end

    subgraph Adapters["Adapters concretos"]
        Grid["GridEmailSender"]
        Mailgun["MailgunEmailSender"]
        Twilio["TwilioSmsSender"]
        Firebase["FirebasePushSender"]
    end

    App --> NSB
    App --> NS
    App --> NRequest
    App --> NResult

    NSB --> NS
    NS --> NSender
    NS --> NTelemetryPort
    NS --> NValidator
    NS --> NObservation
    NTelemetryPort --> NTelemetryScope

    OTel -. implementa .-> NTelemetryPort
    Noop -. implementa .-> NTelemetryPort
    Grid -. implementa .-> NSender
    Mailgun -. implementa .-> NSender
    Twilio -. implementa .-> NSender
    Firebase -. implementa .-> NSender

    classDef public fill:#eaf7ee,stroke:#2c7a4b,stroke-width:1px;
    classDef internal fill:#f4f4f4,stroke:#8a8a8a,stroke-dasharray: 5 5;
    classDef adapter fill:#fff4e5,stroke:#b7791f,stroke-width:1px;

    class NSB,NS,NSender,NTelemetryPort,NTelemetryScope,NRequest,NResult,NChannel,NObservation public;
    class NValidator,Noop,OTel internal;
    class Grid,Mailgun,Twilio,Firebase adapter;
```

Puertos y contratos que quedan accesibles para consumo externo:

- `NotificationServiceBuilder`: punto de composicion para registrar adaptadores y configurar la libreria.
- `NotificationService`: fachada principal para enviar una notificacion.
- `NotificationSender`: puerto de salida que implementan los providers.
- `NotificationTelemetryPort`: puerto de observabilidad agnostico.
- `NotificationTelemetryScope`: scope de observabilidad devuelto por el puerto.
- `NotificationRequest`, `NotificationResult`, `NotificationChannel` y `NotificationTelemetryObservation`: contratos de entrada, salida y observabilidad que cruzan la frontera de la libreria.

### Vista hexagonal

Este diagrama muestra la misma libreria, pero desde la perspectiva hexagonal clasica: entradas por la izquierda, nucleo en el centro y adaptadores de salida por la derecha.

```mermaid
flowchart LR
    App[Aplicacion consumidora]

    subgraph Left["Puertos de entrada"]
        NSB["NotificationServiceBuilder"]
        NS["NotificationService"]
    end

    subgraph Core["Nucleo de la libreria"]
        Request["NotificationRequest"]
        Result["NotificationResult"]
        Channel["NotificationChannel"]
        Observation["NotificationTelemetryObservation"]
        Validator["NotificationValidator"]
    end

    subgraph Right["Puertos de salida y adaptadores"]
        SenderPort["NotificationSender"]
        TelemetryPort["NotificationTelemetryPort"]
        TelemetryScope["NotificationTelemetryScope"]
        Grid["GridEmailSender"]
        Mailgun["MailgunEmailSender"]
        Twilio["TwilioSmsSender"]
        Firebase["FirebasePushSender"]
        OTel["OpenTelemetryNotificationTelemetryAdapter"]
        Noop["NoopNotificationTelemetryPort"]
    end

    App --> NSB
    App --> NS

    NSB --> NS
    NS --> Request
    NS --> Result
    NS --> Channel
    NS --> Observation
    NS --> Validator
    NS --> SenderPort
    NS --> TelemetryPort
    TelemetryPort --> TelemetryScope

    Grid -. implementa .-> SenderPort
    Mailgun -. implementa .-> SenderPort
    Twilio -. implementa .-> SenderPort
    Firebase -. implementa .-> SenderPort
    OTel -. implementa .-> TelemetryPort
    Noop -. implementa .-> TelemetryPort

    classDef core fill:#e8f2ff,stroke:#305f99,stroke-width:1px;
    classDef port fill:#ecfdf3,stroke:#2f855a,stroke-width:1px;
    classDef adapter fill:#fff7ed,stroke:#c05621,stroke-width:1px;

    class NSB,NS,SenderPort,TelemetryPort,TelemetryScope port;
    class Request,Result,Channel,Observation,Validator core;
    class Grid,Mailgun,Twilio,Firebase,OTel,Noop adapter;
```

Los puertos que un consumidor puede implementar o invocar directamente siguen siendo:

- `NotificationServiceBuilder`
- `NotificationService`
- `NotificationSender`
- `NotificationTelemetryPort`
- `NotificationTelemetryScope`
- `NotificationRequest`
- `NotificationResult`
- `NotificationChannel`
- `NotificationTelemetryObservation`

## Requisitos

- Java 21
- Maven 3.9+

## 1. Comandos por perfil

### Flujo normal

Compila, prueba e instala la libreria sin empaquetado ejecutable ni coverage extra.

```bash
mvn clean test
mvn clean install
```

### Perfil `demo-executable`

Genera el jar autocontenido del demo para ejecutar los `main` desde la linea de comandos o Docker.

```bash
mvn -Pdemo-executable -pl notifications-demo -am package
```

Este perfil compila tambien el modulo `notifications-observability-otel`, porque el demo lo incluye dentro del jar final.

El artefacto ejecutable queda en:

```bash
notifications-demo/target/notifications-demo-1.0.0.jar
```

Ese mismo jar contiene los tres ejemplos de ejecucion. Usa `java -jar` para el flujo optimizado por defecto y `java -cp ... <MainClass>` cuando quieras elegir otro `main` de forma explicita.

Ejemplo con observabilidad:

```bash
java -cp notifications-demo/target/notifications-demo-1.0.0.jar com.demo.notifications.examples.NotificationObservabilityMain --channel=email --to=user@example.com --subject=Bienvenido --message="Tu cuenta esta lista."
```

Salida esperada en consola:

```text
NotificationResult[id=..., channel=EMAIL, success=true, provider=SendGrid, message=Notificación enviada correctamente 	, sentAt=...]
```

> Nota: este ejemplo ya usa el adaptador OpenTelemetry. Para exportar trazas a un backend real necesitas ademas un SDK, agente o exporter OTEL. Si no configuras eso, el comando igual imprime el `NotificationResult` en consola.

### Perfil `coverage`

Genera los reportes XML de JaCoCo listos para SonarQube.

```bash
mvn clean verify -Pcoverage
```

Si ademas quieres ejecutar el analisis de SonarQube:

```bash
mvn clean verify -Pcoverage sonar:sonar
```

Ese perfil deja los reportes en `target/site/jacoco/jacoco.xml` por modulo y expone la propiedad `sonar.coverage.jacoco.xmlReportPaths` lista para el scanner.

## 2. Consumir la libreria como dependencia

1. Instala el artefacto en tu repositorio local.

```bash
mvn install
```

2. Agrega las dependencias del modulo que vas a consumir.

```xml
<dependency>
    <groupId>com.demo</groupId>
    <artifactId>notifications-core</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>com.demo</groupId>
    <artifactId>notifications-application</artifactId>
    <version>1.0.0</version>
</dependency>
```

Si quieres reutilizar los providers de ejemplo, agrega tambien `notifications-adapters`.

```xml
<dependency>
    <groupId>com.demo</groupId>
    <artifactId>notifications-adapters</artifactId>
    <version>1.0.0</version>
</dependency>
```

Si quieres conectar OpenTelemetry, agrega el adaptador opcional.

```xml
<dependency>
    <groupId>com.demo</groupId>
    <artifactId>notifications-observability-otel</artifactId>
    <version>1.0.0</version>
</dependency>
```

3. Crea el servicio y registra los remitentes.

```java
import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.providers.email.impl.GridEmailSender;
import com.demo.notifications.providers.email.impl.MailgunEmailSender;
import com.demo.notifications.providers.push.impl.FirebasePushSender;
import com.demo.notifications.providers.sms.impl.TwilioSmsSender;
import com.demo.notifications.observability.otel.OpenTelemetryNotificationTelemetryAdapter;
import com.demo.notifications.services.NotificationService;
import com.demo.notifications.services.NotificationServiceBuilder;

NotificationService service = new NotificationServiceBuilder()
    .telemetry(OpenTelemetryNotificationTelemetryAdapter.usingGlobal())
    .register(new GridEmailSender(
        "demo-sendgrid-api-key",
        "demo.mail.example",
        "noreply@demo.mail.example"))
    .register(new MailgunEmailSender(
        "demo-mailgun-api-key",
        "demo.mail.example",
        "noreply@demo.mail.example"))
    .register(new TwilioSmsSender(
        "demo-twilio-token",
        "AC1234567890",
        "+15551234567"))
    .register(new FirebasePushSender(
        "demo-firebase-api-key",
        "demo-project",
        "service-account.json"))
    .activeProvider(NotificationChannel.EMAIL, "SendGrid")
    .build();
```

4. Construye la solicitud y envia la notificacion.

```java
NotificationResult result = service.send(
    NotificationRequest.email(
        "user@example.com",
        "Bienvenido",
        "Tu cuenta esta lista."));
```

5. Si quieres envio asincrono, usa `sendAsync(...)` o `sendBatchAsync(...)`.

```java
try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
    NotificationService service = new NotificationServiceBuilder()
        .executor(executor)
        .register(new GridEmailSender("api-key", "demo.example", "noreply@demo.example"))
        .build();

    NotificationResult result = service.sendAsync(
        NotificationRequest.sms(
            "+573001234567",
            "Tu codigo es 123456")).join();
}
```

> Nota: `NotificationServiceBuilder` usa virtual threads por defecto. Si quieres un comportamiento sin optimizacion, puedes pasar un executor sincronico como `Runnable::run`.
> Nota: si no llamas a `telemetry(...)`, la libreria usa un `Noop` por defecto y se comporta igual que antes.

> Nota: si registras mas de un provider para el mismo canal, puedes definir el provider activo con `activeProvider(...)` o una cadena de fallback con `fallbackProviders(...)`. Si solo registras uno, se usa automaticamente.
> Nota: el modulo `notifications-demo` genera el jar ejecutable cuando activas el perfil `demo-executable`.

Los comandos de ejecucion de abajo asumen que ya generaste ese jar con el perfil indicado.

## 3. Variante sin optimizacion

La clase `NotificationExamples` usa un executor directo y ejecuta el flujo de forma sincronica.

Ejecutar:

```bash
java -cp notifications-demo/target/notifications-demo-1.0.0.jar com.demo.notifications.examples.NotificationExamples --channel=email --to=user@example.com --subject=Bienvenido --message="Tu cuenta esta lista."
```

## 4. Variante optimizada con virtual threads

La clase `NotificationOptimizedMain` crea un `ExecutorService` con `Executors.newVirtualThreadPerTaskExecutor()` y ademas configura una cadena de fallback para `EMAIL`.

En esta variante:
- Cada peticion corre en un virtual thread independiente.
- El canal puede usar seleccion explicita con `activeProvider(...)` o fallback ordenado con `fallbackProviders(...)`.
- El envio se hace con `sendAsync(...).join()`.

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    NotificationService service = new NotificationServiceBuilder()
        .executor(executor)
        .register(...)
        .register(...)
        .fallbackProviders(NotificationChannel.EMAIL, "SendGrid", "Mailgun")
        .build();

    NotificationResult result = service.sendAsync(request).join();
}
```

### Flujo
```mermaid
sequenceDiagram
    participant App
    participant Service
    participant VT as VirtualThreadExecutor
    participant Sender
    participant Provider

    App->>Service: sendAsync(request)
    Service->>VT: supplyAsync(send)
    VT->>Service: execute task on virtual thread
    Service->>Service: validate + route by channel
    Service->>Sender: send(request)
    Sender->>Provider: blocking call
    Provider-->>Sender: response
    Sender-->>Service: NotificationResult
    Service-->>App: CompletableFuture<NotificationResult>
```

Ejecutar:

```bash
java -jar notifications-demo/target/notifications-demo-1.0.0.jar --channel=sms --to=+573001234567 --message="Tu codigo es 123456"
```

La clase `NotificationObservabilityMain` usa la misma configuracion de envio, pero conecta el adaptador OpenTelemetry. Si solo usas el API, veras la respuesta del envio en consola; para enviar trazas a un backend necesitas ademas un SDK, agente o exporter OTEL.

```bash
java -cp notifications-demo/target/notifications-demo-1.0.0.jar com.demo.notifications.examples.NotificationObservabilityMain --channel=email --to=user@example.com --subject=Bienvenido --message="Tu cuenta esta lista."
```

## 5. Parametros de entrada

| Parametro | Alias | Requerido | Aplica a | Descripcion |
| --- | --- | --- | --- | --- |
| `--channel` | `-c` | Si | Todos | Canal a usar: `email`, `sms` o `push`. |
| `--to` | `-t` | Si | Todos | Destinatario, numero de telefono o token. |
| `--message` | `-m` | Si | Todos | Mensaje principal a enviar. |
| `--subject` | `-s` | No | `email` | Asunto del correo. |
| `--title` | `-l` | No | `push` | Titulo de la notificacion push. |
| `--help` | `-h` | No | Todos | Muestra la ayuda de uso. |

### Ejemplos

Email:

```bash
java -jar notifications-demo/target/notifications-demo-1.0.0.jar --channel=email --to=user@example.com --subject=Bienvenido --message="Tu cuenta esta lista."
```

SMS:

```bash
java -jar notifications-demo/target/notifications-demo-1.0.0.jar --channel=sms --to=+573001234567 --message="Tu codigo es 123456."
```

Push:

```bash
java -jar notifications-demo/target/notifications-demo-1.0.0.jar --channel=push --to=push-token-123456 --title="Nuevo mensaje" --message="Recibiste una notificacion."
```

## 6. Comportamiento interno

- El canal se resuelve por `request.channel()`.
- El sender se busca en un `EnumMap`, asi que el ruteo es de costo constante.
- La capa optimizada no cambia el canal ni el contrato de entrada, solo cambia el executor que atiende la peticion.
- Un mismo canal puede tener varios providers registrados.
- El provider activo se define por configuracion en el builder con `activeProvider(...)`.
- Si un canal tiene varios providers y no se selecciona uno activo ni una politica de fallback, la construccion del servicio falla para evitar ambiguedad.
- Si defines `fallbackProviders(...)`, la libreria intenta los providers en el orden configurado hasta que uno responde con exito.
- `sendAsync(...)` y `sendBatchAsync(...)` propagan el contexto a traves del puerto de telemetria para que el adaptador OTel pueda enlazar spans entre hilos y virtual threads.

## 7. Estado actual de los sender examples

Las clases incluidas en `providers` son implementaciones de ejemplo. Registran resultados y sirven para probar el flujo de la libreria, pero no llaman APIs reales de terceros.

Si necesitas integraciones productivas, reemplaza esas clases por adaptadores que invoquen tus proveedores reales.

## 8. Uso con Docker

El `Dockerfile` del repositorio construye el modulo `notifications-demo`, activa el perfil `demo-executable` y empaqueta el jar ejecutable en una imagen ligera. El contenedor usa `NotificationOptimizedMain` por defecto, pero puedes cambiar el `MAIN_CLASS` para ejecutar otro flujo, incluido el de observabilidad. No necesitas compilar antes en tu maquina, porque la imagen ya hace el `mvn -pl notifications-demo -am -Pdemo-executable -DskipTests package` internamente.

> Importante: esta imagen ejecuta un comando CLI y no un servicio persistente. Si no le pasas argumentos validos, el `main` imprime la ayuda y el contenedor termina. Eso es esperado.

Construir la imagen:

```bash
docker build -t notifications-demo .
```

Ejecutar un ejemplo:

```bash
docker run --rm notifications-demo --channel=email --to=user@example.com --subject=Bienvenido --message="Tu cuenta esta lista."
```

Si necesitas probar otro flujo, solo cambia los argumentos:

```bash
docker run --rm notifications-demo --channel=sms --to=+573001234567 --message="Tu codigo es 123456"
```

Ejecutar el ejemplo de observabilidad:

```bash
docker run --rm \
  -e MAIN_CLASS=com.demo.notifications.examples.NotificationObservabilityMain \
  notifications-demo \
  --channel=email --to=user@example.com --subject=Bienvenido --message="Tu cuenta esta lista."
```

Nota: esta imagen esta pensada para compartir y ejecutar el demo compilado. Para consumo como dependencia Java, sigue siendo mejor publicar los jars de Maven o instalar el parent multi-modulo en un repositorio interno.

Si quieres abrir una shell dentro de la imagen para inspeccionarla manualmente, puedes sobrescribir el `ENTRYPOINT`:

```bash
docker run --rm -it --entrypoint sh notifications-demo
```
