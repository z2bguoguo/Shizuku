package rikka.shizuku.server;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class UserServiceDiagnosticsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void capturesBootstrapStderrAndExitCodeBeforeBinderExists() throws Exception {
        File directory = temp.newFolder("service logs");
        File log = UserServiceDiagnostics.begin(directory, "example.client", "MissingService", 10123);
        String command = UserServiceDiagnostics.capture("(sh -c 'echo BOOTSTRAP_FAILURE >&2; exit 23')&", log);
        Process process = new ProcessBuilder("sh", "-c", command).start();
        assertTrue(process.waitFor(3, TimeUnit.SECONDS));
        String output = "";
        for (int i = 0; i < 100; i++) {
            output = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
            if (output.contains("process-exit=23")) break;
            Thread.sleep(10);
        }
        assertTrue(output, output.contains("BOOTSTRAP_FAILURE"));
        assertTrue(output, output.contains("process-exit=23"));
        assertTrue(UserServiceDiagnostics.collect(directory).contains("MissingService"));
    }

    @Test public void emptyCollectionHasAnActionableMessageAndRetentionIsBounded() throws Exception {
        File directory = temp.newFolder();
        assertTrue(UserServiceDiagnostics.collect(directory).contains("Reproduce a connection attempt"));
        for (int i = 0; i < 15; i++) UserServiceDiagnostics.begin(directory, "example.client", "Service", 10123);
        assertTrue(directory.listFiles().length <= 8);
    }
}
