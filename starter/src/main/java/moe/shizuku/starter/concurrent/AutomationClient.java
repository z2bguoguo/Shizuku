package moe.shizuku.starter.concurrent;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Hooks only this fork's privileged user-service process, before client class initialization. */
public final class AutomationClient {
    private static final IBinder lifetime = new Binder();
    private static IBinder hub;
    private AutomationClient() { }

    public static void install(IBinder broker) throws Exception {
        if (hub != null) return;
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Field cacheField = sm.getDeclaredField("sCache"); cacheField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String, IBinder> cache = (Map<String, IBinder>) cacheField.get(null);
        Method getService = sm.getMethod("getService", String.class);
        IBinder original = (IBinder) getService.invoke(null, "accessibility");
        Class<?> type = Class.forName("android.view.accessibility.IAccessibilityManager");
        Object delegate = Class.forName(type.getName() + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, original);
        // Verify the ABI before changing the process-local cache.
        Method register = null;
        for (Method method : type.getMethods()) if (method.getName().equals("registerUiTestAutomationService")) register = method;
        Method init = null;
        for (Method method : Class.forName("android.accessibilityservice.IAccessibilityServiceClient").getMethods()) {
            if (method.getName().equals("init")) init = method;
        }
        if (init == null || init.getParameterCount() != 3) throw new UnsupportedOperationException("Unsupported automation callback ABI");
        if (register == null || register.getParameterCount() != 5) throw new UnsupportedOperationException("Unsupported automation registration ABI");
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (object, method, args) -> {
            String name = method.getName();
            if (name.equals("registerUiTestAutomationService")) {
                Parcel request = Parcel.obtain(), response = Parcel.obtain();
                try {
                    request.writeInterfaceToken(AutomationProtocol.DESCRIPTOR);
                    request.writeStrongBinder(((IInterface) args[1]).asBinder());
                    ((AccessibilityServiceInfo) args[2]).writeToParcel(request, 0);
                    request.writeInt((Integer) args[3]); request.writeInt((Integer) args[4]);
                    transact(broker, AutomationProtocol.REGISTER, request, response); response.readException();
                    return null;
                } finally { request.recycle(); response.recycle(); }
            }
            if (name.equals("unregisterUiTestAutomationService")) {
                Parcel request = Parcel.obtain(), response = Parcel.obtain();
                try {
                    request.writeInterfaceToken(AutomationProtocol.DESCRIPTOR);
                    request.writeStrongBinder(((IInterface) args[0]).asBinder());
                    transact(broker, AutomationProtocol.UNREGISTER, request, response); response.readException();
                    return null;
                } finally { request.recycle(); response.recycle(); }
            }
            try { return method.invoke(delegate, args); }
            catch (InvocationTargetException error) { throw error.getCause(); }
        });
        Binder manager = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                return original.transact(code, data, reply, flags);
            }
        };
        manager.attachInterface((IInterface) proxy, type.getName());
        synchronized (cache) { cache.put("accessibility", manager); }
        hub = broker;
        installRouted(cache, getService, "input", "android.hardware.input.IInputManager");
        installRouted(cache, getService, "window", "android.view.IWindowManager");
        installRouted(cache, getService, "activity", "android.app.IActivityManager");
        installRouted(cache, getService, "activity_task", "android.app.IActivityTaskManager");
        installRouted(cache, getService, "package", "android.content.pm.IPackageManager");
        System.err.println("stage=shared-automation installed; input/window/activity/package mutation queues enabled");
    }

    private static void installRouted(Map<String, IBinder> cache, Method getService, String name, String type) {
        try {
            IBinder remote = (IBinder) getService.invoke(null, name);
            if (remote == null) return;
            Set<Integer> reads = new HashSet<>();
            for (Field field : Class.forName(type + "$Stub").getDeclaredFields()) {
                if (!field.getName().startsWith("TRANSACTION_")) continue;
                String method = field.getName().substring(12);
                if (method.startsWith("get") || method.startsWith("is") || method.startsWith("has")
                        || method.startsWith("check") || method.startsWith("query") || method.startsWith("find")
                        || method.startsWith("capture") || method.startsWith("takeScreenshot")
                        || method.startsWith("register") || method.startsWith("unregister")
                        || method.startsWith("open") || method.startsWith("create")
                        || method.startsWith("monitor") || method.startsWith("attach")) {
                    field.setAccessible(true); reads.add(field.getInt(null));
                }
            }
            synchronized (cache) { cache.put(name, new RoutedBinder(name, remote, reads)); }
        } catch (Exception error) {
            System.err.println("stage=resource-routing unavailable service=" + name + " error=" + error.getClass().getSimpleName());
        }
    }

    private static void transact(IBinder broker, int code, Parcel request, Parcel response) throws RemoteException {
        if (!broker.transact(code, request, response, 0)) throw new RemoteException("Concurrency broker rejected transaction");
    }
    private static final class RoutedBinder extends Binder {
        final String name; final IBinder original; final Set<Integer> reads;
        RoutedBinder(String name, IBinder original, Set<Integer> reads) throws RemoteException {
            this.name = name; this.original = original; this.reads = reads;
            attachInterface(null, original.getInterfaceDescriptor());
        }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code < FIRST_CALL_TRANSACTION || code > LAST_CALL_TRANSACTION || reads.contains(code)) {
                return original.transact(code, data, reply, flags);
            }
            Parcel request = Parcel.obtain(), response = Parcel.obtain();
            try {
                request.writeInterfaceToken(AutomationProtocol.DESCRIPTOR);
                request.writeStrongBinder(lifetime); request.writeString(name);
                request.writeInt(code); request.writeInt(flags); request.writeInt(data.dataSize());
                request.appendFrom(data, 0, data.dataSize());
                AutomationClient.transact(hub, AutomationProtocol.CALL, request, response); response.readException();
                boolean accepted = response.readInt() != 0;
                int size = response.readInt();
                if (reply != null && size > 0) reply.appendFrom(response, response.dataPosition(), size);
                return accepted;
            } finally { request.recycle(); response.recycle(); }
        }
    }
}
