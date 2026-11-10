package moe.shizuku.manager.privacy

import android.os.Bundle
import android.os.Parcel
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarFragmentActivity
import moe.shizuku.manager.utils.ShizukuSystemApis
import rikka.shizuku.Shizuku
import rikka.shizuku.server.ServerConstants

class BlacklistActivity : AppBarFragmentActivity() {
    override fun onApplyUserThemeResource(theme: android.content.res.Resources.Theme, isDecorView: Boolean) {
        super.onApplyUserThemeResource(theme, isDecorView)
        theme.applyStyle(R.style.ThemeOverlay_Rikka_Material3_Preference, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, BlacklistFragment()).commit()
    }
}

internal object SdkBlacklist {
    private fun transact(packages: List<String>?): List<String> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            val code = if (packages == null) ServerConstants.BINDER_TRANSACTION_getBlacklist
                else ServerConstants.BINDER_TRANSACTION_setBlacklist
            if (packages != null) data.writeStringList(packages)
            check(Shizuku.getBinder()?.transact(code, data, reply, 0) == true)
            reply.readException()
            return if (packages == null) reply.createStringArrayList().orEmpty() else packages
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
    fun get() = transact(null)
    fun set(packages: List<String>) { transact(packages) }
}

class BlacklistFragment : PreferenceFragmentCompat() {
    private data class App(val name: String, val label: String)
    private val selected = linkedSetOf<String>()
    private var apps = listOf<App>()
    private lateinit var category: PreferenceCategory
    private lateinit var save: Preference
    private var query = ""

    override fun onCreatePreferences(state: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        val info = Preference(requireContext()).apply {
            title = getString(R.string.sdk_blacklist_title)
            summary = getString(R.string.sdk_blacklist_details)
            isSelectable = false
        }
        preferenceScreen.addPreference(info)
        save = Preference(requireContext()).apply {
            title = getString(R.string.sdk_blacklist_save)
            isEnabled = false
            setOnPreferenceClickListener { saveSelection(); true }
        }
        preferenceScreen.addPreference(save)
        preferenceScreen.addPreference(EditTextPreference(requireContext()).apply {
            title = getString(R.string.sdk_blacklist_search)
            isPersistent = false
            setOnPreferenceChangeListener { _, value ->
                query = value.toString().trim()
                summary = query
                renderApps()
                true
            }
        })
        category = PreferenceCategory(requireContext()).apply { title = getString(R.string.sdk_blacklist_loading) }
        preferenceScreen.addPreference(category)
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    check(Shizuku.pingBinder()) { getString(R.string.sdk_blacklist_not_running) }
                    val blocked = SdkBlacklist.get()
                    val pm = requireContext().packageManager
                    val all = ShizukuSystemApis.getUsers(false).flatMap { user ->
                        ShizukuSystemApis.getInstalledPackages(0, user.id)
                    }.filter { it.packageName != requireContext().packageName }
                    val entries = all.distinctBy { it.packageName }.map {
                        App(it.packageName, it.applicationInfo?.loadLabel(pm)?.toString() ?: it.packageName)
                    }.toMutableList()
                    // Keep removed/unavailable packages visible so old rules can be cleared.
                    blocked.filter { name -> entries.none { it.name == name } }.forEach { entries.add(App(it, it)) }
                    blocked to entries.sortedBy { it.label.lowercase() }
                }
                selected.addAll(result.first)
                state?.getStringArrayList("selected")?.let { selected.clear(); selected.addAll(it) }
                apps = result.second
                category.title = getString(R.string.sdk_blacklist_apps)
                save.isEnabled = true
                renderApps()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                category.title = getString(R.string.sdk_blacklist_error, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun renderApps() {
        category.removeAll()
        apps.filter { query.isEmpty() || it.name.contains(query, true) || it.label.contains(query, true) }
            .forEach { app ->
                category.addPreference(CheckBoxPreference(requireContext()).apply {
                    title = app.label
                    summary = app.name
                    isPersistent = false
                    isChecked = app.name in selected
                    setOnPreferenceChangeListener { _, value ->
                        if (value == true) selected.add(app.name) else selected.remove(app.name)
                        true
                    }
                })
            }
    }

    private fun saveSelection() {
        save.isEnabled = false
        val packages = selected.toList()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { SdkBlacklist.set(packages) }
                Toast.makeText(requireContext(), R.string.sdk_blacklist_saved, Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.sdk_blacklist_error, e.message ?: ""), Toast.LENGTH_LONG).show()
            } finally {
                save.isEnabled = true
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("selected", ArrayList(selected))
    }
}
