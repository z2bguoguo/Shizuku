package rikka.shizuku.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;

/** Keep a small set of private startup logs even if app_process exits before Binder setup. */
public final class UserServiceDiagnostics {
    private static final AtomicLong NEXT = new AtomicLong();

    private static File directory() {
        return new File("/data/local/tmp/" + ServerConstants.MANAGER_APPLICATION_ID
                + "-" + android.system.Os.getuid() + "-user-service-logs");
    }

    static File begin(String packageName, String className, int uid) throws IOException {
        return begin(directory(), packageName, className, uid);
    }

    static synchronized File begin(File directory, String packageName, String className, int uid) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create startup log directory");
        directory.setReadable(false, false);
        directory.setWritable(false, false);
        directory.setExecutable(false, false);
        directory.setReadable(true, true);
        directory.setWritable(true, true);
        directory.setExecutable(true, true);
        File[] old = directory.listFiles((dir, name) -> name.endsWith(".log"));
        if (old != null) {
            Arrays.sort(old, Comparator.comparingLong(File::lastModified).reversed());
            for (int i = 7; i < old.length; i++) old[i].delete();
        }
        File log = new File(directory, System.currentTimeMillis() + "-" + NEXT.incrementAndGet() + ".log");
        try (FileOutputStream out = new FileOutputStream(log)) {
            out.write(("package=" + packageName + " class=" + className + " callerUid=" + uid + "\n").getBytes(StandardCharsets.UTF_8));
        }
        log.setReadable(false, false);
        log.setWritable(false, false);
        log.setReadable(true, true);
        log.setWritable(true, true);
        return log;
    }

    static String capture(String command, File log) {
        if (!command.startsWith("(") || !command.endsWith(")&")) throw new IllegalArgumentException("Unexpected user-service command");
        String body = command.substring(1, command.length() - 2);
        String path = "'" + log.getAbsolutePath().replace("'", "'\\''") + "'";
        return "(" + body + "; result=$?; printf '\\nprocess-exit=%s\\n' \"$result\") >> " + path + " 2>&1 &";
    }

    static String collect() { return collect(directory()); }

    static String collect(File directory) {
        File[] logs = directory.listFiles((dir, name) -> name.endsWith(".log"));
        if (logs == null || logs.length == 0) {
            return "User-service startup logs: none. Reproduce a connection attempt after restarting this build.\n";
        }
        Arrays.sort(logs, Comparator.comparingLong(File::lastModified).reversed());
        StringBuilder result = new StringBuilder("User-service startup logs: " + logs.length + "\n");
        for (int i = 0; i < Math.min(logs.length, 4); i++) {
            result.append("\n--- ").append(logs[i].getName()).append(" ---\n");
            try (RandomAccessFile file = new RandomAccessFile(logs[i], "r")) {
                file.seek(Math.max(0, file.length() - 8000));
                byte[] bytes = new byte[(int) (file.length() - file.getFilePointer())];
                file.readFully(bytes);
                result.append(new String(bytes, StandardCharsets.UTF_8));
            } catch (IOException e) { result.append("Cannot read startup log: ").append(e.getMessage()); }
        }
        return result.toString();
    }
}
