package app.shizuku.connectionprobe;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import rikka.shizuku.Shizuku;

public class ProbeActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean started, verifying, restarting;
    private volatile boolean complete;
    private int rounds, previousPid;
    private boolean expectNewProcess;
    private String lastAutomation;
    private long startedAt;
    private Shizuku.UserServiceArgs args;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (complete) return;
            if (verifying) { finishResult("FAIL duplicate connection callback"); return; }
            verifying = true;
            main.postDelayed(() -> new Thread(() -> verifyService(binder), "probe-client").start(), rounds == 0 ? 700 : 50);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            if (restarting) {
                restarting = false;
                // Allow the SDK to finish removing its dead connection cache entry.
                main.postDelayed(() -> bindAgain(), 100);
            } else if (!complete) finishResult("FAIL unexpected service disconnect");
        }
    };

    private void verifyService(IBinder binder) {
        Parcel request = Parcel.obtain(), response = Parcel.obtain();
        try {
            if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION, request, response, 0)) throw new IllegalStateException("probe transaction rejected");
            response.readException();
            int uid = response.readInt(), pid = response.readInt(), caller = response.readInt();
            if (uid != 2000) throw new IllegalStateException("wrong service uid: " + uid);
            if (caller != android.os.Process.myUid()) throw new IllegalStateException("caller identity changed: " + caller);
            if (previousPid != 0 && ((pid != previousPid) != expectNewProcess)) throw new IllegalStateException("wrong service reuse/restart behavior");
            previousPid = pid;
            request.setDataSize(0); request.setDataPosition(0);
            response.setDataSize(0); response.setDataPosition(0);
            if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION + 1, request, response, 0)) throw new IllegalStateException("automation transaction rejected");
            response.readException();
            String automation = response.readString();
            if (automation == null || !automation.startsWith("PASS ")) throw new IllegalStateException("User-service automation: " + automation);
            lastAutomation = automation;
            if (rounds == 0) {
                request.setDataSize(0); request.setDataPosition(0);
                response.setDataSize(0); response.setDataPosition(0);
                if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION + 2, request, response, 0)) throw new IllegalStateException("error probe rejected");
                try { response.readException(); throw new AssertionError("service exception was lost"); }
                catch (IllegalStateException expected) {
                    if (!"probe expected transaction failure".equals(expected.getMessage())) throw expected;
                }
            }
            main.post(this::nextRound);
        } catch (Throwable e) { finishResult("FAIL round=" + rounds + " " + android.util.Log.getStackTraceString(e)); }
        finally { request.recycle(); response.recycle(); }
    }

    private void nextRound() {
        if (complete) return;
        verifying = false;
        rounds++;
        if (rounds == 12) {
            try {
                Shizuku.bindUserService(new Shizuku.UserServiceArgs(new ComponentName(getPackageName(),
                        "app.shizuku.connectionprobe.MissingService")).daemon(false)
                        .tag("expected-bootstrap-failure").processNameSuffix("missing"), new ServiceConnection() {
                    @Override public void onServiceConnected(ComponentName n, IBinder b) { }
                    @Override public void onServiceDisconnected(ComponentName n) { }
                });
                finishResult("PASS uid=2000 rounds=" + rounds + " callerIdentity=preserved automation={"
                        + lastAutomation + "} elapsedMs=" + (android.os.SystemClock.uptimeMillis() - startedAt));
            } catch (Throwable e) { finishResult("FAIL " + android.util.Log.getStackTraceString(e)); }
            return;
        }
        try {
            expectNewProcess = rounds % 3 == 0;
            if (expectNewProcess) {
                restarting = true;
                Shizuku.unbindUserService(args, connection, true);
            } else {
                if (rounds % 3 == 2) Shizuku.unbindUserService(args, connection, false);
                bindAgain();
            }
        } catch (Throwable e) { finishResult("FAIL " + android.util.Log.getStackTraceString(e)); }
    }
    private void bindAgain() {
        if (complete) return;
        try { Shizuku.bindUserService(args, connection); }
        catch (Throwable e) { finishResult("FAIL " + android.util.Log.getStackTraceString(e)); }
    }

    private final Shizuku.OnBinderReceivedListener received = this::startProbe;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        android.widget.TextView content = new android.widget.TextView(this);
        content.setText("Shizuku user-service compatibility probe");
        content.setTextSize(24);
        content.setPadding(24, 48, 24, 24);
        setContentView(content);
        new File(getFilesDir(), "result.txt").delete();
        Shizuku.addBinderReceivedListenerSticky(received);
        main.postDelayed(() -> { if (!complete) finishResult("FAIL timed out round=" + rounds + "; binder=" + Shizuku.pingBinder()); }, 60000);
    }
    private synchronized void startProbe() {
        if (started) return;
        started = true;
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) throw new IllegalStateException("probe permission denied");
            startedAt = android.os.SystemClock.uptimeMillis();
            args = new Shizuku.UserServiceArgs(new ComponentName(this, ProbeService.class))
                    .daemon(false).processNameSuffix("probe").debuggable(false).version(2);
            bindAgain();
        } catch (Throwable e) { finishResult("FAIL " + android.util.Log.getStackTraceString(e)); }
    }
    private synchronized void finishResult(String text) {
        if (complete) return;
        complete = true;
        try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "result.txt"))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { android.util.Log.e("ConnectionProbe", "cannot write result", e); }
        android.util.Log.i("ConnectionProbe", text);
    }
}
