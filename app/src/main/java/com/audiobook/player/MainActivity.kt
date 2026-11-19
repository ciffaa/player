package com.audiobook.player

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.ImageView
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
    private lateinit var coverView: ImageView
    private lateinit var speedBtn: TextView
    private lateinit var playPauseBtn: TextView
    private lateinit var timerBtn: TextView
    private lateinit var chapterBtn: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var elapsedTimeView: TextView
    private lateinit var remainingTimeView: TextView

    private lateinit var libraryStatusView: TextView
    private lateinit var libraryListLayout: LinearLayout

    private val audioExt = listOf(".mp3", ".m4b", ".m4a", ".ogg", ".opus", ".wav", ".flac", ".aac", ".wma")
    private val speeds = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private var currentSpeedIndex = 0

    private val handler = Handler(Looper.getMainLooper())
    private var isSeeking = false
    private var lastArtworkHash = 0
    private var sleepTimerEndMs = 0L

    private val updateProgressTask = object : Runnable {
        override fun run() {
            checkSleepTimer()
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(20), dp(36), dp(20), dp(20))
        }

        // Верхние вкладки
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, dp(16))
        }

        tabPlayerBtn = Button(this).apply {
            text = "Плеер"
            setOnClickListener { showTab(isLibrary = false) }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, dp(4), 0)
            }
        }
        tabLibraryBtn = Button(this).apply {
            text = "Библиотека"
            setOnClickListener {
                showTab(isLibrary = true)
                scanLibrary()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, 0, 0)
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
            textSize = 18f
            setTextColor(Color.WHITE)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(20))
        }

        // Крупная квадратная обложка со скруглением
        val coverSize = dp(270)
        coverView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#1A1A1F"))
            setImageResource(android.R.drawable.ic_media_play)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
                }
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(coverSize, coverSize).apply {
                bottomMargin = dp(28)
            }
        }

        // Ряд управления: 1x | -15 | Play | +30 | Таймер
        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 5f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(24)
            }
        }

        fun makeTextAction(label: String, sizeSp: Float, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                textSize = sizeSp
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(12), dp(4), dp(12))
                setOnClickListener { onClick() }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        }

        speedBtn = makeTextAction("1x", 16f) { cycleSpeed() }
        val back15Btn = makeTextAction("↺ 15", 17f) {
            controller?.let {
                it.seekTo((it.currentPosition - 15_000).coerceAtLeast(0))
                saveCurrentProgress()
            }
        }

        val playContainer = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)
        }
        playPauseBtn = TextView(this).apply {
            text = "▶"
            textSize = 28f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E0E0E0"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(68), dp(68))
            setOnClickListener {
                controller?.let { if (it.isPlaying) it.pause() else it.play() }
            }
        }
        playContainer.addView(playPauseBtn)

        val fwd30Btn = makeTextAction("30 ↻", 17f) {
            controller?.let {
                val target = (it.currentPosition + 30_000).coerceAtMost(it.duration.coerceAtLeast(0))
                it.seekTo(target)
                saveCurrentProgress()
            }
        }
        timerBtn = makeTextAction("⏱", 20f) { showSleepTimerDialog() }

        controlsRow.addView(speedBtn)
        controlsRow.addView(back15Btn)
        controlsRow.addView(playContainer)
        controlsRow.addView(fwd30Btn)
        controlsRow.addView(timerBtn)

        // Кнопка списка глав
        chapterBtn = TextView(this).apply {
            text = "☰  Выберите книгу в Библиотеке"
            textSize = 15f
            setTextColor(Color.parseColor("#D0D0D8"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(16))
            setOnClickListener { showChaptersDialog() }
        }

        // Полоса перемотки
        seekBar = SeekBar(this).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val dur = controller?.duration?.coerceAtLeast(0) ?: 0L
                        val pos = progress.toLong()
                        elapsedTimeView.text = formatTime(pos)
                        remainingTimeView.text = "-${formatTime((dur - pos).coerceAtLeast(0))}"
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

        // Время слева и справа
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(4), dp(8), 0)
        }
        elapsedTimeView = TextView(this).apply {
            text = "00:00"
            textSize = 13f
            setTextColor(Color.parseColor("#888890"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        remainingTimeView = TextView(this).apply {
            text = "-00:00"
            textSize = 13f
            setTextColor(Color.parseColor("#888890"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        timeRow.addView(elapsedTimeView)
        timeRow.addView(remainingTimeView)

        playerContainer.addView(bookTitleView)
        playerContainer.addView(coverView)
        playerContainer.addView(controlsRow)
        playerContainer.addView(chapterBtn)
        playerContainer.addView(seekBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        playerContainer.addView(timeRow, LinearLayout.LayoutParams(
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
            setPadding(dp(4), dp(12), dp(4), dp(12))
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

    private fun cycleSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
        val speed = speeds[currentSpeedIndex]
        controller?.setPlaybackSpeed(speed)
        speedBtn.text = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"
    }

    private fun showSleepTimerDialog() {
        val options = arrayOf("Выключить", "15 минут", "30 минут", "45 минут", "60 минут")
        val minutes = intArrayOf(0, 15, 30, 45, 60)
        AlertDialog.Builder(this)
            .setTitle("Таймер сна")
            .setItems(options) { _, which ->
                val min = minutes[which]
                sleepTimerEndMs = if (min == 0) 0L else System.currentTimeMillis() + min * 60_000L
                updateTimerButton()
            }
            .show()
    }

    private fun checkSleepTimer() {
        if (sleepTimerEndMs > 0L) {
            if (System.currentTimeMillis() >= sleepTimerEndMs) {
                sleepTimerEndMs = 0L
                controller?.pause()
                saveCurrentProgress()
            }
            updateTimerButton()
        }
    }

    private fun updateTimerButton() {
        if (sleepTimerEndMs <= 0L) {
            timerBtn.text = "⏱"
            timerBtn.setTextColor(Color.WHITE)
        } else {
            val leftMin = ((sleepTimerEndMs - System.currentTimeMillis()) / 60_000L) + 1
            timerBtn.text = "${leftMin}м"
            timerBtn.setTextColor(Color.parseColor("#80D8FF"))
        }
    }

    private fun showChaptersDialog() {
        val player = controller ?: return
        val count = player.mediaItemCount
        if (count == 0) return

        val titles = Array(count) { i ->
            val title = player.getMediaItemAt(i).mediaMetadata.title?.toString() ?: "Глава ${i + 1}"
            if (i == player.currentMediaItemIndex) "▶ $title" else title
        }

        AlertDialog.Builder(this)
            .setTitle("Главы ($count)")
            .setItems(titles) { _, which ->
                player.seekTo(which, 0L)
                player.play()
                saveCurrentProgress()
                updateUI()
            }
            .show()
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
            libraryStatusView.text
