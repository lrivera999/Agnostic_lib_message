package com.demo.notifications.examples;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.services.NotificationService;

public final class NotificationMultichannelMain {

    private NotificationMultichannelMain() {
    }

    public static void main(String[] args) {
        int exitCode = execute(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int execute(String[] args, PrintStream out, PrintStream err) {
        if (isHelpRequested(args)) {
            out.println(USAGE);
            return 0;
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            NotificationService service = NotificationOptimizedMain.buildService(executor);
            List<NotificationRequest> requests = List.of(
                NotificationRequest.email(
                    "user@example.com",
                    "Bienvenido",
                    "Tu cuenta esta lista."),
                NotificationRequest.sms(
                    "+573001234567",
                    "Tu codigo es 123456."),
                NotificationRequest.push(
                    "push-token-123456",
                    "Nuevo mensaje",
                    "Recibiste una notificacion."));

            out.println("[multicanal] enviando 3 notificaciones en paralelo");
            List<NotificationResult> results = service.sendBatchAsync(requests).join();
            results.forEach(result -> out.println(result));
            return 0;
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            err.println(cause.getMessage());
            err.println(USAGE);
            return 1;
        }
    }

    private static boolean isHelpRequested(String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }

        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }

        return false;
    }

    private static final String USAGE = """
        Uso:
          java -cp notifications-demo/target/notifications-demo-1.0.0.jar com.demo.notifications.examples.NotificationMultichannelMain

        Descripcion:
          Envía una notificación EMAIL, una SMS y una PUSH en la misma ejecución.
        """;
}
