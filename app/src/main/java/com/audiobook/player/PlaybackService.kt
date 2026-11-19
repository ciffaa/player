package com.audiobook.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) savePosition(player)
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun savePosition(player: Player) {
        if (player.mediaItemCount == 0) return
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("last_index", player.currentMediaItemIndex)
            .putLong("last_pos", player.currentPosition)
            .apply()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            savePosition(player)
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
