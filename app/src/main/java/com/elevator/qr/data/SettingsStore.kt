package com.elevator.qr.data

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("elevator_qr_prefs", Context.MODE_PRIVATE)

    var targetLat: Double
        get() = Double.fromBits(prefs.getLong(KEY_TARGET_LAT, 39.9042.toBits()))
        set(value) = prefs.edit().putLong(KEY_TARGET_LAT, value.toBits()).apply()

    var targetLng: Double
        get() = Double.fromBits(prefs.getLong(KEY_TARGET_LNG, 116.4074.toBits()))
        set(value) = prefs.edit().putLong(KEY_TARGET_LNG, value.toBits()).apply()

    var triggerRadiusMeters: Float
        get() = prefs.getFloat(KEY_TRIGGER_RADIUS, 200f)
        set(value) = prefs.edit().putFloat(KEY_TRIGGER_RADIUS, value).apply()

    var isLocationMonitorEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LOCATION_ENABLED, value).apply()

    var customPrefix: String
        get() = prefs.getString(KEY_PREFIX, "UM_&E&21&0&") ?: "UM_&E&21&0&"
        set(value) = prefs.edit().putString(KEY_PREFIX, value).apply()

    var secretKey: String
        get() = prefs.getString(KEY_SECRET_KEY, "Remain2017150705") ?: "Remain2017150705"
        set(value) = prefs.edit().putString(KEY_SECRET_KEY, value).apply()

    var ivKey: String
        get() = prefs.getString(KEY_IV_KEY, "201707Remain1505") ?: "201707Remain1505"
        set(value) = prefs.edit().putString(KEY_IV_KEY, value).apply()

    companion object {
        private const val KEY_TARGET_LAT = "target_lat"
        private const val KEY_TARGET_LNG = "target_lng"
        private const val KEY_TRIGGER_RADIUS = "trigger_radius"
        private const val KEY_LOCATION_ENABLED = "location_enabled"
        private const val KEY_PREFIX = "custom_prefix"
        private const val KEY_SECRET_KEY = "secret_key"
        private const val KEY_IV_KEY = "iv_key"
    }
}
