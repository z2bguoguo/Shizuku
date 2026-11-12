package app.shizuku.connectionprobe;

/** CI-only process with a previous release's main-server name. */
public final class LegacyServerFixture {
    public static void main(String[] args) throws Exception {
        System.out.println("legacy-fixture-ready pid=" + android.os.Process.myPid());
        while (true) Thread.sleep(1000);
    }
}
