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
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.elevator.qr.MainActivity
import com.elevator.qr.R
import com.elevator.qr.data.SettingsStore

class LocationService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var settingsStore: SettingsStore
    private var lastNotifiedTime: Long = 0
    private var currentIntervalMs: Long = 180000L // 默认 3 分钟

    private var hasTriggeredForOffice: Boolean = false

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
        
        // 保底启动：3分钟 / 100 米，使用基站/Wi-Fi低功耗定位
        updateLocationStrategy(180000L, 100f)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "电梯码自动到达提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "当到达配置的公司或特定地点附近时发送自动提醒"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationStrategy(intervalMs: Long, minDistanceMeters: Float) {
        if (currentIntervalMs == intervalMs) return
        currentIntervalMs = intervalMs

        try {
            locationManager.removeUpdates(this)

            // 1. 基站/Wi-Fi 低功耗网络定位 (保底 100% 可用且极低功耗)
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    intervalMs,
                    minDistanceMeters,
                    this
                )
            }

            // 2. 被动定位 (PASSIVE_PROVIDER) 辅助
            if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER,
                    intervalMs,
                    minDistanceMeters,
                    this
                )
            }

            // 3. 当接近目标 500 米内时，开启 GPS 芯片精准判定
            if (intervalMs <= 15000L && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    intervalMs,
                    minDistanceMeters,
                    this
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!settingsStore.isLocationMonitorEnabled) return

        val now = System.currentTimeMillis()
        val targetLoc = Location("target").apply {
            latitude = settingsStore.targetLat
            longitude = settingsStore.targetLng
        }
        val distance = location.distanceTo(targetLoc)
        val triggerRadius = settingsStore.triggerRadiusMeters

        // 到达后在公司休眠控制：在公司 1.5 公里范围内且今日已通知过，降频至 15 分钟
        if (hasTriggeredForOffice) {
            if (distance > 1500f) {
                // 离开公司超过 1.5 公里，自动重置标志
                hasTriggeredForOffice = false
                updateLocationStrategy(180000L, 100f)
            } else {
                updateLocationStrategy(900000L, 500f)
                return
            }
        }

        // 动态分级采样，确保进站能稳定唤醒
        when {
            distance > 2000f -> {
                // 2公里外：3 分钟采样、100 米移动阀值 (基站网络定位，极省电)
                updateLocationStrategy(180000L, 100f)
            }
            distance > 500f -> {
                // 500m ~ 2km：1 分钟采样、50 米移动阀值
                updateLocationStrategy(60000L, 50f)
            }
            else -> {
                // < 500米：10 秒采样、10 米移动阀值 (开启 GPS + 网络高精定位，确保到站 100% 触发)
                updateLocationStrategy(10000L, 10f)
            }
        }

        // 触发通知：距离在配置半径内（如 200m）且冷静期已满 5 分钟
        if (distance <= triggerRadius && (now - lastNotifiedTime > 5 * 60 * 1000)) {
            lastNotifiedTime = now
            hasTriggeredForOffice = true
            sendNearOfficeNotification(distance)
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
            .setContentText("距离设定位置约 ${distance.toInt()} 米，点击快速出示电梯码")
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
