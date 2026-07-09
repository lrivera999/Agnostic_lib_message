package com.demo.notifications.examples;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.core.exceptions.NotificationException;
import com.demo.notifications.providers.email.impl.GridEmailSender;
import com.demo.notifications.providers.email.impl.MailgunEmailSender;
import com.demo.notifications.providers.push.impl.FirebasePushSender;
import com.demo.notifications.providers.sms.impl.TwilioSmsSender;
import com.demo.notifications.services.NotificationService;
import com.demo.notifications.services.NotificationServiceBuilder;

public final class NotificationExamples {

    static final String USAGE = """
        Uso:
          java -jar notifications-demo-1.0.0.jar --channel=<email|sms|push> --to=<destinatario> --message=<mensaje> [--subject=<asunto>] [--title=<titulo>]

        Ejemplos:
          java -jar notifications-demo-1.0.0.jar --channel=email --to=user@example.com --subject=Bienvenido --message="Tu cuenta esta lista."
          java -jar notifications-demo-1.0.0.jar --channel=sms --to=+573001234567 --message="Tu codigo es 123456."
          java -jar notifications-demo-1.0.0.jar --channel=push --to=push-token-123456 --title="Nuevo mensaje" --message="Recibiste una notificacion."

        Atajos:
          -c = --channel
          -t = --to
          -m = --message
          -s = --subject
          -l = --title
          -h = --help
        """;

    private NotificationExamples() {
    }

    public static void main(String[] args) {
        int exitCode = execute(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int execute(String[] args, PrintStream out, PrintStream err) {
        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            err.println(USAGE);
            return 1;
        }

        if (options.showHelp()) {
            out.println(USAGE);
            return 0;
        }

        try {
            NotificationResult result = buildService().send(options.toRequest());
            out.println(result);
            return 0;
        } catch (NotificationException ex) {
            err.println(ex.getMessage());
            err.println(USAGE);
            return 1;
        }
    }

    static NotificationService buildService() {
        return new NotificationServiceBuilder()
            .executor(Runnable::run)
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
    }

    record CliOptions(
        boolean showHelp,
        String channel,
        String recipient,
        String message,
        String subject,
        String title) {

        static CliOptions help() {
            return new CliOptions(true, null, null, null, null, null);
        }

        static CliOptions parse(String[] args) {
            if (args == null || args.length == 0) {
                return help();
            }

            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i++) {
                String token = args[i];
                if (token == null || token.isBlank()) {
                    continue;
                }
                if ("--help".equals(token) || "-h".equals(token)) {
                    return help();
                }
                if (!token.startsWith("-")) {
                    throw new IllegalArgumentException("Argumento no reconocido: " + token);
                }

                String option = token.startsWith("--") ? token.substring(2) : token.substring(1);
                int equalsIndex = option.indexOf('=');
                String rawKey = equalsIndex >= 0 ? option.substring(0, equalsIndex) : option;
                String key = normalizeKey(rawKey);
                if (!isSupportedKey(key)) {
                    throw new IllegalArgumentException("Argumento no reconocido: " + token);
                }
                String value = equalsIndex >= 0 ? option.substring(equalsIndex + 1) : readNextValue(args, ++i, token);

                if ("help".equals(key)) {
                    return help();
                }

                values.put(key, value);
            }

            return new CliOptions(
                false,
                require(values, "channel"),
                require(values, "to"),
                require(values, "message"),
                values.get("subject"),
                values.get("title"));
        }

        NotificationRequest toRequest() {
            NotificationChannel notificationChannel = parseChannel(channel);
            return switch (notificationChannel) {
                case EMAIL -> NotificationRequest.email(recipient, subject, message);
                case SMS -> NotificationRequest.sms(recipient, message);
                case PUSH -> NotificationRequest.push(recipient, title, message);
            };
        }
    }

    private static String normalizeKey(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "c" -> "channel";
            case "t" -> "to";
            case "m" -> "message";
            case "s" -> "subject";
            case "l" -> "title";
            case "h" -> "help";
            case "channel", "to", "message", "subject", "title", "help" -> key.toLowerCase(Locale.ROOT);
            default -> key.toLowerCase(Locale.ROOT);
        };
    }

    private static boolean isSupportedKey(String key) {
        return switch (key) {
            case "channel", "to", "message", "subject", "title", "help" -> true;
            default -> false;
        };
    }

    private static String require(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta el argumento requerido: --" + key);
        }
        return value;
    }

    private static String readNextValue(String[] args, int index, String token) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Falta el valor para " + token);
        }
        String value = args[index];
        if (value == null || value.isBlank() || value.startsWith("-")) {
            throw new IllegalArgumentException("Falta el valor para " + token);
        }
        return value;
    }

    private static NotificationChannel parseChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("El canal de notificacion es obligatorio");
        }

        return switch (channel.trim().toLowerCase(Locale.ROOT)) {
            case "email" -> NotificationChannel.EMAIL;
            case "sms" -> NotificationChannel.SMS;
            case "push" -> NotificationChannel.PUSH;
            default -> throw new IllegalArgumentException("Canal no soportado: " + channel);
        };
    }
}
