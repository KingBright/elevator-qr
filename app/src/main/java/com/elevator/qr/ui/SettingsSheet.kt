package com.elevator.qr.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elevator.qr.data.SettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settingsStore: SettingsStore,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var latText by remember { mutableStateOf(settingsStore.targetLat.toString()) }
    var lngText by remember { mutableStateOf(settingsStore.targetLng.toString()) }
    var radiusText by remember { mutableStateOf(settingsStore.triggerRadiusMeters.toInt().toString()) }
    var locationEnabled by remember { mutableStateOf(settingsStore.isLocationMonitorEnabled) }
    var floorText by remember { mutableStateOf(settingsStore.selectedFloor.toString()) }

    // 时间段设置
    var timeWindowEnabled by remember { mutableStateOf(settingsStore.isTimeWindowEnabled) }
    var start1Text by remember { mutableStateOf(settingsStore.timeStart1) }
    var end1Text by remember { mutableStateOf(settingsStore.timeEnd1) }
    var start2Text by remember { mutableStateOf(settingsStore.timeStart2) }
    var end2Text by remember { mutableStateOf(settingsStore.timeEnd2) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "系统与定位配置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Floor Setting
            OutlinedTextField(
                value = floorText,
                onValueChange = { floorText = it },
                label = { Text("默认目标楼层 (如 11)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Location Monitor Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("到站自动弹出电梯码提醒", fontWeight = FontWeight.SemiBold)
                    Text("靠近目标位置且在指定时间段内自动弹窗", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                Switch(
                    checked = locationEnabled,
                    onCheckedChange = { locationEnabled = it }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Time Window Strategy Setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用时间段双重限定 (推荐)", fontWeight = FontWeight.SemiBold)
                    Text("仅在设定的上下班时间段高精度监听，时间段外彻底休眠", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                Switch(
                    checked = timeWindowEnabled,
                    onCheckedChange = { timeWindowEnabled = it }
                )
            }

            if (timeWindowEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("时间段 1 (例如早晨上班):", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = start1Text,
                        onValueChange = { start1Text = it },
                        label = { Text("开始 (HH:mm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = end1Text,
                        onValueChange = { end1Text = it },
                        label = { Text("结束 (HH:mm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("时间段 2 (例如晚上下班):", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = start2Text,
                        onValueChange = { start2Text = it },
                        label = { Text("开始 (HH:mm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = end2Text,
                        onValueChange = { end2Text = it },
                        label = { Text("结束 (HH:mm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("目标地点坐标设置 (如公司位置)", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("纬度 (Latitude)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it },
                    label = { Text("经度 (Longitude)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val loc = getCurrentLocation(context)
                    if (loc != null) {
                        latText = loc.first.toString()
                        lngText = loc.second.toString()
                        Toast.makeText(context, "已成功抓取当前坐标", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "未能获取当前位置，请确认开启GPS权限", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("设置为当前实际位置")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = radiusText,
                onValueChange = { radiusText = it },
                label = { Text("提醒半径 (米)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    try {
                        settingsStore.targetLat = latText.toDouble()
                        settingsStore.targetLng = lngText.toDouble()
                        settingsStore.triggerRadiusMeters = radiusText.toFloat()
                        settingsStore.isLocationMonitorEnabled = locationEnabled
                        settingsStore.selectedFloor = floorText.toInt().coerceIn(1, 99)
                        
                        settingsStore.isTimeWindowEnabled = timeWindowEnabled
                        settingsStore.timeStart1 = start1Text
                        settingsStore.timeEnd1 = end1Text
                        settingsStore.timeStart2 = start2Text
                        settingsStore.timeEnd2 = end2Text

                        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, "请输入有效的数字和时间格式", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存设置", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun getCurrentLocation(context: Context): Pair<Double, Double>? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val locNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val loc = locGps ?: locNet
        if (loc != null) Pair(loc.latitude, loc.longitude) else null
    } catch (e: Exception) {
        null
    }
}
