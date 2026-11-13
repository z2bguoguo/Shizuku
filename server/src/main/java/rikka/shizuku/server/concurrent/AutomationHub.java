package rikka.shizuku.server.concurrent;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.system.Os;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import moe.shizuku.starter.concurrent.AutomationProtocol;
import moe.shizuku.starter.concurrent.ResourcePolicy;

/** One real system automation registration, independent clients and fair platform mutation lanes. */
public final class AutomationHub extends Binder {
    public AutomationHub() { attachInterface(null, AutomationProtocol.DESCRIPTOR); }
    private static final String CLIENT = "android.accessibilityservice.IAccessibilityServiceClient";
    private static final String CONNECTION = "android.accessibilityservice.IAccessibilityServiceConnection";
    private final Object state = new Object();
    private final ReentrantLock control = new ReentrantLock(true);
    private final Map<IBinder, Subscriber> clients = new LinkedHashMap<>();
    private final Set<IBinder> sessions = new HashSet<>();
    public final FairCallQueue queues = new FairCallQueue();
    private final IBinder owner = new Binder();
    private Mux mux;
    private volatile int generation;
    private Object manager, muxInterface;
    private IBinder realConnection, windowToken;
    private int connectionId, userId;
    private volatile boolean registered;
    private final AtomicInteger nextGesture = new AtomicInteger();
    private Gesture gesture;
    private long gesturesCompleted, gesturesFailed;

