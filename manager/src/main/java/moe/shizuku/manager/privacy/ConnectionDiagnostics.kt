package moe.shizuku.manager.privacy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Parcel
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import rikka.shizuku.Shizuku
import rikka.shizuku.server.ServerConstants

object ConnectionDiagnostics {
    fun show(fragment: Fragment) {
        val context = fragment.requireContext()
        fragment.lifecycleScope.launch {
            val body = try {
                withContext(Dispatchers.IO) {
                    if (!Shizuku.pingBinder()) context.getString(R.string.connection_diagnostics_not_running)
                    else collect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.toString()
            }
            if (!fragment.isAdded) return@launch
            val text = "Version: ${BuildConfig.VERSION_NAME}\n$body"
            val label = context.getString(R.string.connection_diagnostics_title)
            val view = TextView(context).apply {
                setText(text)
                setTextIsSelectable(true)
                val padding = (16 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            }
            MaterialAlertDialogBuilder(context).setTitle(label)
                .setView(ScrollView(context).apply { addView(view) })
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.connection_diagnostics_copy) { _, _ ->
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText(label, text))
                }.show()
        }
    }

    private fun collect(): String {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            check(Shizuku.getBinder()?.transact(ServerConstants.BINDER_TRANSACTION_getConnectionDiagnostics,
                data, reply, 0) == true) { "Server does not support diagnostics; restart the updated Shizuku service." }
            reply.readException()
            return reply.readString().orEmpty()
        } finally { data.recycle(); reply.recycle() }
    }
}
