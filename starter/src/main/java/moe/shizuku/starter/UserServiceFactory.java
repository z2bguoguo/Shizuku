package moe.shizuku.starter;

// Based on Shizuku-API UserService at a27f6e4151ba7b39965ca47edb2bf0aeed7102e5.
// Preserve the same creation contract and capture bootstrap exceptions on stderr.

import android.app.ActivityThread;
import android.app.Application;
import android.content.Context;
import android.content.ContextHidden;
import android.ddm.DdmHandleAppName;
import android.os.Build;
import android.os.IBinder;
import android.os.UserHandle;
import android.os.UserHandleHidden;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import dev.rikka.tools.refine.Refine;
import rikka.hidden.compat.PackageManagerApis;

public class UserServiceFactory {

    private static String TAG;

    public static void setTag(String tag) {
        UserServiceFactory.TAG = tag;
    }

    @Nullable
    public static Pair<IBinder, String> create(String[] args) {
        String name = null;
        String requestedProcessName = null;
        String token = null;
        String pkg = null;
        String cls = null;
        int uid = -1;

        for (String arg : args) {
            if (arg.startsWith("--process-name=")) {
                requestedProcessName = arg.substring(15);
            } else if (arg.startsWith("--debug-name=")) {
                name = arg.substring(13);
            } else if (arg.startsWith("--token=")) {
                token = arg.substring(8);
            } else if (arg.startsWith("--package=")) {
                pkg = arg.substring(10);
            } else if (arg.startsWith("--class=")) {
                cls = arg.substring(8);
            } else if (arg.startsWith("--uid=")) {
                uid = Integer.parseInt(arg.substring(6));
            }
        }

        int userId = uid / 100000;

        Log.i(TAG, String.format("starting service %s/%s...", pkg, cls));

        IBinder service;

        try {
            ActivityThread activityThread = ActivityThread.systemMain();
            String processName = requestedProcessName != null ? requestedProcessName
                    : (name != null ? name : pkg + ":user_service");
            android.content.pm.ApplicationInfo appInfo = PackageManagerApis.getApplicationInfoNoThrow(pkg, 0, userId);
            if (appInfo == null) throw new IllegalStateException("Client application not found: " + pkg);
            Context systemContext = activityThread.getSystemContext();
            DdmHandleAppName.setAppName(processName, userId);
            System.err.println("stage=framework-process name=" + processName);

            //noinspection InstantiationOfUtilityClass
            UserHandle userHandle = Refine.unsafeCast(
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                            ? UserHandleHidden.of(userId)
                            : new UserHandleHidden(userId));
            Context context = Refine.<ContextHidden>unsafeCast(systemContext).createPackageContextAsUser(pkg, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY, userHandle);
            Field mPackageInfo = context.getClass().getDeclaredField("mPackageInfo");
            mPackageInfo.setAccessible(true);
            Object loadedApk = mPackageInfo.get(context);
            Application application = UserServiceContext.makeApplication(
                    activityThread, processName, appInfo, loadedApk, context);
            System.err.println("stage=application-context ready");
            Field mInitialApplication = activityThread.getClass().getDeclaredField("mInitialApplication");
            mInitialApplication.setAccessible(true);
            mInitialApplication.set(activityThread, application);

            ClassLoader classLoader = application.getClassLoader();
            Class<?> serviceClass = classLoader.loadClass(cls);
            Constructor<?> constructorWithContext = null;
            try {
                constructorWithContext = serviceClass.getConstructor(Context.class);
            } catch (NoSuchMethodException | SecurityException ignored) {
            }
            if (constructorWithContext != null) {
                service = (IBinder) constructorWithContext.newInstance(application);
            } else {
                service = (IBinder) serviceClass.newInstance();
            }
        } catch (Throwable tr) {
            Log.w(TAG, String.format("unable to start service %s/%s...", pkg, cls), tr);
            System.err.println("Unable to create user service " + pkg + "/" + cls);
            tr.printStackTrace(System.err);
            return null;
        }

        return new Pair<>(service, token);
    }
}
