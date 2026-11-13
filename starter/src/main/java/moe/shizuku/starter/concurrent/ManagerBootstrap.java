package moe.shizuku.starter.concurrent;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ServiceManager;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Shell/root-only, explicit broadcast rendezvous. Never launches an activity. */
public final class ManagerBootstrap {
    public static final String SERVER = "bootstrapServer", CALLBACK = "bootstrapCallback";
    public static final String DESCRIPTOR = "moe.shizuku.manager.Bootstrap";
    private static volatile IBinder relay;
    private static volatile IBinder broker;
    private ManagerBootstrap() { }

    private static Intent intent() {
        return new Intent().setComponent(new ComponentName(
                rikka.shizuku.common.BuildConfig.MANAGER_APPLICATION_ID,
                "moe.shizuku.manager.BootstrapReceiver"))
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND);
    }

    public static void seed(IBinder server) throws Exception {
        Bundle extras = new Bundle(); extras.putBinder(SERVER, server);
        broadcast(intent().putExtras(extras));
    }

    // A null caller thread is required for app_process, which is not an AMS app process.
    static void broadcast(Intent intent) throws Exception {
        Class<?> stub = Class.forName("android.app.IActivityManager$Stub");
        Object am = stub.getMethod("asInterface", IBinder.class)
                .invoke(null, ServiceManager.getService("activity"));
        Method selected = null;
        for (Method method : Class.forName("android.app.IActivityManager").getMethods()) {
            if (method.getName().equals("broadcastIntentWithFeature")) { selected = method; break; }
            if (method.getName().equals("broadcastIntent")) selected = method;
        }
        if (selected == null) throw new NoSuchMethodException("broadcastIntent");
        Class<?>[] types = selected.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == Intent.class) args[i] = intent;
            else if (types[i] == boolean.class) args[i] = false;
            else if (types[i] == int.class) {
                // resultCode=0, appOp=OP_NONE, userId=0; remaining reference args are null.
                args[i] = i + 1 < types.length && types[i + 1] == Bundle.class ? -1 : 0;
            }
        }
        selected.invoke(am, args);
    }

    public static synchronized IBinder getBroker() throws Exception {
        if (broker != null && broker.isBinderAlive() && relay != null && relay.isBinderAlive()) return broker;
        CountDownLatch ready = new CountDownLatch(1);
        Binder callback = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code != 1) return false;
                data.enforceInterface(DESCRIPTOR);
                IBinder candidateRelay = data.readStrongBinder(), candidateBroker = data.readStrongBinder();
                if (candidateRelay != null && candidateBroker != null) {
                    relay = candidateRelay; broker = candidateBroker; ready.countDown();
                }
                return true;
            }
        };
        Bundle extras = new Bundle(); extras.putBinder(CALLBACK, callback);
        for (int attempt = 0; attempt < 3; attempt++) {
            broadcast(intent().putExtras(extras));
            if (ready.await(600, TimeUnit.MILLISECONDS)) {
                System.err.println("stage=manager-bootstrap broadcast ready");
                return broker;
            }
        }
        throw new IllegalStateException("Manager broadcast bootstrap timed out");
    }

    public static IBinder handoff(IBinder service, String token) throws Exception {
        getBroker();
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR); data.writeStrongBinder(service); data.writeString(token);
            if (!relay.transact(1, data, reply, 0)) throw new IllegalStateException("Bootstrap handoff rejected");
            reply.readException();
            IBinder server = reply.readStrongBinder();
            if (server == null || !server.isBinderAlive()) throw new IllegalStateException("Bootstrap server unavailable");
            return server;
        } finally { data.recycle(); reply.recycle(); }
    }
}
