package moe.shizuku.starter;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import java.lang.reflect.InvocationTargetException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class UserServiceContextTest {
    private static class BindData {
        String processName;
        Object appInfo;
        Object info;
    }
    private static class ThreadState { private BindData mBoundApplication; }

    @Test public void fillsProcessAndPackageStateBeforeApplicationCreation() throws Exception {
        ThreadState thread = new ThreadState();
        Object info = new Object();
        Object data = UserServiceContext.initializeBoundApplication(thread, "li.songe.gkd:user_service", info);
        assertEquals("li.songe.gkd:user_service", thread.mBoundApplication.processName);
        assertSame(info, thread.mBoundApplication.appInfo);
        Object loadedApk = new Object();
        UserServiceContext.setLoadedApk(data, loadedApk);
        assertSame(loadedApk, thread.mBoundApplication.info);
        assertSame(data, UserServiceContext.initializeBoundApplication(thread, "dev.studyfloat:capture", info));
        assertEquals("dev.studyfloat:capture", thread.mBoundApplication.processName);
    }

    private static class OemLoadedApk {
        Application mApplication;
        private Application makeApplication(boolean forceDefault, Instrumentation instrumentation) {
            throw new NullPointerException("OEM Application.getProcessName().equals(...) on null");
        }
    }
    @Test public void knownOemNullPointerFallsBackToAttachedDefaultApplication() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        OemLoadedApk loaded = new OemLoadedApk();
        Application result = UserServiceContext.makeApplication(loaded, context);
        assertNotNull(result);
        assertSame(context, result.getBaseContext());
        assertSame(result, loaded.mApplication);
        assertNotNull(result.getClassLoader());
    }

    private static class BrokenLoadedApk {
        private Application makeApplication(boolean forceDefault, Instrumentation instrumentation) {
            throw new IllegalStateException("unrelated framework failure");
        }
    }
    @Test public void unrelatedFailuresAreNotSilentlyHidden() throws Exception {
        try {
            UserServiceContext.makeApplication(new BrokenLoadedApk(), RuntimeEnvironment.getApplication());
            fail("Expected original failure");
        } catch (InvocationTargetException error) {
            assertTrue(error.getCause() instanceof IllegalStateException);
        }
    }
}
