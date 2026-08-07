package com.elevator.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Elevator
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.elevator.qr.crypto.QrGenerator
import com.elevator.qr.data.SettingsStore
import com.elevator.qr.service.LocationService
import com.elevator.qr.ui.SettingsSheet
import com.elevator.qr.ui.StyledQrCanvas
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            LocationService.startService(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SettingsStore(this)

        // 保持屏幕常亮与提高亮度，方便电梯扫码器硬件极速识别
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val layoutParams = window.attributes
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = layoutParams

        requestRequiredPermissions()

        setContent {
            ElevatorTheme {
                MainScreen(settingsStore = settingsStore)
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            LocationService.startService(this)
        }
    }
}

@Composable
fun ElevatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF3B82F6),
            secondary = Color(0xFF60A5FA),
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B),
            onBackground = Color(0xFFF8FAFC),
            onSurface = Color(0xFFF1F5F9)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 当前展示的楼层 (默认加载设置中的 defaultFloor)
    var selectedFloor by remember { mutableIntStateOf(settingsStore.defaultFloor) }
    var qrContent by remember { mutableStateOf("") }
    var timestamp by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(30) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showFloorPickerModal by remember { mutableStateOf(false) }

    fun refreshQr(targetFloor: Int = selectedFloor) {
        val floorVal = targetFloor.coerceIn(1, 99)
        selectedFloor = floorVal

        val result = QrGenerator.generateElevatorQrCode(
            floor = floorVal,
            prefix = settingsStore.customPrefix,
            secretKey = settingsStore.secretKey,
            ivKey = settingsStore.ivKey
        )
        qrContent = result.first
        timestamp = result.second
        countdown = 30
    }

    // 💡 优化需求：每次重新打开/从后台切回前台时，自动恢复显示“默认楼层”！
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val defaultFl = settingsStore.defaultFloor
                refreshQr(defaultFl)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto Refresh Timer & Countdown
    LaunchedEffect(selectedFloor) {
        refreshQr(selectedFloor)
        while (true) {
            delay(1000)
            if (countdown > 1) {
                countdown--
            } else {
                refreshQr(selectedFloor)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "电梯乘车码",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E1B4B),
                            Color(0xFF0F172A)
                        )
                    )
                )
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                // 🎨 高颜值电梯楼层控制面板
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 楼层选择区域
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showFloorPickerModal = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Elevator,
                                contentDescription = null,
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                val isTempOverride = selectedFloor != settingsStore.defaultFloor
                                Text(
                                    text = if (isTempOverride) "临时楼层 (默认 ${settingsStore.defaultFloor}F)" else "默认目标楼层",
                                    fontSize = 11.sp,
                                    color = if (isTempOverride) Color(0xFFF59E0B) else Color(0xFF94A3B8)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${selectedFloor} 层",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // 胶囊加减步进器
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                IconButton(
                                    onClick = { if (selectedFloor > 1) refreshQr(selectedFloor - 1) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }

                                VerticalDivider(
                                    modifier = Modifier
                                        .height(16.dp)
                                        .width(1.dp),
                                    color = Color(0xFF334155)
                                )

                                IconButton(
                                    onClick = { if (selectedFloor < 99) refreshQr(selectedFloor + 1) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // High-contrast Card Container for 100% Hardware Scanner Recognition
                Card(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        // Status Badge
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF059669))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${selectedFloor}F 加密码 (${countdown}s 后自动刷新)",
                                    fontSize = 12.sp,
                                    color = Color(0xFF059669),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 100% Standard B&W QR Code Canvas
                        StyledQrCanvas(
                            content = qrContent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "请将手机屏幕贴近电梯扫码感应区",
                            color = Color(0xFF475569),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "时间戳: $timestamp",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { refreshQr(selectedFloor) },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB)
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("手动刷新", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("ElevatorQR", qrContent)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "二维码原始指令已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("复制指令", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // 🎨 楼层选择网格弹窗 (Modern Floor Grid Modal)
        if (showFloorPickerModal) {
            ModalBottomSheet(
                onDismissRequest = { showFloorPickerModal = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "选择临时到达楼层",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "仅本次生效，再次打开自动恢复 ${settingsStore.defaultFloor}F 默认楼层",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items((1..30).toList()) { f ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedFloor == f) Color(0xFF2563EB) else Color(0xFF1E293B),
                                modifier = Modifier
                                    .aspectRatio(1.2f)
                                    .clickable {
                                        refreshQr(f)
                                        showFloorPickerModal = false
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = "${f}F",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSettingsSheet) {
            SettingsSheet(
                settingsStore = settingsStore,
                onDismiss = {
                    showSettingsSheet = false
                    refreshQr(settingsStore.defaultFloor)
                }
            )
        }
    }
}
