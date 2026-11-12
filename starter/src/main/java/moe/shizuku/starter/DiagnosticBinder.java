package moe.shizuku.starter;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Local forwarding only: retain the incoming caller identity and original AIDL parcels. */
final class DiagnosticBinder extends Binder {
    private final IBinder service;
    private final Set<Integer> tracedCodes = new HashSet<>();
    private final AtomicInteger failures = new AtomicInteger();

    DiagnosticBinder(IBinder service) throws RemoteException {
        this.service = service;
        String descriptor = service.getInterfaceDescriptor();
        attachInterface(null, descriptor);
        System.err.println("stage=user-service-interface descriptor=" + descriptor);
    }

    private synchronized boolean trace(int code) {
        if (tracedCodes.size() >= 32 || tracedCodes.contains(code)) return false;
        tracedCodes.add(code);
        return true;
    }

    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        boolean trace = trace(code);
        long start = SystemClock.uptimeMillis();
        if (trace) System.err.println("stage=user-service-call begin code=" + code
                + " callerUid=" + Binder.getCallingUid() + " callerPid=" + Binder.getCallingPid());
        try {
            // service is the local Binder constructed in this process. Do not clear
            // calling identity or dispatch on another thread: clients may rely on it.
            boolean accepted = service.transact(code, data, reply, flags);
            if (trace) System.err.println("stage=user-service-call returned code=" + code
                    + " accepted=" + accepted + " replyBytes=" + (reply == null ? 0 : reply.dataSize()) + " elapsedMs=" + (SystemClock.uptimeMillis() - start));
            return accepted;
        } catch (RemoteException | RuntimeException failure) {
            int count = failures.incrementAndGet();
            if (count <= 64) {
                System.err.println("stage=user-service-call failed code=" + code
                        + " callerUid=" + Binder.getCallingUid() + " elapsedMs="
                        + (SystemClock.uptimeMillis() - start));
                failure.printStackTrace(System.err);
            } else if (count == 65) {
                System.err.println("stage=user-service-call further failures suppressed");
            }
            throw failure;
        }
    }
}
