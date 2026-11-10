package moe.shizuku.manager.utils

import android.content.Context
import android.content.pm.ApplicationInfo

/** Resolve actual APK labels, including apps returned by the shell for another user. */
object AppLabelResolver {
    fun load(context: Context, info: ApplicationInfo): String {
        val pm = context.packageManager
        fun useful(value: CharSequence?): String? = value?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() && it != info.packageName }

        useful(info.nonLocalizedLabel)?.let { return it }
        runCatching { useful(info.loadLabel(pm)) }.getOrNull()?.let { return it }
        if (info.labelRes != 0) {
            runCatching { useful(pm.getResourcesForApplication(info).getText(info.labelRes)) }
                .getOrNull()?.let { return it }
        }
        // A PackageInfo obtained through privileged IPC can omit locally cached label data.
        runCatching {
            @Suppress("DEPRECATION")
            val local = pm.getApplicationInfo(info.packageName, 0)
            useful(local.loadLabel(pm))
        }.getOrNull()?.let { return it }
        // Public resources of an installed APK can still be read when local package lookup
        // is unavailable (for example, a package installed only in a work profile).
        val path = info.publicSourceDir ?: info.sourceDir
        if (!path.isNullOrEmpty()) {
            runCatching {
                @Suppress("DEPRECATION")
                val archive = pm.getPackageArchiveInfo(path, 0)?.applicationInfo
                archive?.sourceDir = path
                archive?.publicSourceDir = path
                useful(archive?.loadLabel(pm))
            }.getOrNull()?.let { return it }
        }
        // Some system components and removed blacklist entries genuinely have no app label.
        return info.packageName
    }
}
