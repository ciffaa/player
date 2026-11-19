package com.audiobook.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.audiobook.player.PLAY_PAUSE"
        const val ACTION_REWIND = "com.audiobook.player.REWIND"
        const val ACTION_FORWARD = "com.audiobook.player.FORWARD"
    }

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
                syncWidgets(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncWidgets(player)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                WidgetUpdater.setArtwork(mediaMetadata.artworkData)
                syncWidgets(player)
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player
        if (player != null && intent?.action != null) {
            if (player.mediaItemCount == 0) {
                val open = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(open)
            } else {
                when (intent.action) {
                    ACTION_PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
                    ACTION_REWIND -> player.seekTo((player.currentPosition - 15_000).coerceAtLeast(0))
                    ACTION_FORWARD -> player.seekTo((player.currentPosition + 30_000).coerceAtMost(player.duration.coerceAtLeast(0)))
                }
                syncWidgets(player)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun syncWidgets(player: Player) {
        val meta = player.currentMediaItem?.mediaMetadata
        WidgetUpdater.lastBook = meta?.artist?.toString() ?: "Аудиокниги"
        WidgetUpdater.lastChapter = meta?.title?.toString() ?: "Глава"
        WidgetUpdater.lastIsPlaying = player.isPlaying
        WidgetUpdater.updateAll(this)
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
