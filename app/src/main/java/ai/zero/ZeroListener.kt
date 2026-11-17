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

class ZeroListener : Service() {

    companion object {
        private const val CHANNEL = "zero_listener"
        private const val ACTION_STOP = "ai.zero.STOP_LISTENING"
    }

    private val main = Handler(Looper.getMainLooper())
    private var voice: Voice? = null
    private var active = false

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
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()

        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("ZERO")
            .setContentText("المساعد الصوتي يعمل")
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

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                10,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(10, notification)
        }

        active = true
        listenAgain()
        return START_NOT_STICKY
    }

    private fun listenAgain() {
        if (!active) return

        voice?.listen(
            onText = { text ->
                if (!active) return@listen

                Zero.handle(this, text) { reply ->
                    main.post {
                        if (active) {
                            voice?.speak(reply)
                            main.postDelayed({ listenAgain() }, 1200)
                        }
                    }
                }
            },
            onFail = {
                if (active) {
                    main.postDelayed({ listenAgain() }, 1500)
                }
            }
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL,
                "ZERO Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun stopListening() {
        active = false
        main.removeCallbacksAndMessages(null)
        voice?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        active = false
        main.removeCallbacksAndMessages(null)
        voice?.stop()
        voice = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
