package moe.shizuku.starter.concurrent;

import android.content.IContentProvider;
import android.os.Bundle;
import android.os.IBinder;
import moe.shizuku.starter.util.IContentProviderCompat;
import rikka.hidden.compat.ActivityManagerApis;

public final class BrokerDiscovery {
    private BrokerDiscovery() { }
    public static IBinder get() throws Exception {
        String authority = rikka.shizuku.common.BuildConfig.MANAGER_APPLICATION_ID + ".shizuku";
        IContentProvider provider = ActivityManagerApis.getContentProviderExternal(authority, 0, null, authority);
        if (provider == null) throw new IllegalStateException("Manager provider unavailable for concurrency broker");
        try {
            Bundle result = IContentProviderCompat.call(provider, null, null, authority, AutomationProtocol.METHOD, null, new Bundle());
            IBinder binder = result == null ? null : result.getBinder(AutomationProtocol.EXTRA);
            if (binder == null || !binder.isBinderAlive()) throw new IllegalStateException("Concurrency broker unavailable");
            return binder;
        } finally { ActivityManagerApis.removeContentProviderExternal(authority, null); }
    }
}
