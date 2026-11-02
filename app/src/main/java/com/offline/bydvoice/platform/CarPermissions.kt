package com.offline.bydvoice.platform

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.util.Log

/**
 * The head unit's vehicle-API permissions (android.permission.BYDAUTO_*). On DiLink the *_COMMON
 * ones are runtime permissions: the driver approves them in Android's permission dialog, just as
 * diplus asks for them on first start. Without them the car refuses every vehicle call.
 */
object CarPermissions {
    private const val TAG = "CarPermissions"
    private const val PREFIX = "android.permission.BYDAUTO_"

    /** The vehicle permissions this app declares that this head unit actually defines. */
    private fun defined(context: Context): List<PermissionInfo> {
        val pm = context.packageManager
        val declared = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        return declared.filter { it.startsWith(PREFIX) }.mapNotNull { runCatching { pm.getPermissionInfo(it, 0) }.getOrNull() }
    }

    /** False on phones and emulators: there's no vehicle API to ask for. */
    fun isCar(context: Context): Boolean = defined(context).isNotEmpty()

    /** Runtime vehicle permissions not granted yet: request these with the permission dialog. */
    fun missing(context: Context): List<String> = defined(context)
        .filter { it.protection == PermissionInfo.PROTECTION_DANGEROUS }
        .map { it.name }
        .filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    /** One log line per permission (adb logcat -s CarPermissions) to diagnose a car that refuses commands. */
    fun log(context: Context) {
        val all = defined(context)
        if (all.isEmpty()) { Log.i(TAG, "no BYDAUTO permissions on this device (not a BYD head unit)"); return }
        for (p in all) {
            val kind = when (p.protection) {
                PermissionInfo.PROTECTION_DANGEROUS -> "runtime"
                PermissionInfo.PROTECTION_NORMAL -> "normal"
                PermissionInfo.PROTECTION_SIGNATURE -> "signature"
                else -> "level ${p.protection}"
            }
            val granted = context.checkSelfPermission(p.name) == PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "${p.name.removePrefix("android.permission.")}: $kind, ${if (granted) "granted" else "NOT granted"}")
        }
    }
}
