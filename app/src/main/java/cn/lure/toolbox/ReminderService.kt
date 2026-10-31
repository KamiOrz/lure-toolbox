package cn.lure.toolbox

import android.app.*
import android.content.Intent
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

 data class TimerState(val status: TimerEngine.Status = TimerEngine.Status.IDLE, val remainingMs: Long = 5000, val count: Long = 0, val settings: Settings = Settings(), val error: String? = null)
class ReminderService: Service() {
    companion object {
        private val mutableState = MutableStateFlow(TimerState())
        val state = mutableState.asStateFlow()
        const val START = "start"; const val PAUSE = "pause"; const val RESUME = "resume"; const val STOP = "stop"
        fun command(context: Context, action: String, settings: Settings = Settings()) {
            val intent = Intent(context, ReminderService::class.java).setAction(action)
                .putExtra("seconds", settings.seconds).putExtra("sound", settings.sound).putExtra("vibration", settings.vibration)
            if(action == START) context.startForegroundService(intent) else context.startService(intent)
        }
    }
    private val engine = TimerEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var settings = Settings()
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var tone: ReminderTone
    private var renewWakeAt = 0L
    private val vibrationAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val manager get() = getSystemService(NotificationManager::class.java)
    override fun onCreate() {
        super.onCreate()
        manager.createNotificationChannel(NotificationChannel("cadence", "逗钓提醒运行状态", NotificationManager.IMPORTANCE_LOW).apply { setSound(null,null); enableVibration(false) })
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Lure:Cadence").apply { setReferenceCounted(false) }
        tone = ReminderTone(this)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when(intent?.action) {
                START -> {
                    if(engine.status != TimerEngine.Status.IDLE) return START_NOT_STICKY
                    settings = Settings(intent.getIntExtra("seconds",5),intent.getBooleanExtra("sound",true),intent.getBooleanExtra("vibration",true))
                    if(settings.seconds !in 1..300 || (!settings.sound && !settings.vibration)) { stopSelf(); return START_NOT_STICKY }
                    engine.start(settings.seconds, SystemClock.elapsedRealtime())
                    publish()
                    if (Build.VERSION.SDK_INT >= 29) {
                        val types = (if (settings.sound) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0) or
                            (if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
                        startForeground(1, notification(), types)
                    } else startForeground(1, notification())
                    acquireWake()
                    startTicker()
                }
                PAUSE -> if(engine.status == TimerEngine.Status.RUNNING) {
                    engine.pause(SystemClock.elapsedRealtime()); ticker?.cancel(); tone.stop(); releaseWake(); publish(); updateNotification()
                }
                RESUME -> if(engine.status == TimerEngine.Status.PAUSED) {
                    engine.resume(SystemClock.elapsedRealtime()); acquireWake(); publish(); updateNotification(); startTicker()
                }
                STOP -> finishTimer()
                else -> if(engine.status == TimerEngine.Status.IDLE) stopSelf()
            }
        } catch (_: RuntimeException) {
            finishTimer("无法启动提醒，请检查系统通知及后台运行设置后重试")
        }
        return START_NOT_STICKY
    }
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while(isActive) {
                val now = SystemClock.elapsedRealtime()
                if(now >= renewWakeAt) acquireWake()
                val reminded = engine.tick(now)
                if(reminded) alert()
                publish()
                if(reminded) updateNotification()
                delay(50)
            }
        }
    }
    private fun alert() {
        val audio = getSystemService(AudioManager::class.java)
        // Respect silent mode and DND, including haptics during DND.
        if(manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        if(settings.sound) tone.play()
        if(settings.vibration && audio.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(180,VibrationEffect.DEFAULT_AMPLITUDE), vibrationAttributes)
        }
    }
    private fun publish(error: String? = null) { mutableState.value = TimerState(engine.status,engine.remaining(SystemClock.elapsedRealtime()),engine.count,settings,error ?: if(settings.sound) tone.playbackError else null) }
    private fun pending(action: String, code: Int) = PendingIntent.getService(this,code,Intent(this,ReminderService::class.java).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notification(): Notification {
        val paused = engine.status == TimerEngine.Status.PAUSED
        val open = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).putExtra("reminder",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,"cadence").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if(paused) "逗钓提醒 · 已暂停" else "逗钓提醒 · 运行中")
            .setContentText(if(paused) "剩余 ${(engine.remaining(SystemClock.elapsedRealtime())+999)/1000} 秒 · 已提醒 ${engine.count} 次" else "每 ${settings.seconds} 秒提醒 · 已提醒 ${engine.count} 次")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null,if(paused) "继续" else "暂停",pending(if(paused) RESUME else PAUSE,1)).build())
            .addAction(Notification.Action.Builder(null,"结束",pending(STOP,2)).build()).build()
    }
    private fun updateNotification() { manager.notify(1,notification()) }
    private fun acquireWake() { wakeLock.acquire(10 * 60 * 1000L); renewWakeAt = SystemClock.elapsedRealtime() + 5 * 60 * 1000L }
    private fun releaseWake() { if(wakeLock.isHeld) wakeLock.release() }
    private fun finishTimer(error: String? = null) { ticker?.cancel(); tone.stop(); engine.stop(); releaseWake(); publish(error); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { ticker?.cancel(); scope.cancel(); releaseWake(); tone.release(); engine.stop(); publish(mutableState.value.error); super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}
