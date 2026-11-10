package moe.shizuku.manager.privacy

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.EditTextPreference
import moe.shizuku.manager.R
import moe.shizuku.manager.utils.AppLabelResolver
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BlacklistUiTest {
    @Test
    fun searchDialogOpensAndAppliesAppNameAndPackageQueries() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.AppTheme)
        activity.theme.applyStyle(R.style.ThemeOverlay_Rikka_Material3_Preference, true)
        controller.setup()
        val fragment = BlacklistFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment).commitNow()
        val search = requireNotNull(fragment.findPreference<EditTextPreference>("sdk_blacklist_search"))
        for (query in listOf("GKD", "dev.studyfloat", "")) {
            search.performClick()
            activity.supportFragmentManager.executePendingTransactions()
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = activity.supportFragmentManager.findFragmentByTag(
                "androidx.preference.PreferenceFragment.DIALOG") as DialogFragment
            assertTrue(dialog.requireDialog().isShowing)
            dialog.requireDialog().findViewById<EditText>(android.R.id.edit).setText(query)
            dialog.requireDialog().findViewById<android.view.View>(android.R.id.button1).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            activity.supportFragmentManager.executePendingTransactions()
            assertEquals(query, search.text)
            assertEquals(query, search.summary.toString())
        }
        val state = Bundle()
        fragment.onSaveInstanceState(state)
        assertEquals("", state.getString("query"))
        controller.pause().stop().destroy()
    }

    @Test
    fun resolvesLabelsFromLocalPackageWhenShellInfoOnlyHasPackageName() {
        val app = RuntimeEnvironment.getApplication()
        val packageName = app.packageName
        val local = app.packageManager.getApplicationInfo(packageName, 0)
        local.nonLocalizedLabel = "浮窗解题"
        val packageInfo = android.content.pm.PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = local
        }
        shadowOf(app.packageManager).installPackage(packageInfo)
        val remote = ApplicationInfo().apply { this.packageName = packageName }
        assertEquals("浮窗解题", AppLabelResolver.load(app, remote))
    }

    @Test
    fun retainsNonLocalizedNamesAndHandlesPackagesWithoutResources() {
        val app = RuntimeEnvironment.getApplication()
        val info = ApplicationInfo().apply {
            packageName = "example.unavailable"
            nonLocalizedLabel = "GKD"
        }
        assertEquals("GKD", AppLabelResolver.load(app, info))
        info.nonLocalizedLabel = null
        assertEquals("example.unavailable", AppLabelResolver.load(app, info))
    }
}
