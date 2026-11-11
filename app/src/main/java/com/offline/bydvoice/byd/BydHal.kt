package com.offline.bydvoice.byd

import android.content.Context
import android.util.Log

/**
 * Direct, offline car control through BYD's own vehicle HAL — the path AutoTark and Di+ use, and
 * the one BYD's own system apps use. Reached by reflection into the hidden `android.hardware.bydauto.*`
 * device classes, which are whitelisted on DiLink (AutoTark reaches them with no hidden-API bypass),
 * so no signature permissions, no Chinese assistant and no cloud are involved — only the normal
 * `BYDAUTO_*_COMMON` permissions this app already declares (the same posture AutoTark ships with).
 *
 * Call shape mirrors AutoTark's helper:
 *   dev = BYDAuto<Domain>Device.getInstance(context)
 *   dev.set(int[]{featureId}, BYDAutoEventValue{ intValue = value })        // write
 *   dev.get(int[]{featureId}, Integer.TYPE).intValue                        // read
 *
 * Feature ids are resolved by NAME from BYDAutoFeatureIds at runtime, because some vary by CAN-bus
 * type (BYDAutoFeatureIds.isCanFD), so the constant correct for THIS car is always used.
 *
 * STATUS: unverified on-car. Reads are side-effect-free; writes actuate the vehicle. Exercise it
 * only through [com.offline.bydvoice.testhook.HalProbeReceiver] (debug builds) until a real write
 * is confirmed on the car; nothing in the live command path calls this yet.
 */
object BydHal {
    private const val TAG = "BydHal"

    /** BYD HAL sentinel for invalid / unsupported / undefined. */
    const val HAL_ERR = -2147482648
    /** BYD HAL sentinel for a native permission denial. */
    const val PERM_DENY = -2147482644

    /** HAL device class names, by domain. */
    object Dev {
        const val AC = "android.hardware.bydauto.ac.BYDAutoAcDevice"
        const val BODYWORK = "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"
        const val DOOR_LOCK = "android.hardware.bydauto.doorlock.BYDAutoDoorLockDevice"
        const val MIRROR = "android.hardware.bydauto.doormirror.BYDAutoRearViewMirrorDevice"
        const val LIGHT = "android.hardware.bydauto.light.BYDAutoLightDevice"
        const val SETTING = "android.hardware.bydauto.setting.BYDAutoSettingDevice"
    }

    private const val EVENT_VALUE = "android.hardware.bydauto.BYDAutoEventValue"
    private const val FEATURE_IDS = "android.hardware.bydauto.BYDAutoFeatureIds"

    @Volatile private var hiddenApiTried = false
    private val deviceCache = HashMap<String, Any?>()

    /** Best-effort lift of the hidden-API block. Harmless no-op where it fails or isn't needed. */
    @Synchronized
    private fun ensureHiddenApi() {
        if (hiddenApiTried) return
        hiddenApiTried = true
        runCatching {
            val vm = Class.forName("dalvik.system.VMRuntime")
            val runtime = vm.getDeclaredMethod("getRuntime").invoke(null)
            vm.getDeclaredMethod("setHiddenApiExemptions", Array<String>::class.java)
                .invoke(runtime, arrayOf("Landroid/hardware/bydauto/"))
        }.onFailure { Log.d(TAG, "hidden-api exemption skipped (usually fine): ${it.message}") }
    }

    private fun device(context: Context, deviceClass: String): Any? {
        ensureHiddenApi()
        return deviceCache.getOrPut(deviceClass) {
            runCatching {
                Class.forName(deviceClass).getMethod("getInstance", Context::class.java)
                    .invoke(null, context.applicationContext)
            }.onFailure { Log.w(TAG, "getInstance($deviceClass) failed", it) }.getOrNull()
        }
    }

    /** Resolve a feature-id constant by name (BYDAutoFeatureIds, then a nested \$[domain] class). */
    fun featureId(name: String, domain: String? = null): Int? {
        ensureHiddenApi()
        fun lookup(cls: String): Int? = runCatching {
            (Class.forName(cls).getField(name).get(null) as? Number)?.toInt()
        }.getOrNull()
        lookup(FEATURE_IDS)?.let { return it }
        if (domain != null) lookup("$FEATURE_IDS\$$domain")?.let { return it }
        return null
    }

    /** Write [value] to [featureId] on [deviceClass]; returns the HAL status (0 = ok; see sentinels). */
    fun set(context: Context, deviceClass: String, featureId: Int, value: Int): Int {
        val dev = device(context, deviceClass) ?: return HAL_ERR
        return runCatching {
            val evCls = Class.forName(EVENT_VALUE)
            val ev = evCls.getConstructor().newInstance()
            evCls.getField("intValue").setInt(ev, value)
            (dev.javaClass.getMethod("set", IntArray::class.java, evCls)
                .invoke(dev, intArrayOf(featureId), ev) as? Number)?.toInt() ?: HAL_ERR
        }.onFailure { Log.w(TAG, "set($deviceClass, $featureId, $value) failed", it) }.getOrDefault(HAL_ERR)
    }

    /** Read [featureId] from [deviceClass]; returns the int value, or null on error/unreachable. */
    fun get(context: Context, deviceClass: String, featureId: Int): Int? {
        val dev = device(context, deviceClass) ?: return null
        return runCatching {
            val ev = dev.javaClass.getMethod("get", IntArray::class.java, Class::class.java)
                .invoke(dev, intArrayOf(featureId), java.lang.Integer.TYPE)
            val v = ev?.javaClass?.getField("intValue")?.getInt(ev)
            if (v == null || v == HAL_ERR) null else v
        }.onFailure { Log.w(TAG, "get($deviceClass, $featureId) failed", it) }.getOrNull()
    }
}
