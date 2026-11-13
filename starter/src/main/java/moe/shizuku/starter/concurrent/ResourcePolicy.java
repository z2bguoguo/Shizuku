package moe.shizuku.starter.concurrent;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Only known platform interfaces are classified. Unknown APIs keep their original behavior. */
public final class ResourcePolicy {
    private static final Map<String, Map<Integer, String>> methods = new ConcurrentHashMap<>();
    private ResourcePolicy() { }
    public static String method(String descriptor, int code) {
        return methods.computeIfAbsent(descriptor, name -> {
            Map<Integer, String> result = new HashMap<>();
            try {
                for (Field field : Class.forName(name + "$Stub").getDeclaredFields()) {
                    if (field.getName().startsWith("TRANSACTION_")) {
                        field.setAccessible(true); result.put(field.getInt(null), field.getName().substring(12));
                    }
                }
            } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) { }
            return result;
        }).get(code);
    }
    public static String lane(String descriptor, int code) {
        String resource;
        switch (descriptor) {
            case "android.hardware.input.IInputManager":
            case "android.view.IWindowManager":
            case "android.app.IActivityManager":
            case "android.app.IActivityTaskManager":
            case "android.accessibilityservice.IAccessibilityServiceConnection": resource = "ui-input"; break;
            case "android.content.pm.IPackageManager": resource = "package-mutations"; break;
            default: return null;
        }
        String method = method(descriptor, code);
        if (method == null || method.startsWith("get") || method.startsWith("is") || method.startsWith("has")
                || method.startsWith("check") || method.startsWith("query") || method.startsWith("find")
                || method.startsWith("capture") || method.startsWith("takeScreenshot")
                || method.startsWith("register") || method.startsWith("unregister")
                || method.startsWith("open") || method.startsWith("create")
                || method.startsWith("monitor") || method.startsWith("attach")) return null;
        return resource;
    }
}
