package moe.shizuku.starter;

import android.app.Application;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class DiagnosticBinderTest {
    @Test public void preservesDescriptorParcelsFlagsAndCallerIdentity() throws Exception {
        int caller = Binder.getCallingUid();
        Binder service = new Binder() {
            { attachInterface(null, "example.IService"); }
            @Override protected boolean onTransact(int code, Parcel request, Parcel reply, int flags) {
                assertEquals(IBinder.FIRST_CALL_TRANSACTION, code);
                assertEquals(IBinder.FLAG_ONEWAY, flags);
                assertEquals(caller, Binder.getCallingUid());
                request.enforceInterface("example.IService");
                reply.writeNoException();
                reply.writeInt(request.readInt() + 1);
                return true;
            }
        };
        DiagnosticBinder forwarding = new DiagnosticBinder(service);
        assertEquals(service.getInterfaceDescriptor(), forwarding.getInterfaceDescriptor());
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        try {
            request.writeInterfaceToken("example.IService");
            request.writeInt(41);
            assertTrue(forwarding.transact(IBinder.FIRST_CALL_TRANSACTION, request, reply, IBinder.FLAG_ONEWAY));
            reply.readException();
            assertEquals(42, reply.readInt());
        } finally { request.recycle(); reply.recycle(); }
    }

    @Test public void logsAndRethrowsOriginalServiceExceptionWithoutReadingPayload() throws Exception {
        RemoteException original = new RemoteException("original service failure");
        DiagnosticBinder forwarding = new DiagnosticBinder(new Binder() {
            @Override protected boolean onTransact(int code, Parcel request, Parcel reply, int flags) throws RemoteException {
                throw original;
            }
        });
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream previous = System.err;
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        try {
            System.setErr(new PrintStream(captured));
            request.writeString("private request payload");
            try {
                forwarding.transact(7, request, reply, 0);
                fail("Expected original exception");
            } catch (RemoteException failure) { assertSame(original, failure); }
        } finally { System.setErr(previous); request.recycle(); reply.recycle(); }
        String logs = captured.toString("UTF-8");
        assertTrue(logs, logs.contains("user-service-call failed code=7"));
        assertTrue(logs, logs.contains("original service failure"));
        assertFalse(logs, logs.contains("private request payload"));
    }

    @Test public void preservesRejectedTransactionResult() throws Exception {
        DiagnosticBinder forwarding = new DiagnosticBinder(new Binder() {
            @Override protected boolean onTransact(int code, Parcel request, Parcel reply, int flags) { return false; }
        });
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        try { assertFalse(forwarding.transact(31, request, reply, 0)); }
        finally { request.recycle(); reply.recycle(); }
    }
}
