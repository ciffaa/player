package com.audiobook.player

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : Activity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private lateinit var bookTitleView: TextView
    private lateinit var chapterTitleView: TextView
    private lateinit var timeView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playPauseBtn: Button

    private val handler = Handler(Looper.getMainLooper())
    private var isSeeking = false

    private val updateProgressTask = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        buildUI()
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(48, 96, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val pickFolderBtn = Button(this).apply {
            text = "Выбрать папку с книгой"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, 200)
            }
        }

        bookTitleView = TextView(this).apply {
            text = "Книга не выбрана"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 64, 0, 16)
        }

        chapterTitleView = TextView(this).apply {
            text = "Выберите папку с аудиофайлами"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }

        seekBar = SeekBar(this).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val dur = controller?.duration?.coerceAtLeast(0) ?: 0L
                        timeView.text = "${formatTime(progress.toLong())} / ${formatTime(dur)}"
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) { isSeeking = true }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    isSeeking = false
                    sb?.let { controller?.seekTo(it.progress.toLong()) }
                }
            })
        }

        timeView = TextView(this).apply {
            text = "00:00 / 00:00"
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 48)
        }

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val prevBtn = Button(this).apply {
            text = "⏮"
            setOnClickListener { controller?.seekToPreviousMediaItem() }
        }
        val back15Btn = Button(this).apply {
            text = "-15с"
            setOnClickListener {
                controller?.let { it.seekTo((it.currentPosition - 15_000).coerceAtLeast(0)) }
            }
        }
        playPauseBtn = Button(this).apply {
            text = "▶"
            setOnClickListener {
                controller?.let { if (it.isPlaying) it.pause() else it.play() }
            }
        }
        val fwd30Btn = Button(this).apply {
            text = "+30с"
            setOnClickListener {
                controller?.let {
                    val target = (it.currentPosition + 30_000).coerceAtMost(it.duration.coerceAtLeast(0))
                    it.seekTo(target)
                }
            }
        }
        val nextBtn = Button(this).apply {
            text = "⏭"
            setOnClickListener { controller?.seekToNextMediaItem() }
        }

        controlsRow.addView(prevBtn)
        controlsRow.addView(back15Btn)
        controlsRow.addView(playPauseBtn)
        controlsRow.addView(fwd30Btn)
        controlsRow.addView(nextBtn)

        root.addView(pickFolderBtn)
        root.addView(bookTitleView)
        root.addView(chapterTitleView)
        root.addView(seekBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(timeView)
        root.addView(controlsRow)

        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    updateUI()
                }
            })
            restoreSavedBookIfEmpty()
            updateUI()
            handler.post(updateProgressTask)
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        handler.removeCallbacks(updateProgressTask)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200 && resultCode == RESULT_OK) {
            val treeUri = data?.data ?: return
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("folder_uri", treeUri.toString())
                .putInt("last_index", 0)
                .putLong("last_pos", 0L)
                .apply()
            loadFolder(treeUri, 0, 0L, autoPlay = true)
        }
    }

    private fun restoreSavedBookIfEmpty() {
        val player = controller ?: return
        if (player.mediaItemCount > 0) return
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("folder_uri", null) ?: return
        val index = prefs.getInt("last_index", 0)
        val pos = prefs.getLong("last_pos", 0L)
        loadFolder(Uri.parse(uriStr), index, pos, autoPlay = false)
    }

    private fun loadFolder(treeUri: Uri, startIndex: Int, startPos: Long, autoPlay: Boolean) {
        val dir = DocumentFile.fromTreeUri(this, treeUri) ?: return
        val bookName = dir.name ?: "Аудиокнига"
        val audioExt = listOf(".mp3", ".m4b", ".m4a", ".ogg", ".wav", ".flac", ".aac")

        val files = dir.listFiles()
            .filter { file ->
                val name = file.name?.lowercase() ?: ""
                file.isFile && audioExt.any { name.endsWith(it) }
            }
            .sortedBy { it.name?.lowercase() }

        if (files.isEmpty()) {
            chapterTitleView.text = "В этой папке нет аудиофайлов"
            return
        }

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
        controller?.apply {
            setMediaItems(items, safeIndex, startPos)
            prepare()
            if (autoPlay) play()
        }
        updateUI()
    }

    private fun updateUI() {
        val player = controller ?: return
        val meta = player.currentMediaItem?.mediaMetadata
        if (meta != null) {
            bookTitleView.text = meta.artist ?: "Аудиокнига"
            chapterTitleView.text = meta.title ?: "Глава"
        }
        playPauseBtn.text = if (player.isPlaying) "⏸" else "▶"

        val dur = player.duration.coerceAtLeast(0)
        val pos = player.currentPosition.coerceAtLeast(0)
        if (!isSeeking) {
            seekBar.max = dur.toInt()
            seekBar.progress = pos.toInt()
            timeView.text = "${formatTime(pos)} / ${formatTime(dur)}"
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }
}
