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
    private boolean started;
    private volatile boolean complete;
    private long startedAt;
    private Shizuku.UserServiceArgs args;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            Parcel request = Parcel.obtain();
            Parcel response = Parcel.obtain();
            try {
                if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION, request, response, 0)) throw new IllegalStateException("probe transaction rejected");
                response.readException();
                int uid = response.readInt();
                if (uid != 2000) throw new IllegalStateException("wrong service uid: " + uid);
                // Exercise a process that dies before Binder setup; diagnostics must
                // still expose its actual class-loading exception and exit status.
                Shizuku.bindUserService(new Shizuku.UserServiceArgs(new ComponentName(
                        ProbeActivity.this.getPackageName(), "app.shizuku.connectionprobe.MissingService"))
                        .daemon(false).tag("expected-bootstrap-failure").processNameSuffix("missing"),
                        new ServiceConnection() {
                            @Override public void onServiceConnected(ComponentName n, IBinder b) { }
                            @Override public void onServiceDisconnected(ComponentName n) { }
                        });
                finishResult("PASS uid=" + uid + " elapsedMs=" + (android.os.SystemClock.uptimeMillis() - startedAt));
            } catch (Throwable e) { finishResult("FAIL " + android.util.Log.getStackTraceString(e)); }
            finally { request.recycle(); response.recycle(); }
        }
        @Override public void onServiceDisconnected(ComponentName name) { finishResult("FAIL service disconnected"); }
    };
    private final Shizuku.OnBinderReceivedListener received = this::startProbe;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        new File(getFilesDir(), "result.txt").delete();
        Shizuku.addBinderReceivedListenerSticky(received);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!complete) finishResult("FAIL timed out; binder=" + Shizuku.pingBinder());
        }, 15000);
    }
    private synchronized void startProbe() {
        if (started) return;
        started = true;
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) throw new IllegalStateException("probe permission denied");
            startedAt = android.os.SystemClock.uptimeMillis();
            args = new Shizuku.UserServiceArgs(new ComponentName(this, ProbeService.class))
                    .daemon(false).processNameSuffix("probe").debuggable(false).version(1);
            Shizuku.bindUserService(args, connection);
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
