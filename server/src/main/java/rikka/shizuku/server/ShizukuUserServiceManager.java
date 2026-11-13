package rikka.shizuku.server;

import android.content.pm.PackageInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import java.util.ArrayDeque;
import moe.shizuku.server.IShizukuServiceConnection;
import rikka.shizuku.ShizukuApiConstants;
import android.util.ArrayMap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import moe.shizuku.starter.ServiceStarter;
import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.UserManagerApis;
import rikka.shizuku.server.util.UserHandleCompat;

public class ShizukuUserServiceManager extends ConcurrentUserServiceManager {

    private final Map<UserServiceRecord, ApkChangedListener> apkChangedListeners = new ArrayMap<>();
    private final Map<UserServiceRecord, String> diagnosticRecords = new ArrayMap<>();
    private final ArrayDeque<String> events = new ArrayDeque<>();

    private synchronized void event(String message) {
        if (events.size() == 64) events.removeFirst();
        events.addLast("uptimeMs=" + SystemClock.uptimeMillis() + " " + message);
    }

    @Override public int addUserService(IShizukuServiceConnection connection, Bundle options, int apiVersion) {
        event("bind-request uid=" + Binder.getCallingUid() + " pid=" + Binder.getCallingPid()
                + " component=" + options.getParcelable(ShizukuApiConstants.USER_SERVICE_ARG_COMPONENT)
                + " noCreate=" + options.getBoolean(ShizukuApiConstants.USER_SERVICE_ARG_NO_CREATE, false));
        try {
            int result = super.addUserService(connection, options, apiVersion);
            event("bind-request accepted result=" + result);
            return result;
        } catch (RuntimeException failure) {
            event("bind-request failed " + failure.getClass().getSimpleName());
            throw failure;
        }
    }

    @Override public int removeUserService(IShizukuServiceConnection connection, Bundle options) {
        event("unbind-request uid=" + Binder.getCallingUid()
                + " component=" + options.getParcelable(ShizukuApiConstants.USER_SERVICE_ARG_COMPONENT)
                + " remove=" + options.getBoolean(ShizukuApiConstants.USER_SERVICE_ARG_REMOVE, true));
        return super.removeUserService(connection, options);
    }

    @Override public void attachUserService(IBinder binder, Bundle options) {
        event("service-handoff received");
        try {
            super.attachUserService(binder, options);
            event("service-handoff accepted; connection callbacks dispatched");
        } catch (RuntimeException failure) {
            event("service-handoff failed " + failure.getClass().getSimpleName());
            throw failure;
        }
    }

    synchronized String collectDiagnostics() {
        StringBuilder text = new StringBuilder("Active user services: " + diagnosticRecords.size() + "\n");
        for (Map.Entry<UserServiceRecord, String> entry : diagnosticRecords.entrySet()) {
            UserServiceRecord record = entry.getKey();
            boolean alive = record.service != null && record.service.pingBinder();
            text.append("package=").append(entry.getValue())
                    .append(" binderAlive=").append(alive)
                    .append(" awaitingBinder=").append(record.service == null && record.starting)
                    .append(" callbacks=").append(record.callbacks.getRegisteredCallbackCount())
                    .append(" daemon=").append(record.daemon).append('\n');
        }
        text.append("Recent service lifecycle (server uptime):\n");
        for (String event : events) text.append(event).append('\n');
        return startupQueueDiagnostics() + text.toString();
    }

    private final IBinder server;

    public ShizukuUserServiceManager(IBinder server) {
        super();
        this.server = server;
    }

    @Override
    public String getUserServiceStartCmd(
            UserServiceRecord record, String key, String token, String packageName,
            String classname, String processNameSuffix, int callingUid, boolean use32Bits, boolean debug) {

        try {
            moe.shizuku.starter.concurrent.ManagerBootstrap.seed(server);
        } catch (Exception failure) {
            LOGGER.w(failure, "Cannot seed background manager bootstrap; provider fallback retained");
        }
        String appProcess = "/system/bin/app_process";
        if (use32Bits && new File("/system/bin/app_process32").exists()) {
            appProcess = "/system/bin/app_process32";
        }
        String command = ServiceStarter.commandForUserService(
                appProcess,
                ShizukuService.getManagerApplicationInfo().sourceDir,
                token, packageName, classname, processNameSuffix, callingUid, debug);
        try {
            return UserServiceDiagnostics.capture(command, UserServiceDiagnostics.begin(packageName, classname, callingUid));
        } catch (java.io.IOException e) {
            LOGGER.w(e, "Cannot capture user service startup output");
            return command;
        }
    }

    @Override
    public void onUserServiceRecordCreated(UserServiceRecord record, PackageInfo packageInfo) {
        super.onUserServiceRecordCreated(record, packageInfo);
        synchronized (this) { diagnosticRecords.put(record, packageInfo.packageName); }
        event("service-record created package=" + packageInfo.packageName);

        String packageName = packageInfo.packageName;
        ApkChangedListener listener = new ApkChangedListener() {
            @Override
            public void onApkChanged() {
                String newSourceDir = null;

                for (int userId : UserManagerApis.getUserIdsNoThrow()) {
                    PackageInfo pi = PackageManagerApis.getPackageInfoNoThrow(packageName, 0, userId);
                    if (pi != null && pi.applicationInfo != null && pi.applicationInfo.sourceDir != null) {
                        newSourceDir = pi.applicationInfo.sourceDir;
                        break;
                    }
                }

                if (newSourceDir == null) {
                    LOGGER.v("remove record %s because package %s has been removed", record.token, packageName);
                    record.removeSelf();
                } else {
                    LOGGER.v("update apk listener for record %s since package %s is upgrading", record.token, packageName);
                    ApkChangedObservers.stop(this);
                    ApkChangedObservers.start(newSourceDir, this);
                }
            }
        };

        ApkChangedObservers.start(packageInfo.applicationInfo.sourceDir, listener);
        apkChangedListeners.put(record, listener);
    }

    @Override
    public void onUserServiceRecordRemoved(UserServiceRecord record) {
        super.onUserServiceRecordRemoved(record);
        synchronized (this) {
            event("service-record removed package=" + diagnosticRecords.remove(record));
        }
        ApkChangedListener listener = apkChangedListeners.get(record);
        if (listener != null) {
            ApkChangedObservers.stop(listener);
            apkChangedListeners.remove(record);
        }
    }
}
