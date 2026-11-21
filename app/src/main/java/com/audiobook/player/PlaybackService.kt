package com.audiobook.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val audioExt = listOf(".mp3", ".m4b", ".m4a", ".ogg", ".opus", ".wav", ".flac", ".aac", ".wma")

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    saveState(exoPlayer)
                }
                notifyWidgets(this@PlaybackService)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                saveState(exoPlayer)
                notifyWidgets(this@PlaybackService)
            }
        })

        player = exoPlayer

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivity = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        mediaSession = MediaSession.Builder(this, exoPlayer).apply {
            if (sessionActivity != null) {
                setSessionActivity(sessionActivity)
            }
        }.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val exoPlayer = player ?: return START_STICKY

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                if (exoPlayer.mediaItemCount == 0) {
                    restoreAndPlay(exoPlayer)
                } else if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                } else {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }
            }
            ACTION_FORWARD -> {
                if (exoPlayer.mediaItemCount > 0) {
                    val target = (exoPlayer.currentPosition + 30_000L)
                        .coerceAtMost(exoPlayer.duration.coerceAtLeast(0L))
                    exoPlayer.seekTo(target)
                    saveState(exoPlayer)
                }
            }
            ACTION_REWIND -> {
                if (exoPlayer.mediaItemCount > 0) {
                    val target = (exoPlayer.currentPosition - 15_000L).coerceAtLeast(0L)
                    exoPlayer.seekTo(target)
                    saveState(exoPlayer)
                }
            }
        }
        return START_STICKY
    }

    private fun restoreAndPlay(exoPlayer: ExoPlayer) {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("folder_uri", null) ?: return
        val startIndex = prefs.getInt("idx_$uriStr", prefs.getInt("last_index", 0))
        val startPos = prefs.getLong("pos_$uriStr", prefs.getLong("last_pos", 0L))

        Thread {
            val dir = DocumentFile.fromTreeUri(this, Uri.parse(uriStr)) ?: return@Thread
            val bookName = dir.name ?: "Аудиокнига"
            val files = mutableListOf<DocumentFile>()
            collectAudioFiles(dir, files)
            if (files.isEmpty()) return@Thread

            val items = files.map { file ->
                val title = file.name?.substringBeforeLast('.') ?: "Глава"
                MediaItem.Builder()
                    .setUri(file.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(bookName)
                            .build()
                    )
                    .build()
            }

            android.os.Handler(mainLooper).post {
                val p = player ?: return@post
                val safeIndex = startIndex.coerceIn(0, items.lastIndex)
                p.setMediaItems(items, safeIndex, startPos)
                p.prepare()
                p.playWhenReady = true
                p.play()
            }
        }.start()
    }

    private fun collectAudioFiles(dir: DocumentFile, out: MutableList<DocumentFile>) {
        val entries = dir.listFiles().sortedBy { it.name?.lowercase() }
        for (entry in entries) {
            if (entry.isDirectory) {
                collectAudioFiles(entry, out)
            } else if (entry.isFile) {
                val name = entry.name?.lowercase() ?: ""
                if (audioExt.any { name.endsWith(it) }) {
                    out.add(entry)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = player
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        player?.let { saveState(it) }
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun saveState(exoPlayer: ExoPlayer) {
        if (exoPlayer.mediaItemCount == 0) return
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val currentUri = prefs.getString("folder_uri", null)
        val title = exoPlayer.currentMediaItem?.mediaMetadata?.artist?.toString()

        val editor = prefs.edit()
            .putInt("last_index", exoPlayer.currentMediaItemIndex)
            .putLong("last_pos", exoPlayer.currentPosition.coerceAtLeast(0L))

        if (!currentUri.isNullOrEmpty()) {
            editor.putInt("idx_$currentUri", exoPlayer.currentMediaItemIndex)
            editor.putLong("pos_$currentUri", exoPlayer.currentPosition.coerceAtLeast(0L))
        }
        if (!title.isNullOrEmpty()) {
            editor.putString("last_book_title", title)
        }
        editor.apply()
    }

    companion object {
        fun notifyWidgets(context: Context) {
            context.sendBroadcast(Intent(context, GridWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            })
            context.sendBroadcast(Intent(context, BarWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            })
        }
    }
}
