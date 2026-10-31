package cn.lure.toolbox

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/** Preview and background cues share one playback path, released on actual completion. */
class ReminderTone(context: Context) {
    companion object { const val STREAM = AudioManager.STREAM_MUSIC }
    private val context = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var player: MediaPlayer? = null
    private var released = false
    private var focusHeld = false
    private var startedAt = 0L
    var playbackError: String? = null
        private set
    // Only a failure watchdog; normal playback ends through OnCompletionListener.
    private val watchdog = Runnable { fail("提示音播放超时，请重新开启提醒") }
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener({ change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                finishCue()
            }
        }, handler).build()

    fun blockedReason(): String? = when {
        released -> "提示音已关闭，请重新打开音量面板"
        notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL -> "手机处于勿扰模式，暂不播放提示音"
        audio.ringerMode != AudioManager.RINGER_MODE_NORMAL -> "手机处于静音或振动模式，暂不播放提示音"
        audio.getStreamVolume(STREAM) == 0 -> "媒体音量为零，请先调大音量"
        else -> playbackError
    }

    fun play(): Boolean {
        playbackError = null
        blockedReason()?.let { playbackError = it; return false }
        finishCue()
        val result = try { audio.requestAudioFocus(focusRequest) } catch (_: SecurityException) {
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        log("Transient cue focus result=$result")
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            playbackError = "声音暂时被占用，请稍后重试"
            return false
        }
        focusHeld = true
        return try {
            val cue = MediaPlayer()
            player = cue
            cue.setAudioAttributes(attributes)
            cue.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            cue.setOnPreparedListener {
                if (player === it && !released) {
                    startedAt = SystemClock.elapsedRealtime()
                    it.start()
                    log("Cue started duration=${it.duration}ms")
                }
            }
            cue.setOnCompletionListener {
                if (player === it) {
                    log("Cue completed position=${it.currentPosition}ms elapsed=${SystemClock.elapsedRealtime() - startedAt}ms")
                    finishCue()
                }
            }
            cue.setOnErrorListener { current, what, extra ->
                if (player === current) {
                    log("Cue error what=$what extra=$extra")
                    fail("提示音播放失败，请重新开启提醒")
                }
                true
            }
            context.resources.openRawResourceFd(R.raw.cadence).use {
                cue.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            cue.prepareAsync()
            handler.postDelayed(watchdog, 3000L)
            true
        } catch (_: Exception) {
            fail("提示音播放失败，请重新开启提醒")
            false
        }
    }

    private fun log(message: String) { if (debug) Log.d("LureAudio", message) }
    private fun fail(message: String) { playbackError = message; finishCue() }
    private fun finishCue() {
        handler.removeCallbacks(watchdog)
        val previous = player
        player = null
        previous?.release()
        if (focusHeld) {
            focusHeld = false
            audio.abandonAudioFocusRequest(focusRequest)
            log("Transient cue focus released")
        }
    }
    fun stop() { finishCue(); playbackError = null }
    fun release() { if (!released) { stop(); released = true } }
}
