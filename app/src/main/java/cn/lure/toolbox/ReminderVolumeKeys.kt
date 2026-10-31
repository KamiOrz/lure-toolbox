package cn.lure.toolbox

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import kotlin.math.roundToInt

/** Explicitly target our playback stream; some device skins ignore the suggested stream. */
object ReminderVolumeKeys {
    fun handle(context: Context, event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val audio = context.getSystemService(AudioManager::class.java)
            val maximum = audio.getStreamMaxVolume(ReminderTone.STREAM)
            val current = audio.getStreamVolume(ReminderTone.STREAM)
            val step = (maximum / 15f).roundToInt().coerceAtLeast(1)
            try {
                audio.setStreamVolume(ReminderTone.STREAM, (current + direction * step).coerceIn(0, maximum), AudioManager.FLAG_SHOW_UI)
            } catch (_: SecurityException) {
                // The volume panel still exposes read-back and an actionable permission error.
            }
        }
        return true
    }
}
