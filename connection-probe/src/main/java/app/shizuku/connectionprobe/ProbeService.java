package app.shizuku.connectionprobe;

import android.os.Binder;
import android.os.Parcel;

public class ProbeService extends Binder {
    public ProbeService() { }
    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        if (code == FIRST_CALL_TRANSACTION) {
            reply.writeNoException();
            reply.writeInt(android.os.Process.myUid());
            return true;
        }
        return false;
    }
}
