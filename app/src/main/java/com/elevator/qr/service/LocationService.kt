package com.elevator.qr.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.elevator.qr.MainActivity
import com.elevator.qr.R
import com.elevator.qr.data.SettingsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var settingsStore: SettingsStore
    private var lastNotifiedTime: Long = 0
    private var currentIntervalMs: Long = -1L

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
        evaluateAndApplyStrategy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "电梯码自动到达提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "当在设定时间段且靠近目标地点时自动发送通知"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 💡 判断当前系统时间是否处于用户设定的活动时间段内 (例如 07:30 - 09:30 或 17:30 - 19:30)
     */
    private fun isInActiveTimeWindow(): Boolean {
        if (!settingsStore.isTimeWindowEnabled) return true // 若未开启时间段限制，默认全天生效

        return try {
            val nowTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val inWindow1 = isCurrentTimeBetween(nowTimeStr, settingsStore.timeStart1, settingsStore.timeEnd1)
            val inWindow2 = isCurrentTimeBetween(nowTimeStr, settingsStore.timeStart2, settingsStore.timeEnd2)
            inWindow1 || inWindow2
        } catch (e: Exception) {
            true
        }
    }

    private fun isCurrentTimeBetween(now: String, start: String, end: String): Boolean {
        if (start.isBlank() || end.isBlank()) return false
        return now >= start && now <= end
    }

    /**
     * 💡 根据“时间段 + 距离”双重条件评估并选择最佳定位策略
     */
    @SuppressLint("MissingPermission")
    private fun evaluateAndApplyStrategy(distanceToTargetMeters: Float = 99999f) {
        val inTimeWindow = isInActiveTimeWindow()

        // 1. 如果不在设定时间段内：彻底切入“极低功耗挂起”状态 (30分钟采样 / 纯被动定位，耗电接近 0)
        val targetIntervalMs = if (!inTimeWindow) {
            1800000L // 30 分钟
        } else {
            // 2. 在时间段内：采用高灵敏度分级策略！放宽限制，确保 100% 触发弹窗
            when {
                distanceToTargetMeters > 2000f -> 120000L // >2km: 2分钟采样
                distanceToTargetMeters > 500f  -> 30000L  // 500m~2km: 30秒采样
                else                           -> 10000L  // <500m: 10秒高精采样 (到站秒弹)
            }
        }

        if (currentIntervalMs == targetIntervalMs) return
        currentIntervalMs = targetIntervalMs

        try {
            locationManager.removeUpdates(this)

            if (!inTimeWindow) {
                // 时间段外：仅注册 PASSIVE_PROVIDER (0 额外耗电)
                if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.PASSIVE_PROVIDER,
                        targetIntervalMs,
                        500f,
                        this
                    )
                }
                return
            }

            // 时间段内：高灵敏度混合定位 (NETWORK + GPS)
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    targetIntervalMs,
                    if (targetIntervalMs <= 10000L) 10f else 50f,
                    this
                )
            }

            if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER,
                    targetIntervalMs,
                    if (targetIntervalMs <= 10000L) 10f else 50f,
                    this
                )
            }

            // 靠近 500 米内开启 GPS 硬件保障 100% 秒读
            if (targetIntervalMs <= 30000L && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    targetIntervalMs,
                    10f,
                    this
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!settingsStore.isLocationMonitorEnabled) return

        val targetLoc = Location("target").apply {
            latitude = settingsStore.targetLat
            longitude = settingsStore.targetLng
        }
        val distance = location.distanceTo(targetLoc)
        val triggerRadius = settingsStore.triggerRadiusMeters

        // 根据最新距离与时间段更新定位灵敏度
        evaluateAndApplyStrategy(distance)

        // 仅在指定时间段内，且靠近目标半径 (如 200m) 时触发通知
        if (isInActiveTimeWindow() && distance <= triggerRadius) {
            val now = System.currentTimeMillis()
            // 防重复报警：5 分钟内不重复弹窗
            if (now - lastNotifiedTime > 5 * 60 * 1000) {
                lastNotifiedTime = now
                sendNearOfficeNotification(distance)
            }
        }
    }

    private fun sendNearOfficeNotification(distance: Float) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_FROM_NOTIFICATION", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📍 已到达目标地点附近")
            .setContentText("距离设定位置约 ${distance.toInt()} 米，点击快速出示 ${settingsStore.selectedFloor}F 电梯码")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val CHANNEL_ID = "elevator_qr_location_channel"
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, LocationService::class.java)
            context.startService(intent)
        }
    }
}
