package com.demo.notifications.examples;

import java.io.PrintStream;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.providers.email.impl.GridEmailSender;
import com.demo.notifications.providers.email.impl.MailgunEmailSender;
import com.demo.notifications.providers.push.impl.FirebasePushSender;
import com.demo.notifications.providers.sms.impl.TwilioSmsSender;
import com.demo.notifications.services.NotificationService;
import com.demo.notifications.services.NotificationServiceBuilder;

public final class NotificationOptimizedMain {

    private NotificationOptimizedMain() {
    }

    public static void main(String[] args) {
        int exitCode = execute(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int execute(String[] args, PrintStream out, PrintStream err) {
        NotificationExamples.CliOptions options;
        try {
            options = NotificationExamples.CliOptions.parse(args);
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            err.println(NotificationExamples.USAGE);
            return 1;
        }

        if (options.showHelp()) {
            out.println(NotificationExamples.USAGE);
            return 0;
        }

        NotificationRequest request;
        try {
            request = options.toRequest();
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            err.println(NotificationExamples.USAGE);
            return 1;
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            NotificationService service = buildService(executor);
            // Virtual thread por petición: cada envío corre en un hilo ligero independiente.
            NotificationResult result = service.sendAsync(request).join();
            out.println(result);
            return 0;
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            err.println(cause.getMessage());
            err.println(NotificationExamples.USAGE);
            return 1;
        }
    }

    static NotificationService buildService(Executor executor) {
        return new NotificationServiceBuilder()
            .executor(executor)
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
            .fallbackProviders(NotificationChannel.EMAIL, "SendGrid", "Mailgun")
            .build();
    }
}
