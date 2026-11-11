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

    private static Application invokeMakeApplication(Object loadedApk) throws ReflectiveOperationException {
        Method make = loadedApk.getClass().getDeclaredMethod("makeApplication", boolean.class, Instrumentation.class);
        make.setAccessible(true);
        return (Application) make.invoke(loadedApk, true, null);
    }

    private static boolean isNullState(InvocationTargetException failure) {
        Throwable cause = failure.getCause();
        for (int depth = 0; cause != null && depth < 12; depth++, cause = cause.getCause()) {
            if (cause instanceof NullPointerException) return true;
        }
        return false;
    }

    static Application makeApplication(Object thread, String processName, Object appInfo,
            Object loadedApk, Context context) throws ReflectiveOperationException {
        // Keep the official app_process runtime contract on frameworks that work
        // without synthetic bind state. Package/process attribution is not a real
        // application bind and must not persist into client service calls.
        try {
            return invokeMakeApplication(loadedApk);
        } catch (InvocationTargetException failure) {
            if (!isNullState(failure)) throw failure;
            System.err.println("stage=application-context retrying OEM null-state with temporary framework state");
            Field bound = field(thread.getClass(), "mBoundApplication");
            Object previous = bound.get(thread);
            try {
                // Do not mutate an existing bind record: restore its exact object
                // and fields even if framework creation or fallback throws.
                bound.set(thread, null);
                Object temporary = initializeBoundApplication(thread, processName, appInfo);
                setLoadedApk(temporary, loadedApk);
                return makeApplication(loadedApk, context);
            } finally {
                bound.set(thread, previous);
                System.err.println("stage=framework-state restored after OEM bootstrap");
            }
        }
    }

    static Application makeApplication(Object loadedApk, Context context) throws ReflectiveOperationException {
        try {
            return invokeMakeApplication(loadedApk);
        } catch (InvocationTargetException failure) {
            if (!isNullState(failure)) throw failure;
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
