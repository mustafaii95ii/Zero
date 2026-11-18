package ai.zero

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log

private const val TAG = "ZERO"

/**
 * Always-on ears. Speech is IGNORED unless it contains the wake word ("زيرو" / "zero").
 * "زيرو" alone -> answers "تفضل" and treats the next sentence as the command.
 * Exactly one listen -> handle -> speak -> listen chain runs at a time.
 */
class ZeroListener : Service() {

    companion object {
        private const val CHANNEL = "zero_listener"
        private const val ACTION_STOP = "ai.zero.STOP_LISTENING"
        private const val WAKE_WINDOW_MS = 10_000L
    }

    private val main = Handler(Looper.getMainLooper())
    private var voice: Voice? = null
    private var active = false
    private var awakeUntil = 0L
    private var wake: WakeWord? = null
    private var vosk = false   // true while the offline wake-word engine is in charge

    override fun onCreate() {
        super.onCreate()
        voice = Voice(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopListening()
            return START_NOT_STICKY
        }

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO not granted, stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()

        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("ZERO")
            .setContentText("المساعد الصوتي يعمل — قل: زيرو")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "إيقاف",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, ZeroListener::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        startForeground(10, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

        if (!active) {          // never start a second listening chain
            active = true
            beginListening()
        }
        return START_NOT_STICKY
    }

    // ---- Wake-word mode (Vosk): the mic stays open quietly; the system speech
    //      recognizer opens ONLY after "zero" was heard (no more open/close beeps). ----
    private fun beginListening() {
        val w = WakeWord(this) { onWake() }
        wake = w
        w.load(
            onReady = {
                if (active) {
                    vosk = w.start()
                    Log.d(TAG, "wake-word engine started: $vosk")
                    if (!vosk) startListen()
                }
            },
            onError = { msg ->
                Log.e(TAG, "wake-word engine unavailable ($msg), falling back to STT loop")
                vosk = false
                if (active) startListen()
            }
        )
    }

    private fun onWake() {
        if (!active) return
        Log.d(TAG, "wake word detected")
        voice?.speak(Say.p(this, "تفضل", "Yes, sir?")) { listenForCommand() }
    }

    private fun listenForCommand() {
        if (!active) return
        voice?.listen(
            onText = { raw -> if (active) runCommand(Text.stripWake(raw) ?: raw) },
            onFail = { resumeWake() }
        )
    }

    private fun runCommand(command: String) {
        if (command.isBlank()) { resumeWake(); return }
        Zero.handle(
            this, command,
            onInterim = { voice?.speak(it) },
            onFinal = { reply -> if (active) voice?.speak(reply) { resumeWake() } }
        )
    }

    private fun resumeWake() {
        if (!active) return
        main.postDelayed({
            if (active && wake?.start() != true) {
                Log.e(TAG, "wake word restart failed, falling back to STT loop")
                vosk = false
                startListen()
            }
        }, 300)
    }

    // ---- Fallback (no wake-word engine): old loop, ignores speech without "zero" ----
    private fun startListen() {
        if (!active) return
        voice?.listen(
            onText = { raw -> if (active) onHeard(raw) },
            onFail = { listenAgain(800) }
        )
    }

    private fun listenAgain(delayMs: Long = 300) {
        if (!active) return
        main.postDelayed({ if (active) startListen() }, delayMs)
    }

    private fun onHeard(raw: String) {
        val now = SystemClock.elapsedRealtime()
        val afterWake = Text.stripWake(raw)

        val command = when {
            afterWake != null -> afterWake
            now < awakeUntil -> raw
            else -> {
                Log.d(TAG, "ignored (no wake word): $raw")
                listenAgain()
                return
            }
        }

        if (command.isBlank()) {                 // only the wake word was said
            awakeUntil = now + WAKE_WINDOW_MS
            voice?.speak(Say.p(this, "تفضل", "Yes, sir?")) { listenAgain() }
            return
        }

        awakeUntil = 0L
        Zero.handle(
            this, command,
            onInterim = { voice?.speak(it) },
            onFinal = { reply ->
                if (active) voice?.speak(reply) { listenAgain() }
            }
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL, "ZERO Voice Assistant", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun stopListening() {
        active = false
        main.removeCallbacksAndMessages(null)
        wake?.release(); wake = null
        voice?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        active = false
        main.removeCallbacksAndMessages(null)
        wake?.release(); wake = null
        voice?.shutdown()
        voice = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
