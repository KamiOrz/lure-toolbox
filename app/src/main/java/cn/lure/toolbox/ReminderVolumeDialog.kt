package cn.lure.toolbox

import android.app.Dialog
import android.media.AudioManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun ReminderVolumeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    val maximum = remember(audio) { audio.getStreamMaxVolume(ReminderTone.STREAM).coerceAtLeast(1) }
    var level by remember { mutableFloatStateOf(audio.getStreamVolume(ReminderTone.STREAM).toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val tone = remember(context) { ReminderTone(context.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(tone) { onDispose { tone.release() } }
    LaunchedEffect(lifecycle, audio) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                if (!dragging) level = audio.getStreamVolume(ReminderTone.STREAM).toFloat()
                delay(250)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提醒音量") },
        text = {
            VolumeDialogKeys()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${(level / maximum * 100).roundToInt()}%")
                Slider(
                    value = level.coerceIn(0f, maximum.toFloat()),
                    valueRange = 0f..maximum.toFloat(),
                    onValueChange = {
                        dragging = true
                        try {
                            audio.setStreamVolume(ReminderTone.STREAM, it.roundToInt().coerceIn(0, maximum), 0)
                            // Read back the actual system value, including any device volume limit.
                            level = audio.getStreamVolume(ReminderTone.STREAM).toFloat()
                            message = null
                        } catch (_: SecurityException) {
                            level = audio.getStreamVolume(ReminderTone.STREAM).toFloat()
                            message = "系统未允许调整，请使用手机媒体音量设置"
                        }
                    },
                    onValueChangeFinished = { dragging = false },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "提醒音量滑块" }
                )
                Text("与手机媒体音量同步，也可使用侧面音量键调整。")
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        dismissButton = {
            TextButton(onClick = {
                message = if (tone.play()) null else tone.blockedReason() ?: "提示音暂未准备好，请稍后重试"
            }) { Text("试听") }
        }
    )
}


@Composable
private fun VolumeDialogKeys() {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.setVolumeControlStream(ReminderTone.STREAM)
        val dialog = window?.callback as? Dialog
        dialog?.setOnKeyListener { _, _, event -> ReminderVolumeKeys.handle(context, event) }
        onDispose { dialog?.setOnKeyListener(null) }
    }
}
