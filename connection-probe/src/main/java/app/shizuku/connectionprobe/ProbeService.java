package app.shizuku.connectionprobe;

import android.os.Binder;
import android.os.Parcel;

public class ProbeService extends Binder {
    public ProbeService(android.content.Context context) {
        String processName = android.app.Application.getProcessName();
        if (processName == null || !processName.equals("app.shizuku.connectionprobe:probe")) {
            throw new IllegalStateException("Missing/wrong framework process name: " + processName);
        }
        if (context.getApplicationContext() == null) {
            throw new IllegalStateException("Application context was not initialized");
        }
    }
    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        if (code == FIRST_CALL_TRANSACTION) {
            reply.writeNoException();
            reply.writeInt(android.os.Process.myUid());
            return true;
        }
        return false;
    }
}
