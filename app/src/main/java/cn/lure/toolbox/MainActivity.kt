package cn.lure.toolbox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings as SystemSettings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

private val Orange = Color(0xFFFF6B24)
private val Background = Color(0xFF101214)
private val Ink = Color(0xFFF5F4F1)
private val Muted = Color(0xFFA8ADB3)
private val Divider = Color(0xFF34393D)
private val ControlSurface = Color(0xFF171A1D)

data class Tool(val route: String, val title: String, val description: String)
val tools = listOf(Tool("cadence", "逗钓频率提醒器", "跟随节奏，让每一次逗钓更从容"))

class MainActivity : ComponentActivity() {
    private val openReminder = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = ReminderTone.STREAM
        openReminder.value = intent.getBooleanExtra("reminder", false)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Orange, onPrimary = Ink,
                    secondary = Orange, onSecondary = Background,
                    secondaryContainer = Orange, onSecondaryContainer = Ink,
                    background = Background, onBackground = Ink,
                    surface = Background, onSurface = Ink,
                    surfaceVariant = ControlSurface, onSurfaceVariant = Muted,
                    outline = Divider
                )
            ) {
                val nav = rememberNavController()
                val requested by openReminder.collectAsStateWithLifecycle()
                LaunchedEffect(requested) {
                    if (requested) {
                        nav.navigate("cadence") { launchSingleTop = true }
                        openReminder.value = false
                    }
                }
                Surface(Modifier.fillMaxSize(), color = Background) {
                    NavHost(nav, startDestination = "home", modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                        composable("home") { Home { nav.navigate(it) } }
                        composable("cadence") { Reminder(back = { nav.popBackStack() }) }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (ReminderVolumeKeys.handle(this, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (ReminderVolumeKeys.handle(this, event)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openReminder.value = intent.getBooleanExtra("reminder", false)
    }
}

@Composable
private fun Home(open: (String) -> Unit) {
    val state by ReminderService.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text("路亚随身工具", color = Orange, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("路亚工具箱", fontSize = 36.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold)
            Text("专注水面，节奏交给工具。", color = Muted, fontSize = 16.sp)
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("随身工具", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${tools.size} 个工具", color = Muted, fontSize = 13.sp)
            }
            tools.forEach { tool ->
                Surface(
                    onClick = { open(tool.route) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = ControlSurface,
                    contentColor = Ink,
                    border = BorderStroke(1.dp, Divider)
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(shape = CircleShape, color = Orange.copy(alpha = 0.12f)) {
                            Icon(painterResource(R.drawable.ic_timer), null, tint = Orange, modifier = Modifier.padding(14.dp).size(28.dp))
                        }
                        Text(tool.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(tool.description, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
                        HorizontalDivider(color = Divider)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (state.status != TimerEngine.Status.IDLE) "查看当前提醒" else "开启我的节奏", color = Orange, fontSize = 15.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = Orange, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
            Text("为每一次出钓，准备一点顺手。", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Reminder(back: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val saved by vm.settings.collectAsStateWithLifecycle()
    val state by ReminderService.state.collectAsStateWithLifecycle()
    val saveError by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("5") }
    var sound by rememberSaveable { mutableStateOf(true) }
    var vibration by rememberSaveable { mutableStateOf(true) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var showVolume by rememberSaveable { mutableStateOf(false) }
    var showBackgroundHelp by rememberSaveable { mutableStateOf(false) }
    var permissionWarning by rememberSaveable { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Settings?>(null) }
    val active = state.status != TimerEngine.Status.IDLE

    LaunchedEffect(saved) {
        if (!initialized && saved != null) {
            text = saved!!.seconds.toString()
            sound = saved!!.sound
            vibration = saved!!.vibration
            initialized = true
        }
    }
    fun persist() {
        text.toIntOrNull()?.takeIf { it in 1..300 }?.let { vm.save(Settings(it, sound, vibration)) }
    }
    fun start(value: Settings) {
        focus.clearFocus()
        try {
            ReminderService.command(context, ReminderService.START, value)
        } catch (_: RuntimeException) {
            startError = "启动失败，请检查系统后台运行设置后重试"
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionWarning = !granted
        pending?.let { start(it) }
        pending = null
    }
    val number = text.toIntOrNull()
    val valid = number != null && number in 1..300
    val editable = !active && initialized
    val seconds = if (active) ceil(state.remainingMs / 1000.0).toInt() else number?.takeIf { valid } ?: 5
    if (showVolume) ReminderVolumeDialog(onDismiss = { showVolume = false })
    if (showBackgroundHelp) AlertDialog(
        onDismissRequest = { showBackgroundHelp = false },
        title = { Text("允许锁屏后台提醒") },
        text = { Text("锁屏后系统可能暂停提醒。请允许后台运行；小米/红米还需在应用信息的省电策略中选择「无限制」。暂停或结束提醒后，本 App 会释放唤醒锁。") },
        confirmButton = { TextButton(onClick = {
            showBackgroundHelp = false
            val power = context.getSystemService(PowerManager::class.java)
            val intent = if (!power.isIgnoringBatteryOptimizations(context.packageName))
                Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            else Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            try { context.startActivity(intent) } catch (_: RuntimeException) {
                startError = "请在系统设置中打开本 App 的电池设置，允许后台运行"
            }
        }) { Text("打开系统设置") } },
        dismissButton = { TextButton(onClick = { showBackgroundHelp = false }) { Text("稍后") } }
    )
    val progress = if (active) (1f - state.remainingMs / (state.settings.seconds * 1000f)).coerceIn(0f, 1f) else 0.10f

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            // Preserve the visual dial proportions; compact/landscape screens scroll above fixed controls.
            val dialSize = minOf((maxWidth - 48.dp) * 0.70f, (maxHeight - 570.dp).coerceIn(160.dp, 260.dp))
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                ) {
                    TextButton(onClick = back, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(48.dp)) {
                        Icon(painterResource(R.drawable.ic_arrow_back), null, tint = Ink, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("工具箱", color = Muted, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("逗钓提醒", fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
                    Text("让动作保持节奏", color = Muted, fontSize = 18.sp, lineHeight = 26.sp)
                    Spacer(Modifier.height(8.dp))
                    TimerDial(seconds, state.status, progress, dialSize, Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(4.dp))
                    Text("已提醒 ${state.count} 次", color = Muted, fontSize = 14.sp, lineHeight = 24.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Divider)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("周期（秒）", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("范围 1～300 秒", color = Muted, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    PeriodInput(
                        value = if (active) state.settings.seconds.toString() else text,
                        enabled = editable,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onChange = { text = it; persist() }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10, 15, 30).forEach { preset ->
                            PresetButton(
                                preset, selected = (if (active) state.settings.seconds else number) == preset,
                                enabled = editable, modifier = Modifier.weight(1f)
                            ) { focus.clearFocus(); text = preset.toString(); persist() }
                        }
                    }
                    if (!valid && initialized) ErrorText("请输入 1～300 的整数秒")
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Divider)
                    SettingSwitch(R.drawable.ic_volume_up, "声音提醒", "双音提示 · 跟随媒体音量", if (active) state.settings.sound else sound, editable, onVolumeClick = { showVolume = true }) { sound = it; persist() }
                    HorizontalDivider(color = Divider)
                    SettingSwitch(R.drawable.ic_vibration, "振动提醒", "通过振动进行提醒", if (active) state.settings.vibration else vibration, editable) { vibration = it; persist() }
                    if (!sound && !vibration && !active) ErrorText("请至少开启一种提醒方式")
                    (startError ?: state.error ?: saveError)?.let { ErrorText(it) }
                    Spacer(Modifier.height(8.dp))
                }
                Column(
                    Modifier.fillMaxWidth().background(Background).padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!active) {
                        Button(
                            onClick = {
                                focus.clearFocus()
                                val value = Settings(number!!, sound, vibration)
                                vm.save(value)
                                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    pending = value
                                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else start(value)
                            },
                            enabled = valid && (sound || vibration) && initialized,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text("开始提醒", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { ReminderService.command(context, if (state.status == TimerEngine.Status.PAUSED) ReminderService.RESUME else ReminderService.PAUSE) },
                                modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape
                            ) { Text(if (state.status == TimerEngine.Status.PAUSED) "继续提醒" else "暂停提醒", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                            OutlinedButton(
                                onClick = { ReminderService.command(context, ReminderService.STOP) },
                                modifier = Modifier.weight(1f).height(52.dp), border = BorderStroke(1.dp, Divider), shape = CircleShape
                            ) { Text("结束", color = Ink, fontSize = 18.sp) }
                        }
                    }
                    if (permissionWarning) {
                        Text("通知权限未开启，可在系统设置中开启通知栏控制。", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    } else {
                        TextButton(onClick = { showBackgroundHelp = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(28.dp)) {
                            Text("锁屏后台设置 · 音量键调节提示音", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerDial(seconds: Int, status: TimerEngine.Status, progress: Float, diameter: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        // This is a live timer visualization: the arc advances using the service's monotonic clock.
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.465f
            val center = Offset(size.width / 2, size.height / 2)
            val sweep = progress * 360f
            fun point(degrees: Float, distance: Float): Offset {
                val angle = Math.toRadians(degrees.toDouble())
                return center + Offset((cos(angle) * distance).toFloat(), (sin(angle) * distance).toFloat())
            }
            repeat(60) { tick ->
                if (tick * 6f > sweep + 10 || tick == 0) {
                    val angle = -90f + tick * 6
                    drawLine(Color(0xFF646B72), point(angle, radius - 4.dp.toPx()), point(angle, radius), 1.6.dp.toPx(), StrokeCap.Round)
                }
            }
            if (sweep > 0.1f) {
                drawArc(Orange, -86f, sweep, false, center - Offset(radius, radius), Size(radius * 2, radius * 2), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            }
            drawCircle(Orange, 6.dp.toPx(), point(-86f + sweep + 6f, radius))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (status) { TimerEngine.Status.RUNNING -> "正在提醒"; TimerEngine.Status.PAUSED -> "已暂停"; TimerEngine.Status.IDLE -> "准备就绪" },
                color = Orange, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Spacer(Modifier.height(8.dp))
            val numeralSize = (diameter.value * 0.35f).coerceIn(54f, 88f)
            Text(seconds.toString().padStart(2, '0'), color = Ink, fontSize = numeralSize.sp, lineHeight = (numeralSize * 1.08f).sp, fontWeight = FontWeight.Medium, letterSpacing = (-3).sp)
            Text("秒", color = Muted, fontSize = 16.sp, lineHeight = 24.sp)
        }
    }
}

@Composable
private fun PeriodInput(value: String, enabled: Boolean, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val number = value.toIntOrNull()
    Surface(modifier.widthIn(max = 244.dp).fillMaxWidth(), color = ControlSurface, shape = CircleShape, border = BorderStroke(1.dp, Divider)) {
        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(((number ?: 5) - 1).coerceIn(1, 300).toString()) }, enabled = enabled && number != 1, modifier = Modifier.width(56.dp)) {
                Icon(painterResource(R.drawable.ic_remove), "减少周期", tint = if (enabled && number != 1) Muted else Divider)
            }
            VerticalDivider(Modifier.height(46.dp), color = Divider)
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= 6) onChange(it) },
                enabled = enabled, singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                cursorBrush = SolidColor(Orange),
                modifier = Modifier.weight(1f).semantics { contentDescription = "周期（秒）" }
            )
            VerticalDivider(Modifier.height(46.dp), color = Divider)
            IconButton(onClick = { onChange(((number ?: 5) + 1).coerceIn(1, 300).toString()) }, enabled = enabled && number != 300, modifier = Modifier.width(56.dp)) {
                Icon(painterResource(R.drawable.ic_add), "增加周期", tint = if (enabled && number != 300) Orange else Divider)
            }
        }
    }
}

@Composable
private fun PresetButton(seconds: Int, selected: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(48.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick, enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(34.dp).semantics { this.selected = selected },
            shape = RoundedCornerShape(8.dp),
            color = if (selected) Orange else ControlSurface,
            contentColor = Ink,
            border = if (selected) null else BorderStroke(1.dp, Divider)
        ) {
            Box(contentAlignment = Alignment.Center) { Text("${seconds}秒", fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun SettingSwitch(icon: Int, title: String, subtitle: String, checked: Boolean, enabled: Boolean, onVolumeClick: (() -> Unit)? = null, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 66.dp).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = change).semantics { contentDescription = title }, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = Ink, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
        if (onVolumeClick != null) {
            TextButton(onClick = onVolumeClick, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.semantics { contentDescription = "调节提醒音量" }) { Text("音量", fontSize = 13.sp) }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            modifier = Modifier.graphicsLayer { scaleX = 0.82f; scaleY = 0.82f },
            colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Orange, checkedBorderColor = Orange,
                uncheckedTrackColor = ControlSurface, uncheckedBorderColor = Divider, uncheckedThumbColor = Muted,
                disabledCheckedTrackColor = Orange.copy(alpha = 0.45f), disabledCheckedThumbColor = Ink.copy(alpha = 0.7f))
        )
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
}
