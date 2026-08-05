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

            // Location Monitor Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("到站自动弹出电梯码提醒", fontWeight = FontWeight.SemiBold)
                    Text("检测到靠近目标位置时自动发通知", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                Switch(
                    checked = locationEnabled,
                    onCheckedChange = { locationEnabled = it }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

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
                        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, "请输入有效的经纬度和数值", Toast.LENGTH_SHORT).show()
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
