package com.demo.notifications.examples;

import java.io.PrintStream;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.observability.otel.OpenTelemetryNotificationTelemetryAdapter;
import com.demo.notifications.services.NotificationService;

public final class NotificationObservabilityMain {

    private NotificationObservabilityMain() {
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
            NotificationService service = NotificationOptimizedMain.buildService(
                executor,
                OpenTelemetryNotificationTelemetryAdapter.usingGlobal());
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
}
