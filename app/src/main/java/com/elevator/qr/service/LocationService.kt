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
    private var lastProcessedTime: Long = 0
    private var currentIntervalMs: Long = 900000L // 默认超远距离：15 分钟

    // 💡 状态标志：是否已在公司/已触发弹窗 (进入在公司挂起模式)
    private var hasTriggeredForOffice: Boolean = false

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
        
        // 限制 1：初始化即设为 15 分钟 / 500 米双重极低频
        updateLocationStrategy(900000L, 500f)
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

            // 限制 1 核心：超远距离或进入在公司挂起模式时，仅启用被动定位 PASSIVE_PROVIDER (自身 0 唤醒 0 耗电)
            if (intervalMs >= 900000L) {
                if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.PASSIVE_PROVIDER,
                        intervalMs,
                        minDistanceMeters,
                        this
                    )
                }
                return
            }

            // 1. 基站/Wi-Fi 低功耗网络定位
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    intervalMs,
                    minDistanceMeters,
                    this
                )
            }

            // 2. 被动定位 (PASSIVE_PROVIDER)
            if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER,
                    intervalMs,
                    minDistanceMeters,
                    this
                )
            }

            // 3. 仅当真正接近目标 300 米内时，才短时间开启高精 GPS 芯片
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

        // -------------------------------------------------------------
        // 💡 限制 2：到达公司并已弹窗后的“整天休眠/挂起策略”
        // -------------------------------------------------------------
        if (hasTriggeredForOffice) {
            if (distance > 1500f) {
                // 当检测到离开公司超过 1.5 公里 (下班/离开)，解除在公司休眠模式
                hasTriggeredForOffice = false
                updateLocationStrategy(900000L, 500f) // 恢复远处 15 分钟低频
            } else {
                // 仍处于公司及附近范围：进入整天休眠挂起模式 (30分钟采样、仅被动定位、30分钟防抖)
                if (now - lastProcessedTime < 1800000L) { // 30 分钟内直接硬性 return
                    return
                }
                lastProcessedTime = now
                updateLocationStrategy(1800000L, 500f)
                return
            }
        }

        // -------------------------------------------------------------
        // 💡 限制 1：超远距离下时间+距离双重严控 & 代码级死区硬性防抖
        // -------------------------------------------------------------
        val minAllowableThrottleMs = when {
            distance > 2000f -> 600000L // 离公司 > 2公里：代码层硬性限制 10 分钟内最多处理 1 次
            distance > 500f  -> 120000L // 500m ~ 2km: 2 分钟硬性防抖
            else             -> 8000L   // < 500m: 近距离 8 秒防抖
        }

        if (now - lastProcessedTime < minAllowableThrottleMs) {
            return // 时间+距离双重控频：直接丢弃，0 耗电
        }
        lastProcessedTime = now

        // -------------------------------------------------------------
        // 动态分级采样与逼近检测
        // -------------------------------------------------------------
        val isDrivingFast = location.hasSpeed() && location.speed > 14f

        when {
            distance > 2000f -> {
                // 限制 1 规范：> 2公里，15分钟采样、500米移动阈值 (双重控制)
                updateLocationStrategy(900000L, 500f)
            }
            distance > 1000f || isDrivingFast -> {
                // 1km ~ 2km 或快速驾车：5分钟采样、200米移动阈值
                updateLocationStrategy(300000L, 200f)
            }
            distance > 300f -> {
                // 300m ~ 1km：1分钟采样、50米移动阈值
                updateLocationStrategy(60000L, 50f)
            }
            else -> {
                // < 300米：10秒采样、10米移动阈值 (到达准备)
                updateLocationStrategy(10000L, 10f)
            }
        }

        // -------------------------------------------------------------
        // 到达触发通知
        // -------------------------------------------------------------
        if (distance <= triggerRadius && (now - lastNotifiedTime > 10 * 60 * 1000)) {
            lastNotifiedTime = now
            hasTriggeredForOffice = true // 标记已到达公司，进入在公司挂起休眠模式！
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
