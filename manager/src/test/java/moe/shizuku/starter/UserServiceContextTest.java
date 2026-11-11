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
    private static class WorkingLoadedApk {
        final Application application = new Application();
        private Application makeApplication(boolean forceDefault, Instrumentation instrumentation) {
            return application;
        }
    }
    @Test public void normalFrameworkUsesOfficialUnboundState() throws Exception {
        ThreadState thread = new ThreadState();
        WorkingLoadedApk loaded = new WorkingLoadedApk();
        Application result = UserServiceContext.makeApplication(thread, "client:worker", new Object(),
                loaded, RuntimeEnvironment.getApplication());
        assertSame(loaded.application, result);
        assertNull(thread.mBoundApplication);
    }

    private static class StateDependentLoadedApk {
        final ThreadState thread;
        final Application application = new Application();
        int attempts;
        StateDependentLoadedApk(ThreadState thread) { this.thread = thread; }
        private Application makeApplication(boolean forceDefault, Instrumentation instrumentation) {
            attempts++;
            if (attempts == 1) throw new NullPointerException("OEM process state unavailable");
            assertEquals("client:worker", thread.mBoundApplication.processName);
            assertSame(this, thread.mBoundApplication.info);
            return application;
        }
    }
    @Test public void oemRetryRestoresExactPreviousRecordWithoutMutatingIt() throws Exception {
        ThreadState thread = new ThreadState();
        BindData previous = new BindData();
        previous.processName = "previous";
        Object originalInfo = new Object();
        previous.appInfo = originalInfo;
        thread.mBoundApplication = previous;
        StateDependentLoadedApk loaded = new StateDependentLoadedApk(thread);
        assertSame(loaded.application, UserServiceContext.makeApplication(thread, "client:worker",
                new Object(), loaded, RuntimeEnvironment.getApplication()));
        assertEquals(2, loaded.attempts);
        assertSame(previous, thread.mBoundApplication);
        assertEquals("previous", previous.processName);
        assertSame(originalInfo, previous.appInfo);
    }
    @Test public void oemFallbackDoesNotLeaveClientIdentityBound() throws Exception {
        ThreadState thread = new ThreadState();
        Application result = UserServiceContext.makeApplication(thread, "client:worker", new Object(),
                new OemLoadedApk(), RuntimeEnvironment.getApplication());
        assertNotNull(result);
        assertNull(thread.mBoundApplication);
    }
    private static class FailingRetryLoadedApk {
        int attempts;
        private Application makeApplication(boolean forceDefault, Instrumentation instrumentation) {
            if (++attempts == 1) throw new NullPointerException("OEM process state unavailable");
            throw new IllegalStateException("retry failure");
        }
    }
    @Test public void failedRetryAlsoRestoresUnboundState() throws Exception {
        ThreadState thread = new ThreadState();
        try {
            UserServiceContext.makeApplication(thread, "client:worker", new Object(),
                    new FailingRetryLoadedApk(), RuntimeEnvironment.getApplication());
            fail("Expected retry failure");
        } catch (InvocationTargetException failure) {
            assertTrue(failure.getCause() instanceof IllegalStateException);
        }
        assertNull(thread.mBoundApplication);
    }

}
