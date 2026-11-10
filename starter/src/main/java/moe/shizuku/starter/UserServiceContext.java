package moe.shizuku.starter;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Minimal framework state expected by OEM LoadedApk hooks in an app_process service. */
public final class UserServiceContext {
    private UserServiceContext() { }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field result = cursor.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    static Object initializeBoundApplication(Object thread, String processName, Object appInfo)
            throws ReflectiveOperationException {
        if (processName == null || processName.isEmpty()) throw new IllegalArgumentException("processName is empty");
        Field bound = field(thread.getClass(), "mBoundApplication");
        Object data = bound.get(thread);
        if (data == null) {
            Constructor<?> constructor = bound.getType().getDeclaredConstructor();
            constructor.setAccessible(true);
            data = constructor.newInstance();
            bound.set(thread, data);
        }
        field(data.getClass(), "processName").set(data, processName);
        field(data.getClass(), "appInfo").set(data, appInfo);
        return data;
    }

    static void setLoadedApk(Object boundData, Object loadedApk) throws ReflectiveOperationException {
        field(boundData.getClass(), "info").set(boundData, loadedApk);
    }

    static Application makeApplication(Object loadedApk, Context context) throws ReflectiveOperationException {
        Method make = loadedApk.getClass().getDeclaredMethod("makeApplication", boolean.class, Instrumentation.class);
        make.setAccessible(true);
        try {
            return (Application) make.invoke(loadedApk, true, null);
        } catch (InvocationTargetException failure) {
            // Only recover the known OEM framework null-state failure. Other failures
            // retain their original exception and must remain visible in diagnostics.
            Throwable cause = failure.getCause();
            boolean nullState = false;
            for (int depth = 0; cause != null && depth < 12; depth++, cause = cause.getCause()) {
                if (cause instanceof NullPointerException) { nullState = true; break; }
            }
            if (!nullState) throw failure;
            System.err.println("stage=application-context OEM null-state; using package-context fallback");
            failure.getCause().printStackTrace(System.err);
            Application application = new Application();
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            attach.invoke(application, context);
            field(loadedApk.getClass(), "mApplication").set(loadedApk, application);
            return application;
        }
    }
}
