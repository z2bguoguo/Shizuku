package rikka.shizuku.server;

import android.os.Build;
import android.system.Os;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Bounded, manager-only log collection. No data is uploaded or sent automatically. */
final class ConnectionDiagnostics {
    static String collect() {
        String header = "Manager: " + ServerConstants.MANAGER_APPLICATION_ID
                + "\nAndroid API: " + Build.VERSION.SDK_INT + "\nServer UID: " + Os.getuid() + "\n\n";
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder("logcat", "-d", "-t", "400", "-v", "brief",
                    "ShizukuServiceStarter:V", "UserServiceManager:V", "UserServiceRecord:V",
                    "Service:V", "ShizukuManager:V", "AndroidRuntime:E", "*:S")
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
            return header + logs;
        } catch (Throwable e) {
            return header + "Unable to collect logs: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (process != null) process.destroy();
        }
    }
}
