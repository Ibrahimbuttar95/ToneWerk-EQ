package com.tonewerk.eq.audio

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true          // pauses on calls, resumes after
            )
            .setHandleAudioBecomingNoisy(true)          // pause when headphones unplug
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(id: Int) {
                FxEngine.playerSessionId = id
                if (!FxEngine.globalMode) FxEngine.attach(id, isGlobal = false)
            }
        })

        session = MediaSession.Builder(this, player).build()

        val sid = player.audioSessionId
        if (sid != C.AUDIO_SESSION_ID_UNSET) {
            FxEngine.playerSessionId = sid
            if (!FxEngine.globalMode) FxEngine.attach(sid, isGlobal = false)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || (!p.playWhenReady && p.mediaItemCount == 0)) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
