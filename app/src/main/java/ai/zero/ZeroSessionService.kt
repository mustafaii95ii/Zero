package ai.zero

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

class ZeroSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle): VoiceInteractionSession {
        return ZeroSession(this)
    }
}

/** Called when the user triggers the assistant (side button / long-press Home). */
class ZeroSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        try {
            context.startForegroundService(
                Intent(context, ZeroListener::class.java).setAction(ZeroListener.ACTION_WAKE)
            )
        } catch (e: Exception) {
            Log.e("ZERO", "assistant launch failed", e)
        }
        hide()
    }
}
