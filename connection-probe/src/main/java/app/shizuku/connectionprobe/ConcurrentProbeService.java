package app.shizuku.connectionprobe;

import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Persistent UiAutomation client: concurrent tests must not disconnect between reads. */
public class ConcurrentProbeService extends Binder {
    private final HandlerThread worker = new HandlerThread("concurrent-automation");
    private UiAutomation automation;
    private synchronized UiAutomation connect() throws Exception {
        if (automation == null) {
            worker.start();
            Object connection = Class.forName("android.app.UiAutomationConnection").getDeclaredConstructor().newInstance();
            UiAutomation candidate = (UiAutomation) UiAutomation.class.getConstructor(Looper.class,
                    Class.forName("android.app.IUiAutomationConnection")).newInstance(worker.getLooper(), connection);
            UiAutomation.class.getMethod("connect", int.class).invoke(candidate, 1);
            automation = candidate;
        }
        return automation;
    }
    private String inspect() throws Exception {
        UiAutomation service = connect();
        AccessibilityNodeInfo root = null;
        long until = android.os.SystemClock.uptimeMillis() + 3000;
        while (root == null && android.os.SystemClock.uptimeMillis() < until) {
            root = service.getRootInActiveWindow(); if (root == null) Thread.sleep(50);
        }
        if (root == null) throw new IllegalStateException("Concurrent active window missing");
        String pkg;
        try { pkg = String.valueOf(root.getPackageName()); } finally { root.recycle(); }
        if (!pkg.equals("app.shizuku.connectionprobe") && !pkg.equals("app.shizuku.connectionpeer")) {
            throw new IllegalStateException("Unexpected foreground package " + pkg);
        }
        Bitmap bitmap = service.takeScreenshot();
        if (bitmap == null) throw new IllegalStateException("Concurrent screenshot missing");
        try { return "window=" + pkg + " screenshot=" + bitmap.getWidth() + "x" + bitmap.getHeight(); }
        finally { bitmap.recycle(); }
    }
    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        try {
            if (code == FIRST_CALL_TRANSACTION) {
                connect(); reply.writeNoException(); reply.writeInt(android.os.Process.myPid()); return true;
            }
            if (code == FIRST_CALL_TRANSACTION + 1) {
                String result = inspect(); reply.writeNoException(); reply.writeString(result); return true;
            }
            if (code == FIRST_CALL_TRANSACTION + 2) {
                Class<?> input = Class.forName("android.hardware.input.InputManagerGlobal");
                Object inputManager = input.getMethod("getInstance").invoke(null);
                int[] devices = (int[]) input.getMethod("getInputDeviceIds").invoke(inputManager);
                if (devices == null) throw new IllegalStateException("Input-device listener registration failed");
                UiAutomation service = connect();
                android.accessibilityservice.AccessibilityServiceInfo info = service.getServiceInfo();
                info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                service.setServiceInfo(info);
                long down = android.os.SystemClock.uptimeMillis();
                if (!service.injectInputEvent(new KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0), true)) {
                    throw new IllegalStateException("Queued input-down injection was rejected");
                }
                if (!service.injectInputEvent(new KeyEvent(down, android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0), true)) {
                    throw new IllegalStateException("Queued input-up injection was rejected");
                }
                reply.writeNoException(); reply.writeString("mutation-complete"); return true;
            }
            if (code == FIRST_CALL_TRANSACTION + 3) {
                UiAutomation service = connect();
                int id = (Integer) UiAutomation.class.getMethod("getConnectionId").invoke(service);
                Class<?> interactions = Class.forName("android.view.accessibility.AccessibilityInteractionClient");
                Object instance = interactions.getMethod("getInstance").invoke(null);
                Object connection = interactions.getMethod("getConnection", int.class).invoke(instance, id);
                android.graphics.Path path = new android.graphics.Path(); path.moveTo(100, 300); path.lineTo(101, 301);
                android.accessibilityservice.GestureDescription gesture = new android.accessibilityservice.GestureDescription.Builder()
                        .addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 80)).build();
                Class<?> generator = Class.forName("android.accessibilityservice.GestureDescription$MotionEventGenerator");
                java.lang.reflect.Method generate = generator.getDeclaredMethod("getGestureStepsFromGestureDescription", android.accessibilityservice.GestureDescription.class, int.class);
                generate.setAccessible(true); Object steps = generate.invoke(null, gesture, 20);
                Class<?> slice = Class.forName("android.content.pm.ParceledListSlice");
                Object packed = slice.getConstructor(java.util.List.class).newInstance(steps);
                // All clients deliberately reuse the same local ID; the broker must remap it.
                Class.forName("android.accessibilityservice.IAccessibilityServiceConnection")
                        .getMethod("dispatchGesture", int.class, slice, int.class).invoke(connection, 7, packed, 0);
                reply.writeNoException(); reply.writeString("gesture-complete"); return true;
            }
            if (code == FIRST_CALL_TRANSACTION + 4) {
                IBinder broker = (IBinder) Class.forName("moe.shizuku.starter.concurrent.BrokerDiscovery", true, ClassLoader.getSystemClassLoader()).getMethod("get").invoke(null);
                Parcel request = Parcel.obtain(), response = Parcel.obtain();
                try {
                    request.writeInterfaceToken("rikka.shizuku.concurrent.AutomationHub");
                    if (!broker.transact(4, request, response, 0)) throw new IllegalStateException("Broker stats unsupported");
                    response.readException(); String stats = response.readString();
                    reply.writeNoException(); reply.writeString(stats); return true;
                } finally { request.recycle(); response.recycle(); }
            }
            if (code == rikka.shizuku.ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy) {
                try { if (automation != null) UiAutomation.class.getMethod("disconnect").invoke(automation); }
                catch (Exception ignored) { /* Destruction comes from the server UID, not the app that connected. */ }
                finally { worker.quitSafely(); System.exit(0); }
                return true;
            }
        } catch (Exception error) {
            error.printStackTrace(System.err);
            throw new IllegalStateException("Concurrent probe failed: " + error, error);
        }
        return false;
    }
}
