package ai.zero

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class ZeroSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: android.os.Bundle): VoiceInteractionSession {
        return ZeroSession(this)
    }
}

class ZeroSession(context: android.content.Context) :
    VoiceInteractionSession(context) {

    override fun onShow(args: android.os.Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
    }
}
