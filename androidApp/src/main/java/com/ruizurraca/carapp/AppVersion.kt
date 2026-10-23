package com.ruizurraca.carapp

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

/**
 * The app version the Settings screen shows (`docs/SPECIFICATION.md §3.1`). D-212 makes each host read
 * its own version natively, so this reads the installed package and no shared contract carries it.
 */
internal fun Context.appVersionName(): String {
    val info = installedPackageInfo()
    return appVersionLabel(
        versionName = info.versionName,
        versionCode = PackageInfoCompat.getLongVersionCode(info),
    )
}

/**
 * The version label: the package `versionName` verbatim, or the `versionCode` when the package
 * declares no usable name, so the version row is never empty.
 */
internal fun appVersionLabel(
    versionName: String?,
    versionCode: Long,
): String = versionName?.takeIf(String::isNotBlank) ?: versionCode.toString()

private fun Context.installedPackageInfo(): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
