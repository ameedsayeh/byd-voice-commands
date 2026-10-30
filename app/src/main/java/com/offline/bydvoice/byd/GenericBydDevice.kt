package com.offline.bydvoice.byd

import android.content.Context
import android.hardware.bydauto.AbsBYDAutoDevice

/**
 * Same trick as diplus's z.j0: a do-nothing subclass of AbsBYDAutoDevice whose only purpose is to
 * reach the protected generic set(deviceType, featureId, value). Device types seen in diplus:
 * 1023 for HUD / cabin light, 1004 for auto high beam, 1001 for child locks.
 */
class GenericBydDevice(context: Context) : AbsBYDAutoDevice(context) {
    fun setFeature(deviceType: Int, featureId: Int, value: Int): Int = set(deviceType, featureId, value)

    override fun getDevicetype(): Int = 0
    override fun getType(): Int = 1031
    override fun getFeatureList(): IntArray = IntArray(0)
    override fun getGetPermission(): String? = null
    override fun getSetPermission(): String? = null

    companion object {
        const val TYPE_SETTING = 1023
        const val TYPE_ADAS = 1004
        const val TYPE_DOOR_LOCK = 1001
    }
}
