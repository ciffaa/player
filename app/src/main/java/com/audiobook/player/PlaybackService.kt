package com.audiobook.player

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
                savePosition(player)
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
        val action = intent?.action
        if (player != null && action != null) {
            if (player.mediaItemCount == 0) {
                val loaded = restoreSavedBook(player)
                if (!loaded) {
                    val open = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(open)
                    return super.onStartCommand(intent, flags, startId)
                }
            }
            when (action) {
                ACTION_PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
                ACTION_REWIND -> {
                    player.seekTo((player.currentPosition - 15_000).coerceAtLeast(0))
                    savePosition(player)
                }
                ACTION_FORWARD -> {
                    player.seekTo((player.currentPosition + 30_000).coerceAtMost(player.duration.coerceAtLeast(0)))
                    savePosition(player)
                }
            }
            syncWidgets(player)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun restoreSavedBook(player: Player): Boolean {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("folder_uri", null) ?: return false
        val startIndex = prefs.getInt("idx_$uriStr", prefs.getInt("last_index", 0))
        val startPos = prefs.getLong("pos_$uriStr", prefs.getLong("last_pos", 0L))

        val dir = DocumentFile.fromTreeUri(this, Uri.parse(uriStr)) ?: return false
        val bookName = dir.name ?: "Аудиокнига"
        val audioExt = listOf(".mp3", ".m4b", ".m4a", ".ogg", ".opus", ".wav", ".flac", ".aac", ".wma")

        val files = mutableListOf<DocumentFile>()
        collectAudioFiles(dir, audioExt, files)
        if (files.isEmpty()) return false

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

        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        player.setMediaItems(items, safeIndex, startPos)
        player.prepare()
        return true
    }

    private fun collectAudioFiles(dir: DocumentFile, audioExt: List<String>, out: MutableList<DocumentFile>) {
        val entries = dir.listFiles().sortedBy { it.name?.lowercase() }
        for (entry in entries) {
            if (entry.isDirectory) {
                collectAudioFiles(entry, audioExt, out)
            } else if (entry.isFile) {
                val name = entry.name?.lowercase() ?: ""
                if (audioExt.any { name.endsWith(it) }) {
                    out.add(entry)
                }
            }
        }
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
        val currentUri = prefs.getString("folder_uri", null)
        val editor = prefs.edit()
            .putInt("last_index", player.currentMediaItemIndex)
            .putLong("last_pos", player.currentPosition)
        if (currentUri != null) {
            editor.putInt("idx_$currentUri", player.currentMediaItemIndex)
            editor.putLong("pos_$currentUri", player.currentPosition)
        }
        editor.apply()
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
