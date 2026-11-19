package com.audiobook.player

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
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

data class BookEntry(
    val title: String,
    val subtitle: String,
    val uriString: String
)

class MainActivity : Activity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private lateinit var tabPlayerBtn: Button
    private lateinit var tabLibraryBtn: Button
    private lateinit var playerContainer: LinearLayout
    private lateinit var libraryContainer: LinearLayout

    private lateinit var bookTitleView: TextView
    private lateinit var chapterTitleView: TextView
    private lateinit var timeView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playPauseBtn: Button

    private lateinit var libraryStatusView: TextView
    private lateinit var libraryListLayout: LinearLayout

    private val audioExt = listOf(".mp3", ".m4b", ".m4a", ".ogg", ".opus", ".wav", ".flac", ".aac", ".wma")
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
            setPadding(36, 72, 36, 36)
        }

        // Верхняя панель вкладок
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, 24)
        }

        tabPlayerBtn = Button(this).apply {
            text = "Плеер"
            setOnClickListener { showTab(isLibrary = false) }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
        }
        tabLibraryBtn = Button(this).apply {
            text = "Библиотека"
            setOnClickListener {
                showTab(isLibrary = true)
                scanLibrary()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
        }
        tabRow.addView(tabPlayerBtn)
        tabRow.addView(tabLibraryBtn)

        // --- ЭКРАН ПЛЕЕРА ---
        playerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        bookTitleView = TextView(this).apply {
            text = "Книга не выбрана"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 16)
        }

        chapterTitleView = TextView(this).apply {
            text = "Откройте «Библиотеку» и выберите папку Books"
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
                    sb?.let {
                        controller?.seekTo(it.progress.toLong())
                        saveCurrentProgress()
                    }
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
            weightSum = 5f
        }

        fun makeControlButton(label: String, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = label
                textSize = 14f
                minWidth = 0
                minimumWidth = 0
                setPadding(4, 24, 4, 24)
                setOnClickListener { onClick() }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(6, 0, 6, 0)
                }
            }
        }

        val prevBtn = makeControlButton("⏮") { controller?.seekToPreviousMediaItem() }
        val back15Btn = makeControlButton("-15с") {
            controller?.let {
                it.seekTo((it.currentPosition - 15_000).coerceAtLeast(0))
                saveCurrentProgress()
            }
        }
        playPauseBtn = makeControlButton("▶") {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        val fwd30Btn = makeControlButton("+30с") {
            controller?.let {
                val target = (it.currentPosition + 30_000).coerceAtMost(it.duration.coerceAtLeast(0))
                it.seekTo(target)
                saveCurrentProgress()
            }
        }
        val nextBtn = makeControlButton("⏭") { controller?.seekToNextMediaItem() }

        controlsRow.addView(prevBtn)
        controlsRow.addView(back15Btn)
        controlsRow.addView(playPauseBtn)
        controlsRow.addView(fwd30Btn)
        controlsRow.addView(nextBtn)

        playerContainer.addView(bookTitleView)
        playerContainer.addView(chapterTitleView)
        playerContainer.addView(seekBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        playerContainer.addView(timeView)
        playerContainer.addView(controlsRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // --- ЭКРАН БИБЛИОТЕКИ ---
        libraryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val pickLibraryBtn = Button(this).apply {
            text = "Выбрать общую папку Books"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, 200)
            }
        }

        libraryStatusView = TextView(this).apply {
            text = "Папка библиотеки не выбрана"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(4, 16, 4, 16)
        }

        val scrollView = ScrollView(this)
        libraryListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(libraryListLayout)

        libraryContainer.addView(pickLibraryBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        libraryContainer.addView(libraryStatusView)
        libraryContainer.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        root.addView(tabRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(playerContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        root.addView(libraryContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(root)
        showTab(isLibrary = false)
    }

    private fun showTab(isLibrary: Boolean) {
        playerContainer.visibility = if (isLibrary) View.GONE else View.VISIBLE
        libraryContainer.visibility = if (isLibrary) View.VISIBLE else View.GONE
        tabPlayerBtn.alpha = if (isLibrary) 0.5f else 1f
        tabLibraryBtn.alpha = if (isLibrary) 1f else 0.5f
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
        saveCurrentProgress()
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
            prefs.edit().putString("library_uri", treeUri.toString()).apply()
            scanLibrary()
        }
    }

    private fun scanLibrary() {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val libUriStr = prefs.getString("library_uri", null)
        if (libUriStr == null) {
            libraryStatusView.text = "Нажмите кнопку выше и выберите папку Books"
            return
        }

        libraryStatusView.text = "Сканирую библиотеку..."
        libraryListLayout.removeAllViews()

        Thread {
            val rootDir = DocumentFile.fromTreeUri(this, Uri.parse(libUriStr))
            val books = mutableListOf<BookEntry>()
            if (rootDir != null) {
                findBooks(rootDir, "", books)
            }
            books.sortBy { it.title.lowercase() }

            runOnUiThread {
                if (books.isEmpty()) {
                    libraryStatusView.text = "В выбранной папке не найдено аудиокниг"
                } else {
                    libraryStatusView.text = "Найдено книг: ${books.size}"
                    val activeUri = prefs.getString("folder_uri", null)
                    for (book in books) {
                        libraryListLayout.addView(createBookCard(book, book.uriString == activeUri))
                    }
                }
            }
        }.start()
    }

    private fun findBooks(dir: DocumentFile, parentPath: String, out: MutableList<BookEntry>) {
        val entries = dir.listFiles()
        val hasAudioHere = entries.any { file ->
            val name = file.name?.lowercase() ?: ""
            file.isFile && audioExt.any { name.endsWith(it) }
        }

        val currentName = dir.name ?: "Книга"
        if (hasAudioHere) {
            out.add(BookEntry(currentName, parentPath, dir.uri.toString()))
        }

        val nextPath = if (parentPath.isEmpty()) currentName else "$parentPath / $currentName"
        for (entry in entries) {
            if (entry.isDirectory) {
                findBooks(entry, nextPath, out)
            }
        }
    }

    private fun createBookCard(book: BookEntry, isActive: Boolean): View {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val savedIdx = prefs.getInt("idx_${book.uriString}", -1)
        val savedPos = prefs.getLong("pos_${book.uriString}", 0L)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor(if (isActive) "#2B3A4A" else "#1E1E24"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            setOnClickListener {
                switchToBook(book.uriString)
            }
        }

        val titleTv = TextView(this).apply {
            text = if (isActive) "▶ ${book.title}" else book.title
            textSize = 16f
            setTextColor(Color.WHITE)
        }

        val progressText = if (savedIdx >= 0) {
            "Глава ${savedIdx + 1} • ${formatTime(savedPos)}"
        } else {
            "Не начата"
        }

        val subTv = TextView(this).apply {
            text = if (book.subtitle.isNotEmpty()) "${book.subtitle}  |  $progressText" else progressText
            textSize = 12f
            setTextColor(Color.parseColor("#A0A0B0"))
            setPadding(0, 6, 0, 0)
        }

        card.addView(titleTv)
        card.addView(subTv)
        return card
    }

    private fun switchToBook(bookUriStr: String) {
        saveCurrentProgress()
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val savedIndex = prefs.getInt("idx_$bookUriStr", 0)
        val savedPos = prefs.getLong("pos_$bookUriStr", 0L)

        prefs.edit()
            .putString("folder_uri", bookUriStr)
            .putInt("last_index", savedIndex)
            .putLong("last_pos", savedPos)
            .apply()

        showTab(isLibrary = false)
        loadFolder(Uri.parse(bookUriStr), savedIndex, savedPos, autoPlay = true)
    }

    private fun saveCurrentProgress() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) return
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val currentUri = prefs.getString("folder_uri", null) ?: return
        prefs.edit()
            .putInt("last_index", player.currentMediaItemIndex)
            .putLong("last_pos", player.currentPosition)
            .putInt("idx_$currentUri", player.currentMediaItemIndex)
            .putLong("pos_$currentUri", player.currentPosition)
            .apply()
    }

    private fun restoreSavedBookIfEmpty() {
        val player = controller ?: return
        if (player.mediaItemCount > 0) return
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("folder_uri", null) ?: return
        val index = prefs.getInt("idx_$uriStr", prefs.getInt("last_index", 0))
        val pos = prefs.getLong("pos_$uriStr", prefs.getLong("last_pos", 0L))
        loadFolder(Uri.parse(uriStr), index, pos, autoPlay = false)
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

    private fun loadFolder(treeUri: Uri, startIndex: Int, startPos: Long, autoPlay: Boolean) {
        chapterTitleView.text = "Загружаю главы..."
        Thread {
            val dir = DocumentFile.fromTreeUri(this, treeUri) ?: return@Thread
            val bookName = dir.name ?: "Аудиокнига"
            val files = mutableListOf<DocumentFile>()
            collectAudioFiles(dir, files)

            runOnUiThread {
                bookTitleView.text = bookName
                if (files.isEmpty()) {
                    chapterTitleView.text = "В этой папке нет аудиофайлов"
                    return@runOnUiThread
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
        }.start()
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
