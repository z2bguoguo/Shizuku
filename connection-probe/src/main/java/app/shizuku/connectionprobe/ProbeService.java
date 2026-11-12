package app.shizuku.connectionprobe;

import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Binder;
import android.os.Parcel;

public class ProbeService extends Binder {
    public ProbeService(android.content.Context context) {
        String processName = android.app.Application.getProcessName();
        if (processName != null) {
            throw new IllegalStateException("Synthetic framework bind state leaked into service: " + processName);
        }
        if (context.getApplicationContext() == null) {
            throw new IllegalStateException("Application context was not initialized");
        }
    }
    private String exerciseAutomation() throws Exception {
        HandlerThread worker = new HandlerThread("probe-automation");
        worker.start();
        UiAutomation automation = null;
        try {
            Object connection = Class.forName("android.app.UiAutomationConnection")
                    .getDeclaredConstructor().newInstance();
            automation = (UiAutomation) UiAutomation.class.getConstructor(Looper.class,
                    Class.forName("android.app.IUiAutomationConnection"))
                    .newInstance(worker.getLooper(), connection);
            UiAutomation.class.getMethod("connect", int.class).invoke(automation, 1);
            AccessibilityNodeInfo root = null;
            long deadline = android.os.SystemClock.uptimeMillis() + 5000;
            while (root == null && android.os.SystemClock.uptimeMillis() < deadline) {
                root = automation.getRootInActiveWindow();
                if (root == null) Thread.sleep(100);
            }
            if (root == null) throw new IllegalStateException("UiAutomation returned no active window");
            String pkg;
            try { pkg = String.valueOf(root.getPackageName()); }
            finally { root.recycle(); }
            if (!"app.shizuku.connectionprobe".equals(pkg)) {
                throw new IllegalStateException("Unexpected foreground window: " + pkg);
            }
            Bitmap bitmap = automation.takeScreenshot();
            if (bitmap == null) throw new IllegalStateException("UiAutomation returned no screenshot");
            try {
                if (bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                    throw new IllegalStateException("Empty screenshot");
                }
                return "window=" + pkg + " screenshot=" + bitmap.getWidth() + "x" + bitmap.getHeight();
            } finally { bitmap.recycle(); }
        } finally {
            if (automation != null) {
                try { UiAutomation.class.getMethod("disconnect").invoke(automation); }
                catch (Exception ignored) { }
            }
            worker.quitSafely();
        }
    }
    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        if (code == FIRST_CALL_TRANSACTION) {
            reply.writeNoException();
            reply.writeInt(android.os.Process.myUid());
            reply.writeInt(android.os.Process.myPid());
            reply.writeInt(Binder.getCallingUid());
            return true;
        }
        if (code == FIRST_CALL_TRANSACTION + 1) {
            String result;
            try { result = "PASS " + exerciseAutomation(); }
            catch (Throwable error) {
                result = "FAIL " + android.util.Log.getStackTraceString(error);
                System.err.println("stage=automation-probe failed");
                error.printStackTrace(System.err);
            }
            reply.writeNoException();
            reply.writeString(result);
            return true;
        }
        if (code == FIRST_CALL_TRANSACTION + 2) {
            throw new IllegalStateException("probe expected transaction failure");
        }
        if (code == rikka.shizuku.ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy) {
            System.exit(0);
            return true;
        }
        return false;
    }
}