    private final class Subscriber implements IBinder.DeathRecipient {
        final IBinder client; AccessibilityServiceInfo info;
        final Channel channel; boolean initialized;
        Subscriber(IBinder client, AccessibilityServiceInfo info) { this.client = client; this.info = info; this.channel = new Channel(this); }
        @Override public void binderDied() { try { unregister(client); } catch (Exception error) { android.util.Log.w("AutomationHub", "client cleanup failed", error); } }
    }
    private static final class Gesture {
        final Subscriber client; final int original, global;
        Gesture(Subscriber client, int original, int global) { this.client = client; this.original = original; this.global = global; }
    }
    private static Object invoke(Object receiver, String name, Object... args) throws Exception {
        for (Method method : Class.forName("android.view.accessibility.IAccessibilityManager").getMethods()) {
            if (method.getName().equals(name)) {
                try { return method.invoke(receiver, args); }
                catch (InvocationTargetException error) {
                    Throwable cause = error.getCause();
                    if (cause instanceof Exception) throw (Exception) cause;
                    throw error;
                }
            }
        }
        throw new NoSuchMethodException(name);
    }
    private static Object asInterface(String descriptor, IBinder binder) throws Exception {
        return Class.forName(descriptor + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
    }
    private static AccessibilityServiceInfo copy(AccessibilityServiceInfo value) {
        Parcel parcel = Parcel.obtain();
        try { value.writeToParcel(parcel, 0); parcel.setDataPosition(0); return AccessibilityServiceInfo.CREATOR.createFromParcel(parcel); }
        finally { parcel.recycle(); }
    }
    private AccessibilityServiceInfo combinedInfo() {
        AccessibilityServiceInfo result = null;
        for (Subscriber client : clients.values()) {
            if (result == null) result = copy(client.info);
            else {
                result.flags |= client.info.flags;
                result.eventTypes |= client.info.eventTypes;
                result.feedbackType |= client.info.feedbackType;
                try { AccessibilityServiceInfo.class.getMethod("setCapabilities", int.class).invoke(result,
                        result.getCapabilities() | client.info.getCapabilities()); }
                catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot merge automation capabilities", error); }
                result.notificationTimeout = Math.min(result.notificationTimeout, client.info.notificationTimeout);
            }
        }
        if (result != null) {
            try { AccessibilityServiceInfo.class.getMethod("setCapabilities", int.class).invoke(result,
                    result.getCapabilities() | AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot initialize gesture capability", error); }
        }
        if (result != null) result.packageNames = null; // UiAutomation receives all windows; client filtering remains local.
        return result;
    }
    private void register(IBinder binder, AccessibilityServiceInfo info, int user, int flags) throws Exception {
        control.lockInterruptibly();
        Subscriber subscriber = null;
        try {
            synchronized (state) {
                if (clients.containsKey(binder)) return;
                if (clients.size() >= 32) throw new IllegalStateException("Too many automation clients");
                if (registered && userId != user) throw new IllegalStateException("Concurrent automation currently requires the same Android user");
                subscriber = new Subscriber(binder, copy(info));
                binder.linkToDeath(subscriber, 0); clients.put(binder, subscriber);
            }
            if (!registered) {
                manager = asInterface("android.view.accessibility.IAccessibilityManager", ServiceManager.getService("accessibility"));
                mux = new Mux(++generation);
                muxInterface = asInterface(CLIENT, mux);
                AccessibilityServiceInfo combined;
                synchronized (state) { combined = combinedInfo(); userId = user; }
                invoke(manager, "registerUiTestAutomationService", owner, muxInterface, combined, user, flags | 1);
                registered = true;
            }
            boolean ready;
            synchronized (state) { ready = realConnection != null; }
            if (ready) { sendInit(subscriber); updateInfo(); }
        } catch (Exception failure) {
            if (subscriber != null) {
                try { unregister(binder); }
                catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        } finally { control.unlock(); }
    }
    private void unregister(IBinder binder) throws Exception {
        control.lockInterruptibly();
        try {
            Subscriber subscriber;
            boolean empty;
            synchronized (state) {
                subscriber = clients.remove(binder);
                if (subscriber == null) return;
                subscriber.client.unlinkToDeath(subscriber, 0);
                empty = clients.isEmpty();
            }
            queues.cancel(binder);
            if (empty && registered) {
                invoke(manager, "unregisterUiTestAutomationService", muxInterface);
                registered = false; generation++;
                synchronized (state) {
                    realConnection = null; windowToken = null;
                    gesture = null;
                }
            } else if (!empty) updateInfo();
        } finally { control.unlock(); }
    }
    private void updateInfo() throws Exception {
        AccessibilityServiceInfo info;
        IBinder connection;
        synchronized (state) { info = combinedInfo(); connection = realConnection; }
        if (info == null || connection == null) return;
        Object service = asInterface(CONNECTION, connection);
        Class.forName(CONNECTION).getMethod("setServiceInfo", AccessibilityServiceInfo.class).invoke(service, info);
    }
    private void sendInit(Subscriber subscriber) throws RemoteException {
        IBinder real, token; int id;
        synchronized (state) {
            if (!clients.containsKey(subscriber.client) || subscriber.initialized) return;
            real = realConnection; token = windowToken; id = connectionId;
            if (real == null) return;
            subscriber.initialized = true;
        }
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(CLIENT); data.writeStrongBinder(subscriber.channel);
            data.writeInt(id); data.writeStrongBinder(token);
            subscriber.client.transact(transaction(CLIENT, "init"), data, null, IBinder.FLAG_ONEWAY);
        } finally { data.recycle(); }
    }
    private static int transaction(String descriptor, String name) {
        try { java.lang.reflect.Field field = Class.forName(descriptor + "$Stub").getDeclaredField("TRANSACTION_" + name); field.setAccessible(true); return field.getInt(null); }
        catch (Exception error) { throw new UnsupportedOperationException("Unsupported callback ABI: " + name, error); }
    }
    private void enforcePrivilegedProcess() {
        if (Binder.getCallingUid() != Os.getuid()) throw new SecurityException("Concurrency broker is restricted to this server's privileged UID");
    }
    private void watch(IBinder token) throws RemoteException {
        synchronized (sessions) {
            if (!token.isBinderAlive()) throw new RemoteException("Caller disconnected");
            if (sessions.add(token)) {
                try { token.linkToDeath(() -> { synchronized (sessions) { sessions.remove(token); } queues.cancel(token); }, 0); }
                catch (RemoteException failure) { sessions.remove(token); throw failure; }
            }
        }
    }
    public <T> T route(String descriptor, int code, Object caller, java.util.concurrent.Callable<T> operation) throws Exception {
        if (caller instanceof IBinder) watch((IBinder) caller);
        String lane = ResourcePolicy.lane(descriptor, code);
        if (lane == null) return operation.call();
        return queues.call(lane, caller, 10000, () -> {
            if (caller instanceof IBinder && !((IBinder) caller).isBinderAlive()) throw new RemoteException("Caller disconnected");
            synchronized (state) {
                if (lane.equals("ui-input") && gesture != null) throw new IllegalStateException("Input is waiting for a previous gesture completion");
            }
            return operation.call();
        });
    }
    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == INTERFACE_TRANSACTION) return super.onTransact(code, data, reply, flags);
        enforcePrivilegedProcess();
        data.enforceInterface(AutomationProtocol.DESCRIPTOR);
        long identity = Binder.clearCallingIdentity();
        try {
            if (code == AutomationProtocol.STATS) { reply.writeNoException(); reply.writeString(diagnostics()); return true; }
            if (code == AutomationProtocol.REGISTER) {
                IBinder client = data.readStrongBinder();
                AccessibilityServiceInfo info = AccessibilityServiceInfo.CREATOR.createFromParcel(data);
                register(client, info, data.readInt(), data.readInt());
                reply.writeNoException(); return true;
            }
            if (code == AutomationProtocol.UNREGISTER) {
                unregister(data.readStrongBinder()); reply.writeNoException(); return true;
            }
            if (code == AutomationProtocol.CALL) {
                IBinder lifetime = data.readStrongBinder();
                if (lifetime == null || !lifetime.isBinderAlive()) throw new IllegalStateException("Caller disconnected");
                watch(lifetime);
                String name = data.readString();
                if (!java.util.Arrays.asList("input", "window", "activity", "activity_task", "package").contains(name)) throw new SecurityException("Unsupported platform resource");
                IBinder target = ServiceManager.getService(name);
                int targetCode = data.readInt(), targetFlags = data.readInt(), size = data.readInt();
                if (size < 0 || size > data.dataAvail()) throw new IllegalArgumentException("Invalid platform request size");
                Parcel request = Parcel.obtain(), response = Parcel.obtain();
                try {
                    request.appendFrom(data, data.dataPosition(), size);
                    boolean accepted = route(target.getInterfaceDescriptor(), targetCode, lifetime, () -> {
                        if (!lifetime.isBinderAlive()) throw new IllegalStateException("Caller disconnected");
                        return target.transact(targetCode, request, response, targetFlags);
                    });
                    reply.writeNoException(); reply.writeInt(accepted ? 1 : 0); reply.writeInt(response.dataSize());
                    reply.appendFrom(response, 0, response.dataSize()); return true;
                } finally { request.recycle(); response.recycle(); }
            }
            return false;
        } catch (RemoteException | RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("Concurrency broker: " + failure.getMessage(), failure); }
        finally { Binder.restoreCallingIdentity(identity); }
    }
    public String diagnostics() {
        String header;
        synchronized (state) { header = "Shared automation: clients=" + clients.size() + " registered=" + registered
                + " connectionReady=" + (realConnection != null) + " pendingGesture=" + (gesture != null)
                + " activeGestureId=" + (gesture == null ? -1 : gesture.global) + " gestureIds=" + nextGesture.get() + " gesturesCompleted=" + gesturesCompleted + " gesturesFailed=" + gesturesFailed + "\n"; }
        return header + queues.diagnostics();
    }

    private final class Mux extends Binder {
        final int epoch;
        Mux(int epoch) { this.epoch = epoch; attachInterface(null, CLIENT); }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) return super.onTransact(code, data, reply, flags);
            if (epoch != generation) return true;
            if (code == transaction(CLIENT, "init")) {
                data.enforceInterface(CLIENT);
                ArrayList<Subscriber> snapshot;
                synchronized (state) {
                    realConnection = data.readStrongBinder(); connectionId = data.readInt(); windowToken = data.readStrongBinder();
                    snapshot = new ArrayList<>(clients.values());
                    for (Subscriber subscriber : snapshot) subscriber.initialized = false;
                }
                if (realConnection == null) {
                    for (Subscriber subscriber : snapshot) {
                        Parcel disconnected = Parcel.obtain();
                        try { disconnected.writeInterfaceToken(CLIENT); disconnected.writeStrongBinder(null);
                            disconnected.writeInt(-1); disconnected.writeStrongBinder(null);
                            subscriber.client.transact(code, disconnected, null, IBinder.FLAG_ONEWAY);
                        } finally { disconnected.recycle(); }
                    }
                } else for (Subscriber subscriber : snapshot) sendInit(subscriber);
                return true;
            }
            // Full gestures are executed by the coordinator: the platform's
            // UiAutomationService does not implement dispatchGesture callbacks.
            if (code == transaction(CLIENT, "onPerformGestureResult")) return true;
            ArrayList<Subscriber> snapshot;
            synchronized (state) { snapshot = new ArrayList<>(clients.values()); }
            for (Subscriber subscriber : snapshot) {
                Parcel copy = Parcel.obtain();
                try { copy.appendFrom(data, 0, data.dataSize()); subscriber.client.transact(code, copy, null, IBinder.FLAG_ONEWAY); }
                catch (RemoteException failure) { subscriber.binderDied(); }
                finally { copy.recycle(); }
            }
            return true;
        }
    }
    private final class Channel extends Binder {
        final Subscriber subscriber;
        Channel(Subscriber subscriber) { this.subscriber = subscriber; attachInterface(null, CONNECTION); }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) return super.onTransact(code, data, reply, flags);
            enforcePrivilegedProcess();
            IBinder target;
            synchronized (state) {
                if (!clients.containsKey(subscriber.client)) throw new IllegalStateException("Automation client disconnected");
                target = realConnection;
                if (target == null) throw new IllegalStateException("Shared automation connection is not ready");
            }
            String method = ResourcePolicy.method(CONNECTION, code);
            long identity = Binder.clearCallingIdentity();
            try {
                if ("setServiceInfo".equals(method)) {
                    data.enforceInterface(CONNECTION);
                    AccessibilityServiceInfo info = data.readInt() == 0 ? null : AccessibilityServiceInfo.CREATOR.createFromParcel(data);
                    if (info == null) throw new IllegalArgumentException("Missing service info");
                    control.lockInterruptibly();
                    try { synchronized (state) { subscriber.info = info; } updateInfo(); }
                    finally { control.unlock(); }
                    reply.writeNoException(); return true;
                }
                if ("disableSelf".equals(method)) { unregister(subscriber.client); reply.writeNoException(); return true; }
                if ("dispatchGesture".equals(method) || "sendGesture".equals(method)) {
                    Parcel request = Parcel.obtain();
                    try {
                        request.appendFrom(data,0,data.dataSize());request.setDataPosition(0);request.enforceInterface(CONNECTION);
                        int sequence=request.readInt();
                        if(request.readInt()==0)throw new IllegalArgumentException("Missing gesture steps");
                        android.os.Parcelable.Creator<?> creator=(android.os.Parcelable.Creator<?>)Class.forName("android.content.pm.ParceledListSlice").getField("CREATOR").get(null);
                        Object packed=creator.createFromParcel(request);
                        java.util.List<?> steps=(java.util.List<?>)packed.getClass().getMethod("getList").invoke(packed);
                        int display="dispatchGesture".equals(method)?request.readInt():0;
                        return queues.call("ui-input",subscriber.client,10000,()->{
                            Gesture pending=new Gesture(subscriber,sequence,nextGesture.incrementAndGet());
                            synchronized(state){if(gesture!=null)throw new IllegalStateException("Previous gesture is still running");gesture=pending;}
                            boolean success=false;
                            try { success=GestureEngine.execute(steps,display,()->{
                                synchronized(state){return clients.containsKey(subscriber.client)&&subscriber.client.isBinderAlive();}
                            }); }
                            finally {
                                synchronized(state){if(gesture==pending)gesture=null;if(success)gesturesCompleted++;else gesturesFailed++;}
                                Parcel callback=Parcel.obtain();
                                try {callback.writeInterfaceToken(CLIENT);callback.writeInt(pending.original);callback.writeInt(success?1:0);
                                    pending.client.client.transact(transaction(CLIENT,"onPerformGestureResult"),callback,null,IBinder.FLAG_ONEWAY);
                                } catch(RemoteException disconnected) { /* Client death is handled by its subscription. */ }
                                finally {callback.recycle();}
                            }
                            if(reply!=null)reply.writeNoException();return true;
                        });
                    } finally {request.recycle();}
                }
                return route(CONNECTION, code, subscriber.client, () -> target.transact(code, data, reply, flags));
            } catch (RemoteException | RuntimeException failure) { throw failure; }
            catch (Exception failure) { throw new IllegalStateException("Shared automation: " + failure.getMessage(), failure); }
            finally { Binder.restoreCallingIdentity(identity); }
        }
    }
}
