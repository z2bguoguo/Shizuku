package rikka.shizuku.server;

import android.os.Build;
import android.system.Os;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Bounded, manager-only log collection. No data is uploaded or sent automatically. */
final class ConnectionDiagnostics {
    private static String redact(String value) {
        return value.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?:-[0-9]+)?", "<token>");
    }

    static String collect(String serviceState) {
        String header = "Manager: " + ServerConstants.MANAGER_APPLICATION_ID
                + "\nAndroid API: " + Build.VERSION.SDK_INT + "\nServer UID: " + Os.getuid() + "\n\n";
        header += serviceState + "\n" + UserServiceDiagnostics.collect() + "\nRecent matching logcat:\n";
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder("logcat", "-d", "-t", "3000", "-v", "brief",
                    "ShizukuServiceStarter:V", "UserServiceManager:V", "UserServiceRecord:V",
                    "Service:V", "ShizukuManager:V", "UiAutomation:V", "UiAutomationConnection:V",
                    "UiAutomationManager:V", "AccessibilityManagerService:W", "AndroidRuntime:E", "*:S")
                    .redirectErrorStream(true).start();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream input = process.getInputStream()) {
                byte[] chunk = new byte[2048];
                int length;
                while (buffer.size() < 48000 && (length = input.read(chunk, 0,
                        Math.min(chunk.length, 48000 - buffer.size()))) > 0) buffer.write(chunk, 0, length);
            }
            String logs = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            // User-service tokens authorize a Binder handoff; never copy them out.
            logs = logs.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?:-[0-9]+)?", "<token>");
            if (logs.trim().isEmpty()) logs = "No matching logcat records. See startup output above.\n";
            return redact(header + logs);
        } catch (Throwable e) {
            return redact(header) + "Unable to collect logcat: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (process != null) process.destroy();
        }
    }
}
