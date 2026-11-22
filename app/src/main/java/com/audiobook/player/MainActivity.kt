package com.audiobook.player

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import kotlin.math.sqrt

data class BookEntry(
    val title: String,
    val subtitle: String,
    val uriString: String
)

data class LibraryFilterItem(
    val id: String,
    val label: String,
    val isSmartInProgress: Boolean = false,
    val isSmartNotStarted: Boolean = false
)

data class AppTheme(
    val id: String,
    val name: String,
    val bg: Int,
    val surface: Int,
    val activeCard: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accentBg: Int,
    val accentText: Int,
    val navBg: Int,
    val navActive: Int,
    val navInactive: Int,
    val timerActive: Int
)

data class BadgeOption(
    val id: String,
    val drawableRes: Int
)

class MainActivity : Activity(), SensorEventListener {

    private val themes = listOf(
        AppTheme(
            id = "dark",
            name = "Тёмная",
            bg = Color.parseColor("#000000"),
            surface = Color.parseColor("#16161A"),
            activeCard = Color.parseColor("#263238"),
            textPrimary = Color.parseColor("#FFFFFF"),
            textSecondary = Color.parseColor("#9090A0"),
            accentBg = Color.parseColor("#E0E0E0"),
            accentText = Color.parseColor("#000000"),
            navBg = Color.parseColor("#121216"),
            navActive = Color.parseColor("#FFFFFF"),
            navInactive = Color.parseColor("#6E6E7A"),
            timerActive = Color.parseColor("#80D8FF")
        ),
        AppTheme(
            id = "light",
            name = "Светлая",
            bg = Color.parseColor("#F4F5F7"),
            surface = Color.parseColor("#FFFFFF"),
            activeCard = Color.parseColor("#E3F2FD"),
            textPrimary = Color.parseColor("#121216"),
            textSecondary = Color.parseColor("#666672"),
            accentBg = Color.parseColor("#1A1A20"),
            accentText = Color.parseColor("#FFFFFF"),
            navBg = Color.parseColor("#FFFFFF"),
            navActive = Color.parseColor("#121216"),
            navInactive = Color.parseColor("#8E8E98"),
            timerActive = Color.parseColor("#0277BD")
        ),
        AppTheme(
            id = "dark_green",
            name = "Тёмно-зелёная",
            bg = Color.parseColor("#0B1410"),
            surface = Color.parseColor("#13221B"),
            activeCard = Color.parseColor("#1E3A2C"),
            textPrimary = Color.parseColor("#E8F5E9"),
            textSecondary = Color.parseColor("#81A692"),
            accentBg = Color.parseColor("#66BB6A"),
            accentText = Color.parseColor("#0B1410"),
            navBg = Color.parseColor("#101D17"),
            navActive = Color.parseColor("#66BB6A"),
            navInactive = Color.parseColor("#5B7A69"),
            timerActive = Color.parseColor("#A5D6A7")
        )
    )

    private val badges = listOf(
        BadgeOption("v1", R.drawable.cat_logo),
        BadgeOption("v2", R.drawable.cat_curious)
    )

    private var currentTheme: AppTheme = themes[0]
    private var currentBadge: BadgeOption = badges[0]
    private var currentTab = 0

    private lateinit var bookmarksManager: BookmarksManager
    private lateinit var m4bChaptersReader: M4bChaptersReader
    private var activeM4bChapters: List<M4bChapter> = emptyList()
    private var lastLoadedChapterUri: String? = null

    private var bookDurationsUri: String? = null
    private var bookTrackDurationsMs: LongArray = LongArray(0)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private lateinit var rootLayout: LinearLayout
    private lateinit var contentContainer: LinearLayout
    private lateinit var bottomNavBar: LinearLayout
    private lateinit var navPlayerIcon: ImageView
    private lateinit var navLibraryIcon: ImageView
    private lateinit var navSettingsIcon: ImageView

    private lateinit var playerContainer: LinearLayout
    private lateinit var libraryContainer: LinearLayout
    private lateinit var settingsScrollView: ScrollView
    private lateinit var settingsContainer: LinearLayout

    private lateinit var topHeaderView: TextView
    private lateinit var coverGlowView: ImageView
    private lateinit var coverView: ImageView
    private lateinit var authorSubView: TextView
    private lateinit var bookTitleView: TextView
    private lateinit var speedBtn: TextView
    private lateinit var back15Btn: ImageView
    private lateinit var playPauseBtn: ImageView
    private lateinit var fwd30Btn: ImageView
    private lateinit var timerBtn: TextView
    private lateinit var chapterBtn: TextView
    private lateinit var bookmarkBtn: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var elapsedTimeView: TextView
    private lateinit var remainingTimeView: TextView
    private lateinit var bookProgressTextView: TextView

    private lateinit var searchBarContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var clearSearchBtn: TextView
    private lateinit var filterRowLayout: LinearLayout
    private lateinit var filterChipsScroll: HorizontalScrollView
    private lateinit var filterChipsRow: LinearLayout
    private lateinit var viewModeBtn: TextView
    private lateinit var editFiltersBtn: TextView
    private var isGridViewMode = false
    private var libraryFilters: MutableList<LibraryFilterItem> = mutableListOf()
    private var selectedFilterId: String = "all"
    private lateinit var libraryStatusView: TextView
    private lateinit var libraryListLayout: LinearLayout
    private var cachedBooks: List<BookEntry> = emptyList()
    private val bookCoverCache = mutableMapOf<String, Bitmap?>()
    private var libraryCoverFetchGeneration = 0

    private lateinit var settingsTitleView: TextView
    private lateinit var librarySectionLabel: TextView
    private lateinit var pickLibraryBtn: Button
    private lateinit var libraryFolderPathView: TextView
    private lateinit var themeSectionLabel: TextView
    private lateinit var themeButtonsContainer: LinearLayout
    private lateinit var badgeSectionLabel: TextView
    private lateinit var badgeButtonsContainer: LinearLayout
    private lateinit var rewindSectionLabel: TextView
    private lateinit var rewindCard: LinearLayout
    private lateinit var rewindTitle: TextView
    private lateinit var rewindStatus: TextView
    private lateinit var shakeCard: LinearLayout
    private lateinit var shakeTitle: TextView
    private lateinit var shakeStatus: TextView
    private lateinit var aiSectionLabel: TextView
    private lateinit var aiKeyCard: LinearLayout
    private lateinit var aiKeyTitle: TextView
    private lateinit var aiKeyStatus: TextView
    private lateinit var helpSectionLabel: TextView
    private lateinit var helpCard: LinearLayout
    private lateinit var helpCardTitle: TextView
    private lateinit var helpCardSubtitle: TextView

    private val audioExt = listOf(".mp3", ".m4b", ".m4a", ".ogg", ".opus", ".wav", ".flac", ".aac", ".wma")
    private val imageExt = listOf(".jpg", ".jpeg", ".png", ".webp")
    private val speeds = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private var currentSpeedIndex = 0

    private val handler = Handler(Looper.getMainLooper())
    private var isSeeking = false
    private var isLoadingFolder = false
    private var lastArtworkSize = -1
    private var lastCoverBookUri: String? = null
    private val attemptedCoverSearch = mutableSetOf<String>()
    private var detectedAuthorForCurrentBook: String = ""
    private var glowAnimator: ValueAnimator? = null

    private var sleepTimerEndMs = 0L
    private val sleepFadeDurationMs = 30_000L
    private var isFadingVolume = false

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isShakeRegistered = false
    private var lastShakeExtendAtMs = 0L

    private var wasPlayingPreviously = false
    private var pauseRecordedAtMs = 0L
    private var suppressNextRewindOnce = false

    private val updateProgressTask = object : Runnable {
        override fun run() {
            checkSleepTimer()
            updateProgressOnly()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookmarksManager = BookmarksManager(this)
        m4bChaptersReader = M4bChaptersReader(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val savedThemeId = prefs.getString("app_theme", "dark")
        currentTheme = themes.find { it.id == savedThemeId } ?: themes[0]

        val savedBadgeId = prefs.getString("app_badge", "v1")
        currentBadge = badges.find { it.id == savedBadgeId } ?: badges[0]
        isGridViewMode = prefs.getBoolean("library_grid_mode", false)

        loadLibraryFilters()
        buildUI()
        setupGlowAnimator()
        applyTheme()
        updateLibraryFolderLabel()

        val savedBookTitle = prefs.getString("last_book_title", null)
        val savedAuthor = prefs.getString("last_book_author", "") ?: ""
        val savedPos = prefs.getLong("last_pos", 0L)
        if (!savedBookTitle.isNullOrEmpty()) {
            detectedAuthorForCurrentBook = savedAuthor
            applyBookHeaderText(savedBookTitle, savedAuthor)
            elapsedTimeView.text = formatTime(savedPos)
        }

        showTab(0)
    }

    private fun setupGlowAnimator() {
        glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3400L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                if (!::coverGlowView.isInitialized) return@addUpdateListener
                val scale = 0.94f + 0.06f * t
                coverGlowView.scaleX = scale
                coverGlowView.scaleY = scale
                coverGlowView.alpha = 0.80f + 0.20f * t
            }
        }
    }

    private fun syncGlowAnimationState(isPlaying: Boolean) {
        val anim = glowAnimator ?: return
        val activePlayerScreen = currentTab == 0 && !isFinishing && !isDestroyed
        if (isPlaying && activePlayerScreen) {
            if (!anim.isStarted) {
                anim.start()
            } else if (anim.isPaused) {
                anim.resume()
            }
        } else {
            if (anim.isStarted && !anim.isPaused) {
                anim.pause()
            }
        }
    }

    private fun isCycleMetaFolder(name: String): Boolean {
        val lower = name.trim().lowercase()
        return lower == "цикл" ||
            lower == "серия" ||
            lower == "сборник" ||
            lower == "антология" ||
            lower.startsWith("цикл ") ||
            lower.startsWith("серия ")
    }

    private fun extractAuthorFromBookUri(bookUriStr: String?): String {
        if (bookUriStr.isNullOrEmpty()) return ""
        val decoded = Uri.decode(bookUriStr)
        val pathPart = decoded.substringAfterLast(':')
        val segments = pathPart.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        for (i in (segments.size - 2) downTo 0) {
            val candidate = segments[i]
            if (candidate.equals("Books", ignoreCase = true) ||
                candidate.equals("Audiobooks", ignoreCase = true) ||
                candidate.equals("Audio", ignoreCase = true) ||
                candidate.equals("Книги", ignoreCase = true) ||
                isCycleMetaFolder(candidate)
            ) {
                continue
            }
            return candidate
        }
        return ""
    }

    private fun applyBookHeaderText(rawTitle: String, explicitAuthor: String = "") {
        val clean = rawTitle.trim()
        if (clean.isEmpty() || clean == "Книга не выбрана") {
            topHeaderView.text = ""
            authorSubView.text = ""
            authorSubView.visibility = View.GONE
            bookTitleView.text = "Книга не выбрана"
            return
        }

        val parts = clean.split(Regex("\\s*[—–-]\\s*|\\s*/\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
        val resolvedAuthor: String
        val resolvedTitle: String

        if (parts.size >= 2) {
            resolvedAuthor = parts.first()
            resolvedTitle = parts.drop(1).joinToString(" — ")
        } else {
            resolvedAuthor = explicitAuthor.trim()
            resolvedTitle = clean
        }

        topHeaderView.text = resolvedTitle
        if (resolvedAuthor.isNotEmpty() && !resolvedAuthor.equals(resolvedTitle, ignoreCase = true)) {
            authorSubView.text = resolvedAuthor
            authorSubView.visibility = View.VISIBLE
            bookTitleView.text = resolvedTitle
        } else {
            authorSubView.text = ""
            authorSubView.visibility = View.GONE
            bookTitleView.text = resolvedTitle
        }
    }

    private fun getActiveBookLookupTitle(): String {
        val titleText = bookTitleView.text?.toString()?.trim() ?: ""
        val authorText = authorSubView.text?.toString()?.trim() ?: ""
        return when {
            titleText.isNotEmpty() && titleText != "Книга не выбрана" && titleText != "Аудиокнига" -> {
                if (authorText.isNotEmpty() && !titleText.contains(authorText, ignoreCase = true)) {
                    "$authorText - $titleText"
                } else {
                    titleText
                }
            }
            authorText.isNotEmpty() -> authorText
            else -> ""
        }
    }

    private fun updateLibraryFolderLabel() {
        if (!::libraryFolderPathView.isInitialized) return
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("library_uri", null)
        if (uriStr.isNullOrEmpty()) {
            libraryFolderPathView.text = "Папка не выбрана — нажмите кнопку выше"
        } else {
            val decoded = Uri.decode(uriStr).substringAfterLast(':').substringAfterLast('/')
            val label = decoded.ifEmpty { uriStr }
            libraryFolderPathView.text = "Текущая папка: $label"
        }
    }

    private fun isShakeToExtendEnabled(): Boolean {
        return getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
            .getBoolean("shake_timer_extend_enabled", true)
    }

    private fun syncShakeListener() {
        val mgr = sensorManager
        val acc = accelerometer
        if (mgr == null || acc == null) return

        val shouldListen = sleepTimerEndMs > 0L && isShakeToExtendEnabled() && !isFinishing && !isDestroyed
        if (shouldListen && !isShakeRegistered) {
            isShakeRegistered = mgr.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
        } else if (!shouldListen && isShakeRegistered) {
            mgr.unregisterListener(this)
            isShakeRegistered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        if (sleepTimerEndMs <= 0L || !isShakeToExtendEnabled()) return

        val now = System.currentTimeMillis()
        if (now - lastShakeExtendAtMs < 3_000L) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val gForce = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH

        if (gForce > 2.35) {
            lastShakeExtendAtMs = now
            val baseTime = maxOf(now, sleepTimerEndMs)
            sleepTimerEndMs = baseTime + 10 * 60_000L
            restorePlaybackVolumeIfNeeded()
            updateTimerButton()
            Toast.makeText(this, "Таймер продлён на 10 мин", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun restorePlaybackVolumeIfNeeded() {
        if (isFadingVolume) {
            controller?.volume = 1.0f
            isFadingVolume = false
        }
    }

    private fun defaultLibraryFilters(): MutableList<LibraryFilterItem> {
        return mutableListOf(
            LibraryFilterItem(id = "in_progress", label = "В процессе", isSmartInProgress = true),
            LibraryFilterItem(id = "not_started", label = "Не начаты", isSmartNotStarted = true),
            LibraryFilterItem(id = "planned", label = "Планирую"),
            LibraryFilterItem(id = "dropped", label = "Забросил")
        )
    }

    private fun loadLibraryFilters() {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("custom_library_filters_json", null)
        if (raw.isNullOrEmpty()) {
            libraryFilters = defaultLibraryFilters()
            return
        }
        try {
            val arr = JSONArray(raw)
            val parsed = mutableListOf<LibraryFilterItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                val label = obj.optString("label", "").trim()
                if (id.isNotEmpty() && label.isNotEmpty()) {
                    parsed.add(
                        LibraryFilterItem(
                            id = id,
                            label = label,
                            isSmartInProgress = obj.optBoolean("smartInProgress", false),
                            isSmartNotStarted = obj.optBoolean("smartNotStarted", false)
                        )
                    )
                }
            }
            libraryFilters = if (parsed.isNotEmpty()) parsed else defaultLibraryFilters()
        } catch (_: Exception) {
            libraryFilters = defaultLibraryFilters()
        }
        if (selectedFilterId != "all" && libraryFilters.none { it.id == selectedFilterId }) {
            selectedFilterId = "all"
        }
    }

    private fun saveLibraryFilters() {
        val arr = JSONArray()
        for (item in libraryFilters) {
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("label", item.label)
                    .put("smartInProgress", item.isSmartInProgress)
                    .put("smartNotStarted", item.isSmartNotStarted)
            )
        }
        getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("custom_library_filters_json", arr.toString())
            .apply()
    }

    private fun getBookAssignedStatus(bookUri: String): String? {
        val raw = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
            .getString("book_status_$bookUri", null)
            ?.trim()
        return if (raw.isNullOrEmpty()) null else raw
    }

    private fun setBookAssignedStatus(bookUri: String, filterId: String?) {
        val editor = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE).edit()
        if (filterId.isNullOrEmpty()) {
            editor.remove("book_status_$bookUri")
        } else {
            editor.putString("book_status_$bookUri", filterId)
        }
        editor.apply()
    }

    private fun showAssignBookStatusDialog(book: BookEntry) {
        val currentAssigned = getBookAssignedStatus(book.uriString)
        val selectable = libraryFilters.filter { !it.isSmartInProgress && !it.isSmartNotStarted }
        val options = mutableListOf<Pair<String, String?>>()
        for (f in selectable) {
            val mark = if (currentAssigned == f.id) "✓ " else ""
            options.add(Pair("$mark${f.label}", f.id))
        }
        options.add(Pair("Без статуса (по прогрессу)", null))

        val labels = options.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(book.title)
            .setItems(labels) { _, which ->
                val chosenId = options[which].second
                setBookAssignedStatus(book.uriString, chosenId)
                renderFilteredLibrary()
                val chosenLabel = selectable.firstOrNull { it.id == chosenId }?.label
                Toast.makeText(
                    this,
                    if (chosenLabel != null) "Статус: $chosenLabel" else "Статус сброшен",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun showManageFiltersDialog() {
        val itemsText = Array(libraryFilters.size) { i ->
            val f = libraryFilters[i]
            val kind = when {
                f.isSmartInProgress -> "авто: по прогрессу"
                f.isSmartNotStarted -> "авто: не начатые"
                else -> "ручной статус"
            }
            "${f.label} ($kind)"
        }

        AlertDialog.Builder(this)
            .setTitle("Настройка фильтров")
            .setItems(itemsText) { _, which ->
                showEditSingleFilterDialog(which)
            }
            .setPositiveButton("+ Добавить") { _, _ ->
                showAddFilterDialog()
            }
            .setNeutralButton("Сбросить") { _, _ ->
                libraryFilters = defaultLibraryFilters()
                selectedFilterId = "all"
                saveLibraryFilters()
                rebuildFilterChips()
                renderFilteredLibrary()
                Toast.makeText(this, "Фильтры сброшены", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showAddFilterDialog() {
        val input = EditText(this).apply {
            hint = "Например: Планирую или Забросил"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Новый фильтр")
            .setView(wrap)
            .setPositiveButton("Добавить") { _, _ ->
                val label = input.text?.toString()?.trim() ?: ""
                if (label.isEmpty()) {
                    Toast.makeText(this, "Введите название фильтра", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val id = "custom_${System.currentTimeMillis()}"
                libraryFilters.add(LibraryFilterItem(id = id, label = label))
                saveLibraryFilters()
                rebuildFilterChips()
                renderFilteredLibrary()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEditSingleFilterDialog(index: Int) {
        if (index !in libraryFilters.indices) return
        val item = libraryFilters[index]
        val input = EditText(this).apply {
            setText(item.label)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
            addView(input)
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Изменить фильтр")
            .setView(wrap)
            .setPositiveButton("Сохранить") { _, _ ->
                val newLabel = input.text?.toString()?.trim() ?: ""
                if (newLabel.isNotEmpty()) {
                    libraryFilters[index] = item.copy(label = newLabel)
                    saveLibraryFilters()
                    rebuildFilterChips()
                    renderFilteredLibrary()
                }
            }
            .setNegativeButton("Отмена", null)

        if (libraryFilters.size > 1) {
            builder.setNeutralButton("Удалить") { _, _ ->
                if (selectedFilterId == item.id) {
                    selectedFilterId = "all"
                }
                libraryFilters.removeAt(index)
                saveLibraryFilters()
                rebuildFilterChips()
                renderFilteredLibrary()
            }
        }
        builder.show()
    }

    private fun isSmartRewindEnabled(): Boolean {
        return getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
            .getBoolean("smart_rewind_enabled", true)
    }

    private fun computeSmartRewindMs(pauseDurationMs: Long): Long {
        if (!isSmartRewindEnabled() || pauseDurationMs < 60_000L) return 0L
        return when {
            pauseDurationMs < 10 * 60_000L -> 10_000L
            pauseDurationMs < 60 * 60_000L -> 25_000L
            else -> 45_000L
        }
    }

    private fun handlePlaybackStateTransition(player: Player) {
        val now = System.currentTimeMillis()
        val isPlayingNow = player.isPlaying

        if (wasPlayingPreviously && !isPlayingNow) {
            pauseRecordedAtMs = now
            saveCurrentProgress()
        } else if (!wasPlayingPreviously && isPlayingNow) {
            if (sleepTimerEndMs <= 0L) {
                restorePlaybackVolumeIfNeeded()
            }
            if (suppressNextRewindOnce) {
                suppressNextRewindOnce = false
                pauseRecordedAtMs = 0L
            } else {
                val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                val lastPauseFromPrefs = prefs.getLong("last_pause_timestamp_ms", 0L)
                val effectivePauseStart = if (pauseRecordedAtMs > 0L) pauseRecordedAtMs else lastPauseFromPrefs
                pauseRecordedAtMs = 0L
                prefs.edit().remove("last_pause_timestamp_ms").apply()

                if (effectivePauseStart > 0L && now > effectivePauseStart) {
                    val pauseDurationMs = now - effectivePauseStart
                    val rewindMs = computeSmartRewindMs(pauseDurationMs)
                    if (rewindMs > 0L) {
                        val curPos = player.currentPosition.coerceAtLeast(0L)
                        if (curPos > 1_500L) {
                            val targetPos = (curPos - rewindMs).coerceAtLeast(0L)
                            suppressNextRewindOnce = true
                            player.seekTo(targetPos)
                            saveCurrentProgress()
                            val sec = (rewindMs / 1000L).toInt()
                            Toast.makeText(this, "Откат на ${sec}с после паузы", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        wasPlayingPreviously = isPlayingNow
    }

    private fun applyLauncherIcon(badgeId: String) {
        try {
            val aliasV1 = ComponentName(this, "$packageName.MainActivityAliasV1")
            val aliasV2 = ComponentName(this, "$packageName.MainActivityAliasV2")

            val useV2 = badgeId == "v2"
            packageManager.setComponentEnabledSetting(
                aliasV1,
                if (!useV2) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            packageManager.setComponentEnabledSetting(
                aliasV2,
                if (useV2) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUI() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        playerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        topHeaderView = TextView(this).apply {
            text = ""
            textSize = 15.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(2), dp(12), dp(6))
            setOnClickListener {
                val query = getActiveBookLookupTitle()
                if (query.isNotEmpty()) {
                    showBookInfoDialog(query, forceRefresh = false)
                }
            }
        }

        val coverWrapper = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val glowSize = dp(440)
        coverGlowView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = FrameLayout.LayoutParams(glowSize, glowSize, Gravity.CENTER)
        }

        val coverSize = dp(304)
        coverView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(android.R.drawable.ic_media_play)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(20).toFloat())
                }
            }
            clipToOutline = true
            layoutParams = FrameLayout.LayoutParams(coverSize, coverSize, Gravity.CENTER)
            setOnClickListener { showCoverMenu() }
        }

        coverWrapper.addView(coverGlowView)
        coverWrapper.addView(coverView)

        val metaBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(6), dp(16), dp(4))
        }

        authorSubView = TextView(this).apply {
            text = ""
            textSize = 13.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.02f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }

        bookTitleView = TextView(this).apply {
            text = "Книга не выбрана"
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.03f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }

        metaBlock.addView(authorSubView)
        metaBlock.addView(bookTitleView)

        val chapterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(6)
            }
        }

        val leftBalanceSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(1))
        }

        chapterBtn = TextView(this).apply {
            text = "☰  Выберите книгу в Библиотеке"
            textSize = 13.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showChaptersDialog() }
        }

        bookmarkBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_bookmark_minimal)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(9), dp(9), dp(9), dp(9))
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener { openBookmarksList() }
            setOnLongClickListener {
                openAddBookmarkNow()
                true
            }
        }

        chapterRow.addView(leftBalanceSpacer)
        chapterRow.addView(chapterBtn)
        chapterRow.addView(bookmarkBtn)

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 5f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        fun makeTextAction(label: String, sizeSp: Float, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                textSize = sizeSp
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(12), dp(4), dp(12))
                setOnClickListener { onClick() }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        }

        speedBtn = makeTextAction("1x", 15.5f) { cycleSpeed() }

        val backWrap = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                controller?.let {
                    suppressNextRewindOnce = true
                    it.seekTo((it.currentPosition - 15_000).coerceAtLeast(0))
                    saveCurrentProgress()
                }
            }
        }
        back15Btn = ImageView(this).apply {
            setImageResource(R.drawable.ic_rewind_15)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        backWrap.addView(back15Btn)

        val playContainer = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)
        }
        playPauseBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_play)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(dp(68), dp(68))
            setOnClickListener {
                controller?.let {
                    if (it.isPlaying) {
                        it.pause()
                    } else {
                        it.play()
                    }
                }
            }
        }
        playContainer.addView(playPauseBtn)

        val fwdWrap = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                controller?.let {
                    suppressNextRewindOnce = true
                    val target = (it.currentPosition + 30_000).coerceAtMost(it.duration.coerceAtLeast(0))
                    it.seekTo(target)
                    saveCurrentProgress()
                }
            }
        }
        fwd30Btn = ImageView(this).apply {
            setImageResource(R.drawable.ic_forward_30)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        fwdWrap.addView(fwd30Btn)

        timerBtn = makeTextAction("⏱", 19f) { showSleepTimerDialog() }

        controlsRow.addView(speedBtn)
        controlsRow.addView(backWrap)
        controlsRow.addView(playContainer)
        controlsRow.addView(fwdWrap)
        controlsRow.addView(timerBtn)

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
                        suppressNextRewindOnce = true
                        controller?.seekTo(it.progress.toLong())
                        saveCurrentProgress()
                    }
                }
            })
        }

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(4), dp(8), dp(6))
        }
        elapsedTimeView = TextView(this).apply {
            text = "00:00"
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        remainingTimeView = TextView(this).apply {
            text = "-00:00"
            textSize = 13f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        timeRow.addView(elapsedTimeView)
        timeRow.addView(remainingTimeView)

        bookProgressTextView = TextView(this).apply {
            text = ""
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(6))
        }

        playerContainer.addView(topHeaderView)
        playerContainer.addView(coverWrapper)
        playerContainer.addView(metaBlock)
        playerContainer.addView(chapterRow)
        playerContainer.addView(controlsRow)
        playerContainer.addView(seekBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        playerContainer.addView(timeRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        playerContainer.addView(bookProgressTextView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        libraryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        searchInput = EditText(this).apply {
            hint = "Поиск по книге, автору или папке..."
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true)
            background = null
            setPadding(0, dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    clearSearchBtn.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                    renderFilteredLibrary()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        clearSearchBtn = TextView(this).apply {
            text = "✕"
            textSize = 16f
            visibility = View.GONE
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(4), dp(4))
            setOnClickListener {
                searchInput.text.clear()
            }
        }

        searchBarContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(searchInput)
            addView(clearSearchBtn)
        }

        filterChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        filterChipsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            addView(filterChipsRow)
        }

        viewModeBtn = TextView(this).apply {
            text = if (isGridViewMode) "☰" else "⊞"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(7))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                isGridViewMode = !isGridViewMode
                getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("library_grid_mode", isGridViewMode)
                    .apply()
                viewModeBtn.text = if (isGridViewMode) "☰" else "⊞"
                renderFilteredLibrary()
            }
        }

        editFiltersBtn = TextView(this).apply {
            text = "✎"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setOnClickListener { showManageFiltersDialog() }
        }

        filterRowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
            addView(filterChipsScroll)
            addView(viewModeBtn)
            addView(editFiltersBtn)
        }

        rebuildFilterChips()

        libraryStatusView = TextView(this).apply {
            text = "Папка библиотеки не выбрана"
            textSize = 14f
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }

        val scrollView = ScrollView(this)
        libraryListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(libraryListLayout)

        libraryContainer.addView(searchBarContainer)
        libraryContainer.addView(filterRowLayout)
        libraryContainer.addView(libraryStatusView)
        libraryContainer.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        settingsScrollView = ScrollView(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        settingsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        settingsTitleView = TextView(this).apply {
            text = "Настройки"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(8), dp(4), dp(20))
        }

        librarySectionLabel = TextView(this).apply {
            text = "Библиотека"
            textSize = 14f
            setPadding(dp(4), 0, dp(4), dp(10))
        }

        pickLibraryBtn = Button(this).apply {
            text = "Выбрать общую папку Books"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, 200)
            }
        }

        libraryFolderPathView = TextView(this).apply {
            textSize = 12f
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }

        themeSectionLabel = TextView(this).apply {
            text = "Тема оформления"
            textSize = 14f
            setPadding(dp(4), dp(14), dp(4), dp(10))
        }

        themeButtonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        badgeSectionLabel = TextView(this).apply {
            text = "Значок"
            textSize = 14f
            setPadding(dp(4), dp(16), dp(4), dp(10))
        }

        badgeButtonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        rewindSectionLabel = TextView(this).apply {
            text = "Воспроизведение"
            textSize = 14f
            setPadding(dp(4), dp(20), dp(4), dp(10))
        }

        rewindTitle = TextView(this).apply {
            text = "Умный автооткат после паузы"
            textSize = 16f
        }

        rewindStatus = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(4), 0, 0)
        }

        rewindCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
            setOnClickListener {
                val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                val nextVal = !isSmartRewindEnabled()
                prefs.edit().putBoolean("smart_rewind_enabled", nextVal).apply()
                updateRewindCard()
                Toast.makeText(
                    this@MainActivity,
                    if (nextVal) "Умный автооткат включён" else "Умный автооткат выключен",
                    Toast.LENGTH_SHORT
                ).show()
            }
            addView(rewindTitle)
            addView(rewindStatus)
        }

        shakeTitle = TextView(this).apply {
            text = "Продление таймера сна встряхиванием"
            textSize = 16f
        }

        shakeStatus = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(4), 0, 0)
        }

        shakeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
            setOnClickListener {
                val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                val nextVal = !isShakeToExtendEnabled()
                prefs.edit().putBoolean("shake_timer_extend_enabled", nextVal).apply()
                updateShakeCard()
                syncShakeListener()
                Toast.makeText(
                    this@MainActivity,
                    if (nextVal) "Продление встряхиванием включено" else "Продление встряхиванием выключено",
                    Toast.LENGTH_SHORT
                ).show()
            }
            addView(shakeTitle)
            addView(shakeStatus)
        }

        aiSectionLabel = TextView(this).apply {
            text = "ИИ-поиск описаний (Gemini API)"
            textSize = 14f
            setPadding(dp(4), dp(16), dp(4), dp(10))
        }

        aiKeyTitle = TextView(this).apply {
            text = "API-ключ Gemini"
            textSize = 16f
        }

        aiKeyStatus = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(4), 0, 0)
        }

        aiKeyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
            setOnClickListener { showApiKeyDialog() }
            addView(aiKeyTitle)
            addView(aiKeyStatus)
        }

        helpSectionLabel = TextView(this).apply {
            text = "Справка и подсказки"
            textSize = 14f
            setPadding(dp(4), dp(12), dp(4), dp(10))
        }

        helpCardTitle = TextView(this).apply {
            text = "Как пользоваться плеером"
            textSize = 16f
        }

        helpCardSubtitle = TextView(this).apply {
            text = "Закладки, обложки, описание книги и таймер сна"
            textSize = 13f
            setPadding(0, dp(4), 0, 0)
        }

        helpCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(24)
            }
            setOnClickListener { showHelpDialog() }
            addView(helpCardTitle)
            addView(helpCardSubtitle)
        }

        settingsContainer.addView(settingsTitleView)
        settingsContainer.addView(librarySectionLabel)
        settingsContainer.addView(pickLibraryBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        settingsContainer.addView(libraryFolderPathView)
        settingsContainer.addView(themeSectionLabel)
        settingsContainer.addView(themeButtonsContainer)
        settingsContainer.addView(badgeSectionLabel)
        settingsContainer.addView(badgeButtonsContainer)
        settingsContainer.addView(rewindSectionLabel)
        settingsContainer.addView(rewindCard)
        settingsContainer.addView(shakeCard)
        settingsContainer.addView(aiSectionLabel)
        settingsContainer.addView(aiKeyCard)
        settingsContainer.addView(helpSectionLabel)
        settingsContainer.addView(helpCard)
        settingsScrollView.addView(settingsContainer)

        contentContainer.addView(playerContainer)
        contentContainer.addView(libraryContainer)
        contentContainer.addView(settingsScrollView)

        bottomNavBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 3f
            setPadding(dp(12), dp(12), dp(12), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun makeNavIcon(resId: Int, tabIndex: Int): Pair<LinearLayout, ImageView> {
            val icon = ImageView(this).apply {
                setImageResource(resId)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            }
            val wrapper = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { showTab(tabIndex) }
                addView(icon)
            }
            return Pair(wrapper, icon)
        }

        val (libWrap, libIcon) = makeNavIcon(R.drawable.ic_nav_library, 1)
        val (playerWrap, playerIcon) = makeNavIcon(R.drawable.ic_nav_player, 0)
        val (settingsWrap, settingsIcon) = makeNavIcon(R.drawable.ic_nav_settings, 2)

        navLibraryIcon = libIcon
        navPlayerIcon = playerIcon
        navSettingsIcon = settingsIcon

        bottomNavBar.addView(libWrap)
        bottomNavBar.addView(playerWrap)
        bottomNavBar.addView(settingsWrap)

        rootLayout.addView(contentContainer)
        rootLayout.addView(bottomNavBar)

        setContentView(rootLayout)
    }

    private fun buildSoftGlowBitmap(baseColor: Int): Bitmap {
        val size = 400
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = size * 0.5f
        val cy = size * 0.5f
        val radius = size * 0.5f

        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)

        val inner = Color.argb(238, r, g, b)
        val mid = Color.argb(155, r, g, b)
        val outer = Color.argb(62, r, g, b)
        val edge = Color.argb(0, r, g, b)

        val shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(inner, mid, outer, edge),
            floatArrayOf(0.0f, 0.46f, 0.70f, 0.86f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
        }
        canvas.drawCircle(cx, cy, radius, paint)
        return bmp
    }

    private fun updateCoverGlowColor(bmp: Bitmap?) {
        val t = currentTheme
        val glowSourceColor = when {
            bmp != null -> {
                val scaled = Bitmap.createScaledBitmap(bmp, 24, 24, true)
                var sumR = 0
                var sumG = 0
                var sumB = 0
                var count = 0
                for (y in 3 until 21) {
                    for (x in 3 until 21) {
                        val px = scaled.getPixel(x, y)
                        sumR += Color.red(px)
                        sumG += Color.green(px)
                        sumB += Color.blue(px)
                        count++
                    }
                }
                if (scaled != bmp) scaled.recycle()
                val avgR = (sumR / count.coerceAtLeast(1))
                val avgG = (sumG / count.coerceAtLeast(1))
                val avgB = (sumB / count.coerceAtLeast(1))
                when (t.id) {
                    "dark_green" -> Color.rgb(
                        minOf(255, (avgR + 120) / 2 + 12),
                        minOf(255, (avgG + 210) / 2 + 16),
                        minOf(255, (avgB + 125) / 2 + 12)
                    )
                    "dark" -> Color.rgb(
                        minOf(255, (avgR * 1.25f).toInt() + 28),
                        minOf(255, (avgG * 1.25f).toInt() + 28),
                        minOf(255, (avgB * 1.25f).toInt() + 28)
                    )
                    "light" -> Color.rgb(
                        maxOf(0, (avgR * 0.72f).toInt()),
                        maxOf(0, (avgG * 0.72f).toInt()),
                        maxOf(0, (avgB * 0.85f).toInt())
                    )
                    else -> Color.rgb(avgR, avgG, avgB)
                }
            }
            t.id == "dark_green" -> Color.parseColor("#76D275")
            t.id == "dark" -> Color.parseColor("#82B1FF")
            t.id == "light" -> Color.parseColor("#1976D2")
            else -> t.accentBg
        }

        val prevDrawable = coverGlowView.drawable as? BitmapDrawable
        coverGlowView.setImageBitmap(buildSoftGlowBitmap(glowSourceColor))
        prevDrawable?.bitmap?.recycle()
    }

    private fun setCoverBitmap(bmp: Bitmap?) {
        if (bmp != null) {
            coverView.setImageBitmap(bmp)
            updateCoverGlowColor(bmp)
        } else {
            coverView.setImageResource(android.R.drawable.ic_media_play)
            updateCoverGlowColor(null)
        }
    }

    private fun rebuildFilterChips() {
        if (!::filterChipsRow.isInitialized) return
        filterChipsRow.removeAllViews()
        val t = currentTheme

        val allItems = mutableListOf(LibraryFilterItem(id = "all", label = "Все"))
        allItems.addAll(libraryFilters)

        for (i in allItems.indices) {
            val item = allItems[i]
            val selected = selectedFilterId == item.id
            val chip = TextView(this).apply {
                text = item.label
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(dp(11), dp(7), dp(11), dp(7))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(if (selected) t.activeCard else t.surface)
                }
                setTextColor(if (selected) t.textPrimary else t.textSecondary)
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (i < allItems.lastIndex) marginEnd = dp(8)
                }
                setOnClickListener {
                    selectedFilterId = item.id
                    rebuildFilterChips()
                    renderFilteredLibrary()
                }
            }
            filterChipsRow.addView(chip)
        }

        if (::viewModeBtn.isInitialized) {
            viewModeBtn.text = if (isGridViewMode) "☰" else "⊞"
            viewModeBtn.background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(if (isGridViewMode) t.activeCard else t.surface)
            }
            viewModeBtn.setTextColor(if (isGridViewMode) t.textPrimary else t.textSecondary)
        }

        if (::editFiltersBtn.isInitialized) {
            editFiltersBtn.background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(t.surface)
            }
            editFiltersBtn.setTextColor(t.textPrimary)
        }
    }

    private fun updateRewindCard() {
        val enabled = isSmartRewindEnabled()
        val t = currentTheme
        rewindCard.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (enabled) t.activeCard else t.surface)
        }
        rewindTitle.setTextColor(t.textPrimary)
        rewindStatus.setTextColor(t.textSecondary)
        rewindStatus.text = if (enabled) {
            "Включён: откат 10–45 сек после паузы от 1 мин"
        } else {
            "Выключен — нажмите, чтобы включить"
        }
    }

    private fun updateShakeCard() {
        val enabled = isShakeToExtendEnabled()
        val t = currentTheme
        shakeCard.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (enabled) t.activeCard else t.surface)
        }
        shakeTitle.setTextColor(t.textPrimary)
        shakeStatus.setTextColor(t.textSecondary)
        shakeStatus.text = if (enabled) {
            "Включено: встряхивание добавляет +10 мин при активном таймере"
        } else {
            "Выключено — нажмите, чтобы включить"
        }
    }

    private fun computeBookDurationsAsync(bookUriStr: String, files: List<DocumentFile>) {
        if (files.isEmpty()) return
        bookDurationsUri = bookUriStr
        bookTrackDurationsMs = LongArray(files.size) { 0L }

        Thread {
            val durations = LongArray(files.size) { 0L }
            val retriever = MediaMetadataRetriever()
            try {
                for (i in files.indices) {
                    if (bookDurationsUri != bookUriStr) return@Thread
                    try {
                        retriever.setDataSource(this, files[i].uri)
                        val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val d = durStr?.toLongOrNull() ?: 0L
                        if (d > 0L) durations[i] = d
                    } catch (_: Exception) {
                    }
                }
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }

            runOnUiThread {
                if (!isFinishing && !isDestroyed && bookDurationsUri == bookUriStr) {
                    bookTrackDurationsMs = durations
                    updateBookProgressUI()
                }
            }
        }.start()
    }

    private fun getEffectiveTrackDurationMs(index: Int, player: Player): Long {
        if (index == player.currentMediaItemIndex && player.duration > 0L) {
            return player.duration
        }
        return if (index in bookTrackDurationsMs.indices) bookTrackDurationsMs[index] else 0L
    }

    private fun computeTotalBookDurationMs(): Long {
        val player = controller ?: return 0L
        val count = player.mediaItemCount
        if (count <= 0) return 0L
        var sum = 0L
        for (i in 0 until count) {
            sum += getEffectiveTrackDurationMs(i, player)
        }
        return sum
    }

    private fun computeElapsedBookMs(player: Player): Long {
        val count = player.mediaItemCount
        if (count <= 0) return 0L
        val curIdx = player.currentMediaItemIndex.coerceIn(0, count - 1)
        var elapsed = 0L
        for (i in 0 until curIdx) {
            elapsed += getEffectiveTrackDurationMs(i, player)
        }
        elapsed += player.currentPosition.coerceAtLeast(0L)
        return elapsed
    }

    private fun updateBookProgressUI() {
        val player = controller
        if (player == null || player.mediaItemCount <= 0) {
            bookProgressTextView.text = ""
            return
        }

        val totalMs = computeTotalBookDurationMs()
        val elapsedMs = computeElapsedBookMs(player)
        if (totalMs <= 0L) {
            bookProgressTextView.text = ""
            return
        }

        val ratio = (elapsedMs.toDouble() / totalMs.toDouble()).coerceIn(0.0, 1.0)
        val remMs = (totalMs - elapsedMs).coerceAtLeast(0L)
        val pct = (ratio * 100.0).toInt().coerceIn(0, 100)

        bookProgressTextView.text = "Книга: $pct% • Осталось ${formatTime(remMs)}"
    }

    private fun refreshM4bChaptersIfNeeded(player: Player) {
        if (player.mediaItemCount != 1) {
            activeM4bChapters = emptyList()
            lastLoadedChapterUri = null
            return
        }
        val item = player.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        val uriStr = uri.toString()
        if (uriStr == lastLoadedChapterUri) return
        lastLoadedChapterUri = uriStr

        val lowerPath = uriStr.lowercase()
        if (!lowerPath.contains(".m4b") && !lowerPath.contains(".m4a")) {
            activeM4bChapters = emptyList()
            return
        }

        Thread {
            val found = m4bChaptersReader.readChapters(uri)
            runOnUiThread {
                if (!isFinishing && !isDestroyed && uriStr == lastLoadedChapterUri) {
                    activeM4bChapters = found
                    updateUI()
                }
            }
        }.start()
    }

    private fun currentM4bChapter(posMs: Long): M4bChapter? {
        if (activeM4bChapters.isEmpty()) return null
        var current = activeM4bChapters.first()
        for (ch in activeM4bChapters) {
            if (posMs >= ch.startMs) {
                current = ch
            } else {
                break
            }
        }
        return current
    }

    private fun showHelpDialog() {
        val guideText = """
            Закладки:
            • Обычное нажатие на иконку закладки справа от главы — открывает список закладок текущей книги. Внутри есть кнопка «+ Добавить закладку». Нажатие по закладке перематывает на нужный момент, долгое нажатие — удаляет её.
            • Долгое нажатие на закладку — сразу открывает окно создания закладки на текущем таймкоде с полем для заметки.

            Главы и M4B:
            • Нажатие на название главы открывает список глав. Для книг одним файлом .m4b автоматически считываются встроенные главы по таймкодам.

            Обложки и Библиотека:
            • Родная обложка (из папки книги или тегов аудиофайла) всегда имеет приоритет; поиск в интернете запускается только если родной обложки нет.
            • Переключатель «⊞ / ☰» в Библиотеке меняет вид между сеткой блоков с обложками и полупрозрачным прогрессом и обычным списком.
            • Долгое нажатие на книгу задаёт статус («Планирую», «Забросил» и др.), а выбор общей папки Books перенесён в Настройки.

            Описание и пересказ сюжета:
            • Нажмите на верхнюю строку названия книги, чтобы посмотреть аннотацию. Внутри есть кнопка «Освежить память» — Gemini кратко напомнит события до текущей главы без спойлеров.

            Воспроизведение:
            • 1x — переключение скорости (до 2x).
            • 15 и 30 — перемотка назад на 15 сек и вперёд на 30 сек.
            • ⏱ — таймер сна с плавным затуханием звука за 30 сек до конца и продлением встряхиванием (+10 мин).
            • Умный автооткат после паузы — мягко сдвигает позицию назад при возвращении после паузы от 1 минуты.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Как пользоваться плеером")
            .setMessage(guideText)
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun openBookmarksList() {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val bookUri = prefs.getString("folder_uri", null)
        if (bookUri.isNullOrEmpty()) {
            Toast.makeText(this, "Сначала выберите книгу", Toast.LENGTH_SHORT).show()
            return
        }
        val player = controller
        val chapIdx = player?.currentMediaItemIndex ?: prefs.getInt("last_index", 0)
        val posMs = player?.currentPosition?.coerceAtLeast(0L) ?: prefs.getLong("last_pos", 0L)
        val m4bCh = currentM4bChapter(posMs)
        val chapTitle = m4bCh?.title
            ?: player?.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: "Глава ${chapIdx + 1}"
        val bookTitle = getActiveBookLookupTitle().ifEmpty { "Аудиокнига" }

        bookmarksManager.showBookmarksDialog(
            bookUri = bookUri,
            bookTitle = bookTitle,
            currentChapterIndex = chapIdx,
            currentChapterTitle = chapTitle,
            currentPositionMs = posMs,
            theme = currentTheme,
            onJumpTo = { targetChapter: Int, targetPos: Long ->
                player?.let { p ->
                    suppressNextRewindOnce = true
                    val safeChap = targetChapter.coerceIn(0, (p.mediaItemCount - 1).coerceAtLeast(0))
                    p.seekTo(safeChap, targetPos.coerceAtLeast(0L))
                    p.play()
                    saveCurrentProgress()
                    updateUI()
                }
            }
        )
    }

    private fun openAddBookmarkNow() {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val bookUri = prefs.getString("folder_uri", null)
        if (bookUri.isNullOrEmpty()) {
            Toast.makeText(this, "Сначала выберите книгу", Toast.LENGTH_SHORT).show()
            return
        }
        val player = controller
        val chapIdx = player?.currentMediaItemIndex ?: prefs.getInt("last_index", 0)
        val posMs = player?.currentPosition?.coerceAtLeast(0L) ?: prefs.getLong("last_pos", 0L)
        val m4bCh = currentM4bChapter(posMs)
        val chapTitle = m4bCh?.title
            ?: player?.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: "Глава ${chapIdx + 1}"

        bookmarksManager.showAddBookmarkDialog(
            bookUri = bookUri,
            chapterIndex = chapIdx,
            chapterTitle = chapTitle,
            positionMs = posMs,
            theme = currentTheme
        )
    }

    private fun getCoverFile(bookUriStr: String, custom: Boolean): File {
        val dir = File(filesDir, "covers").apply { if (!exists()) mkdirs() }
        val prefix = if (custom) "custom_v5_" else "auto_v5_"
        return File(dir, "$prefix${bookUriStr.hashCode()}.jpg")
    }

    private fun extractEmbeddedCoverFromBook(treeUri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            val dir = DocumentFile.fromTreeUri(this, treeUri) ?: return null
            val files = mutableListOf<DocumentFile>()
            collectAudioFiles(dir, files)
            for (f in files.take(3)) {
                try {
                    retriever.setDataSource(this, f.uri)
                    val pic = retriever.embeddedPicture
                    if (pic != null && pic.isNotEmpty()) {
                        val bmp = BitmapFactory.decodeByteArray(pic, 0, pic.size)
                        if (bmp != null) return bmp
                    }
                } catch (_: Exception) {
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun findNativeBookCover(bookUriStr: String): Bitmap? {
        val treeUri = Uri.parse(bookUriStr)
        return findCoverInFolder(treeUri) ?: extractEmbeddedCoverFromBook(treeUri)
    }

    private fun loadCachedBookCoverBitmap(bookUriStr: String): Bitmap? {
        bookCoverCache[bookUriStr]?.let { return it }

        val customFile = getCoverFile(bookUriStr, custom = true)
        if (customFile.exists()) {
            val bmp = BitmapFactory.decodeFile(customFile.absolutePath)
            if (bmp != null) {
                bookCoverCache[bookUriStr] = bmp
                return bmp
            }
        }

        val nativeBmp = findNativeBookCover(bookUriStr)
        if (nativeBmp != null) {
            val nativeFile = getCoverFile(bookUriStr, custom = false)
            saveBitmapToFile(nativeBmp, nativeFile, 600)
            val savedBmp = BitmapFactory.decodeFile(nativeFile.absolutePath) ?: nativeBmp
            bookCoverCache[bookUriStr] = savedBmp
            return savedBmp
        }

        val autoFile = getCoverFile(bookUriStr, custom = false)
        if (autoFile.exists()) {
            val bmp = BitmapFactory.decodeFile(autoFile.absolutePath)
            if (bmp != null) {
                bookCoverCache[bookUriStr] = bmp
                return bmp
            }
        }
        return null
    }

    private fun stripSeriesMetaWords(raw: String): String {
        return raw
            .replace(Regex("\\b(цикл|серия|сборник|антология)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\b(том|книга|часть|выпуск|ч\\.|т\\.|кн\\.)\\s*\\d+\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildCoverSearchQueries(bookTitle: String, bookUriStr: String? = null): List<String> {
        val authorFromPath = extractAuthorFromBookUri(bookUriStr)
        val parts = bookTitle.split(Regex("\\s*[—–-]\\s*|\\s*/\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
        val specificBookPart = if (parts.size >= 2 && !isCycleMetaFolder(parts.last())) {
            parts.last()
        } else {
            bookTitle
        }

        val cleanBookOnly = stripSeriesMetaWords(normalizeQuery(specificBookPart))
        val cleanFull = stripSeriesMetaWords(normalizeQuery(bookTitle))
        val cleanAuthor = stripSeriesMetaWords(normalizeQuery(authorFromPath))
        val rawFallback = normalizeQuery(bookTitle)

        val list = mutableListOf<String>()
        if (cleanBookOnly.isNotEmpty() && cleanAuthor.isNotEmpty() && !cleanBookOnly.contains(cleanAuthor, ignoreCase = true)) {
            list.add("$cleanBookOnly $cleanAuthor".trim())
        }
        if (cleanBookOnly.isNotEmpty() && !list.contains(cleanBookOnly)) {
            list.add(cleanBookOnly)
        }
        if (cleanFull.isNotEmpty() && !list.contains(cleanFull)) {
            list.add(cleanFull)
        }
        if (rawFallback.isNotEmpty() && !list.contains(rawFallback)) {
            list.add(rawFallback)
        }
        return list
    }

    private fun fetchCoverBitmapForBook(bookTitle: String, bookUriStr: String? = null): Bitmap? {
        val queries = buildCoverSearchQueries(bookTitle, bookUriStr)
        for (q in queries) {
            if (q.isEmpty()) continue
            val bmp = fetchCoverFromGoogleBooks(q) ?: fetchCoverFromItunes(q)
            if (bmp != null) return bmp
        }
        return null
    }

    private fun prefetchMissingLibraryCoversAsync(books: List<BookEntry>) {
        val missing = books.filter { book ->
            val cached = bookCoverCache[book.uriString]
            val autoExists = getCoverFile(book.uriString, custom = false).exists()
            val customExists = getCoverFile(book.uriString, custom = true).exists()
            cached == null && !autoExists && !customExists
        }
        if (missing.isEmpty()) return

        val gen = ++libraryCoverFetchGeneration

        Thread {
            for (book in missing) {
                if (isFinishing || isDestroyed || gen != libraryCoverFetchGeneration) return@Thread
                val targetUri = book.uriString
                val autoFile = getCoverFile(targetUri, custom = false)

                val nativeBmp = findNativeBookCover(targetUri)
                if (nativeBmp != null) {
                    saveBitmapToFile(nativeBmp, autoFile, 600)
                    val savedBmp = BitmapFactory.decodeFile(autoFile.absolutePath) ?: nativeBmp
                    bookCoverCache[targetUri] = savedBmp
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && isGridViewMode && currentTab == 1 && gen == libraryCoverFetchGeneration) {
                            renderFilteredLibrary()
                        }
                    }
                    continue
                }

                if (autoFile.exists()) {
                    val existingBmp = BitmapFactory.decodeFile(autoFile.absolutePath)
                    if (existingBmp != null) {
                        bookCoverCache[targetUri] = existingBmp
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed && isGridViewMode && currentTab == 1 && gen == libraryCoverFetchGeneration) {
                                renderFilteredLibrary()
                            }
                        }
                        continue
                    }
                }

                val fetched = fetchCoverBitmapForBook(book.title, targetUri)
                if (fetched != null) {
                    saveBitmapToFile(fetched, autoFile, 600)
                    val savedBmp = BitmapFactory.decodeFile(autoFile.absolutePath) ?: fetched
                    bookCoverCache[targetUri] = savedBmp
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && isGridViewMode && currentTab == 1 && gen == libraryCoverFetchGeneration) {
                            renderFilteredLibrary()
                        }
                    }
                }
                try {
                    Thread.sleep(150L)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }.start()
    }

    private fun saveBitmapToFile(bmp: Bitmap, target: File, maxSize: Int) {
        val w = bmp.width
        val h = bmp.height
        val scale = maxSize.toFloat() / maxOf(w, h).coerceAtLeast(1)
        val outBmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
        } else {
            bmp
        }
        FileOutputStream(target).use { out ->
            outBmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        if (outBmp != bmp) outBmp.recycle()
    }

    private fun syncWidgetCover(bmp: Bitmap) {
        try {
            val coverFile = File(cacheDir, "widget_cover.jpg")
            saveBitmapToFile(bmp, coverFile, 256)
            PlaybackService.notifyWidgets(this)
        } catch (_: Exception) {
        }
    }

    private fun showCoverMenu() {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val bookUri = prefs.getString("folder_uri", null) ?: return
        val bookTitle = getActiveBookLookupTitle()

        val options = arrayOf(
            "Выбрать из галереи",
            "Найти в интернете",
            "Сбросить обложку"
        )

        AlertDialog.Builder(this)
            .setTitle("Обложка книги")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, 201)
                    }
                    1 -> {
                        if (bookTitle.isNotEmpty()) {
                            Toast.makeText(this, "Ищу обложку...", Toast.LENGTH_SHORT).show()
                            searchOnlineCover(bookUri, bookTitle, asCustom = true)
                        }
                    }
                    2 -> {
                        getCoverFile(bookUri, custom = true).delete()
                        getCoverFile(bookUri, custom = false).delete()
                        bookCoverCache.remove(bookUri)
                        attemptedCoverSearch.remove(bookUri)
                        lastArtworkSize = -1
                        lastCoverBookUri = null
                        setCoverBitmap(null)
                        loadFallbackCoverAsync(bookUri, bookTitle)
                        if (isGridViewMode) renderFilteredLibrary()
                        Toast.makeText(this, "Обложка сброшена к родной", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun loadFallbackCoverAsync(bookUriStr: String, bookTitle: String) {
        Thread {
            val nativeBmp = findNativeBookCover(bookUriStr)
            if (nativeBmp != null) {
                val nativeFile = getCoverFile(bookUriStr, custom = false)
                saveBitmapToFile(nativeBmp, nativeFile, 600)
                val savedBmp = BitmapFactory.decodeFile(nativeFile.absolutePath) ?: nativeBmp
                bookCoverCache[bookUriStr] = savedBmp
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val currentActiveUri = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                        .getString("folder_uri", null)
                    if (currentActiveUri == bookUriStr) {
                        syncWidgetCover(savedBmp)
                        setCoverBitmap(savedBmp)
                    }
                    if (isGridViewMode) renderFilteredLibrary()
                }
                return@Thread
            }

            val autoFile = getCoverFile(bookUriStr, custom = false)
            if (autoFile.exists()) {
                val bmp = BitmapFactory.decodeFile(autoFile.absolutePath)
                if (bmp != null) {
                    bookCoverCache[bookUriStr] = bmp
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        val currentActiveUri = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                            .getString("folder_uri", null)
                        if (currentActiveUri == bookUriStr) {
                            syncWidgetCover(bmp)
                            setCoverBitmap(bmp)
                        }
                        if (isGridViewMode) renderFilteredLibrary()
                    }
                    return@Thread
                }
            }

            if (attemptedCoverSearch.add(bookUriStr)) {
                searchOnlineCoverInternal(bookUriStr, bookTitle, asCustom = false, notifyIfNotFound = false)
            }
        }.start()
    }

    private fun searchOnlineCover(bookUriStr: String, bookTitle: String, asCustom: Boolean) {
        Thread {
            searchOnlineCoverInternal(bookUriStr, bookTitle, asCustom = asCustom, notifyIfNotFound = true)
        }.start()
    }

    private fun searchOnlineCoverInternal(
        bookUriStr: String,
        bookTitle: String,
        asCustom: Boolean,
        notifyIfNotFound: Boolean
    ) {
        val bmp = fetchCoverBitmapForBook(bookTitle, bookUriStr)
        if (bmp != null) {
            val target = getCoverFile(bookUriStr, custom = asCustom)
            saveBitmapToFile(bmp, target, 600)
            val savedBmp = BitmapFactory.decodeFile(target.absolutePath) ?: bmp
            bookCoverCache[bookUriStr] = savedBmp
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val currentActiveUri = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                    .getString("folder_uri", null)
                if (currentActiveUri == bookUriStr) {
                    syncWidgetCover(savedBmp)
                    setCoverBitmap(savedBmp)
                }
                if (isGridViewMode) renderFilteredLibrary()
                if (notifyIfNotFound && currentActiveUri == bookUriStr) {
                    Toast.makeText(this, "Обложка обновлена", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (notifyIfNotFound) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Обложка не найдена", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun findCoverInFolder(treeUri: Uri): Bitmap? {
        return try {
            val dir = DocumentFile.fromTreeUri(this, treeUri) ?: return null
            val images = dir.listFiles().filter { file ->
                val name = file.name?.lowercase() ?: ""
                file.isFile && imageExt.any { name.endsWith(it) }
            }
            if (images.isEmpty()) return null

            val preferred = images.firstOrNull {
                val n = it.name?.lowercase() ?: ""
                n.contains("cover") || n.contains("folder") || n.contains("front")
            } ?: images.first()

            contentResolver.openInputStream(preferred.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadBitmap(imageUrl: String): Bitmap? {
        return try {
            val conn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            if (conn.responseCode != 200) return null
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchCoverFromGoogleBooks(query: String): Bitmap? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=8")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val items = JSONObject(body).optJSONArray("items") ?: return null
            for (i in 0 until items.length()) {
                val links = items.getJSONObject(i)
                    .optJSONObject("volumeInfo")
                    ?.optJSONObject("imageLinks") ?: continue
                val rawUrl = links.optString("thumbnail", "").ifEmpty {
                    links.optString("smallThumbnail", "")
                }
                if (rawUrl.isNotEmpty()) {
                    val httpsUrl = rawUrl.replace("http://", "https://")
                    val bmp = downloadBitmap(httpsUrl)
                    if (bmp != null) return bmp
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchCoverFromItunes(query: String): Bitmap? {
        val medias = listOf("audiobook", "ebook")
        for (media in medias) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://itunes.apple.com/search?term=$encoded&media=$media&limit=5")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                }
                if (conn.responseCode != 200) continue
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val results = JSONObject(body).optJSONArray("results") ?: continue
                for (i in 0 until results.length()) {
                    val art = results.getJSONObject(i).optString("artworkUrl100", "")
                    if (art.isNotEmpty()) {
                        val hiRes = art.replace("100x100bb", "600x600bb")
                        val bmp = downloadBitmap(hiRes)
                        if (bmp != null) return bmp
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun showApiKeyDialog() {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val currentKey = prefs.getString("gemini_api_key", "") ?: ""

        val input = EditText(this).apply {
            setText(currentKey)
            hint = "Вставьте ключ из AI Studio"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setSingleLine(true)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Ключ Gemini API")
            .setMessage("Хранится только на этом устройстве. Получить бесплатно можно в Google AI Studio.")
            .setView(container)
            .setPositiveButton("Сохранить") { _, _ ->
                val trimmed = input.text.toString().trim()
                prefs.edit().putString("gemini_api_key", trimmed).remove("gemini_model_cache").apply()
                updateAiKeyCard()
            }
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Удалить") { _, _ ->
                prefs.edit().remove("gemini_api_key").remove("gemini_model_cache").apply()
                updateAiKeyCard()
            }
            .show()
    }

    private fun updateAiKeyCard() {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val key = prefs.getString("gemini_api_key", "")?.trim() ?: ""
        val t = currentTheme

        aiKeyCard.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (key.isNotEmpty()) t.activeCard else t.surface)
        }
        aiKeyTitle.setTextColor(t.textPrimary)
        aiKeyStatus.setTextColor(t.textSecondary)
        aiKeyStatus.text = if (key.isNotEmpty()) {
            "Ключ сохранён (${key.take(6)}••••) — нажмите, чтобы изменить"
        } else {
            "Не задан — нажмите, чтобы ввести"
        }
    }

    private fun normalizeQuery(raw: String): String {
        return raw
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*]"), " ")
            .replace("_", " ")
            .replace("—", " ")
            .replace("–", " ")
            .replace(" - ", " ")
            .replace(Regex("([!?,.;:])(?=\\S)"), "$1 ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildCurrentProgressContext(): Triple<Int, Int, String> {
        val player = controller
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        if (player != null && player.mediaItemCount > 0) {
            val posMs = player.currentPosition.coerceAtLeast(0L)
            if (player.mediaItemCount == 1 && activeM4bChapters.size > 1) {
                val curCh = currentM4bChapter(posMs) ?: activeM4bChapters.first()
                return Triple(curCh.index + 1, activeM4bChapters.size, curCh.title)
            }
            val curIdx = player.currentMediaItemIndex + 1
            val total = player.mediaItemCount
            val title = player.currentMediaItem?.mediaMetadata?.title?.toString()
                ?: "Глава $curIdx"
            return Triple(curIdx, total, title)
        }
        val savedIdx = prefs.getInt("last_index", 0) + 1
        return Triple(savedIdx, 0, "Глава $savedIdx")
    }

    private fun showStoryRecapDialog(bookTitle: String, forceRefresh: Boolean = false) {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "")?.trim() ?: ""
        if (apiKey.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Освежить память")
                .setMessage("Для пересказа событий до текущей главы нужен API-ключ Gemini. Добавьте его в Настройках.")
                .setPositiveButton("В Настройки") { _, _ -> showTab(2) }
                .setNegativeButton("Закрыть", null)
                .show()
            return
        }

        val (chapterNum, totalChapters, chapterTitle) = buildCurrentProgressContext()
        val totalMs = computeTotalBookDurationMs()
        val elapsedMs = controller?.let { computeElapsedBookMs(it) } ?: 0L
        val pct = if (totalMs > 0L) {
            ((elapsedMs.toDouble() / totalMs.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } else -1

        val cacheKey = "recap_${bookTitle}_${chapterNum}_${chapterTitle}"
        val cached = prefs.getString(cacheKey, null)

        val subtitleInfo = buildString {
            append("Глава $chapterNum")
            if (totalChapters > 0) append(" из $totalChapters")
            if (pct >= 0) append(" ($pct%)")
        }

        if (!forceRefresh && !cached.isNullOrEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Что было ранее • $subtitleInfo")
                .setMessage(cached)
                .setPositiveButton("Закрыть", null)
                .setNeutralButton("Обновить") { _, _ ->
                    showStoryRecapDialog(bookTitle, forceRefresh = true)
                }
                .show()
            return
        }

        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("Освежить память • $subtitleInfo")
            .setMessage("Вспоминаю события до главы «$chapterTitle»...")
            .setNegativeButton("Отмена", null)
            .show()

        Thread {
            val (recap, err) = fetchStoryRecapFromGemini(
                rawTitle = bookTitle,
                chapterNum = chapterNum,
                totalChapters = totalChapters,
                chapterTitle = chapterTitle,
                percent = pct,
                apiKey = apiKey
            )

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loadingDialog.dismiss()

                if (recap != null) {
                    prefs.edit().putString(cacheKey, recap).apply()
                    AlertDialog.Builder(this)
                        .setTitle("Что было ранее • $subtitleInfo")
                        .setMessage(recap)
                        .setPositiveButton("Закрыть", null)
                        .setNeutralButton("Обновить") { _, _ ->
                            showStoryRecapDialog(bookTitle, forceRefresh = true)
                        }
                        .show()
                } else {
                    val msg = buildString {
                        append("Не удалось составить пересказ. Проверьте интернет, VPN и ключ Gemini.")
                        if (!err.isNullOrEmpty()) append("\n\n($err)")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Освежить память")
                        .setMessage(msg)
                        .setPositiveButton("Закрыть", null)
                        .setNeutralButton("Повторить") { _, _ ->
                            showStoryRecapDialog(bookTitle, forceRefresh = true)
                        }
                        .show()
                }
            }
        }.start()
    }

    private fun fetchStoryRecapFromGemini(
        rawTitle: String,
        chapterNum: Int,
        totalChapters: Int,
        chapterTitle: String,
        percent: Int,
        apiKey: String
    ): Pair<String?, String?> {
        val progressDesc = buildString {
            append("текущая глава №$chapterNum")
            if (totalChapters > 0) append(" из $totalChapters")
            append(" («$chapterTitle»)")
            if (percent >= 0) append(", прослушано примерно $percent% книги")
        }
        val prompt = """
            Книга по названию папки/файла: "$rawTitle".
            Отметка слушателя: $progressDesc.
            Напиши по-русски без Markdown (без *, #, **) краткое напоминание событий ДО начала этой главы без спойлеров к текущей и будущим главам.
            Формат: до 4 коротких пунктов с • в начале строки (герои, завязка и ключевые события пройденной части). Если не уверен — ответь ровно: НЕИЗВЕСТНО.
        """.trimIndent()

        return callGeminiText(prompt, apiKey)
    }

    private fun showBookInfoDialog(bookTitle: String, forceRefresh: Boolean) {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val cacheKey = "book_info_$bookTitle"
        val cached = prefs.getString(cacheKey, null)

        if (!forceRefresh && !cached.isNullOrEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(bookTitle)
                .setMessage(cached)
                .setPositiveButton("Закрыть", null)
                .setNegativeButton("Освежить память") { _, _ ->
                    showStoryRecapDialog(bookTitle, forceRefresh = false)
                }
                .setNeutralButton("Обновить") { _, _ ->
                    showBookInfoDialog(bookTitle, forceRefresh = true)
                }
                .show()
            return
        }

        val loadingDialog = AlertDialog.Builder(this)
            .setTitle(bookTitle)
            .setMessage("Ищу информацию о книге...")
            .setNegativeButton("Отмена", null)
            .show()

        val apiKey = prefs.getString("gemini_api_key", "")?.trim() ?: ""
        val cleanQuery = normalizeQuery(bookTitle)

        Thread {
            var geminiError: String? = null
            val geminiResult = if (apiKey.isNotEmpty()) {
                val (res, err) = fetchFromGemini(bookTitle, apiKey)
                geminiError = err
                res
            } else null

            val result = geminiResult
                ?: fetchFromGoogleBooks(cleanQuery)
                ?: fetchFromWikipedia(cleanQuery)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loadingDialog.dismiss()

                if (result != null) {
                    prefs.edit().putString(cacheKey, result).apply()
                    AlertDialog.Builder(this)
                        .setTitle(bookTitle)
                        .setMessage(result)
                        .setPositiveButton("Закрыть", null)
                        .setNegativeButton("Освежить память") { _, _ ->
                            showStoryRecapDialog(bookTitle, forceRefresh = false)
                        }
                        .setNeutralButton("Обновить") { _, _ ->
                            showBookInfoDialog(bookTitle, forceRefresh = true)
                        }
                        .show()
                } else {
                    val hint = buildString {
                        if (apiKey.isNotEmpty()) {
                            append("Не удалось найти описание. Проверьте интернет, VPN и ключ Gemini в Настройках.")
                            if (!geminiError.isNullOrEmpty()) {
                                append("\n\n($geminiError)")
                            }
                        } else {
                            append("Не удалось найти описание. Проверьте подключение к интернету или добавьте ключ Gemini в Настройках.")
                        }
                    }
                    AlertDialog.Builder(this)
                        .setTitle(bookTitle)
                        .setMessage(hint)
                        .setPositiveButton("Закрыть", null)
                        .setNegativeButton("Освежить память") { _, _ ->
                            showStoryRecapDialog(bookTitle, forceRefresh = false)
                        }
                        .setNeutralButton("Повторить") { _, _ ->
                            showBookInfoDialog(bookTitle, forceRefresh = true)
                        }
                        .show()
                }
            }
        }.start()
    }

    private fun fetchFromGemini(rawTitle: String, apiKey: String): Pair<String?, String?> {
        val prompt = "По названию папки аудиокниги определи книгу (учитывай возможный транслит, опечатки, номера или имя чтеца). Название: \"$rawTitle\". Напиши на русском языке без Markdown-разметки: Автор, год выхода (если известен) и краткую аннотацию без спойлеров (3-5 предложений). Если не уверен, что это за книга — ответь ровно одним словом: НЕИЗВЕСТНО."
        return callGeminiText(prompt, apiKey)
    }

    private fun listCandidateModels(apiKey: String): Pair<List<String>, String?> {
        return try {
            val listUrl = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=50")
            val conn = (listUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                requestMethod = "GET"
                setRequestProperty("x-goog-api-key", apiKey)
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                val errMsg = extractApiErrorMessage(body) ?: "HTTP $code"
                return Pair(emptyList(), "ListModels: $errMsg")
            }

            val modelsArr = JSONObject(body).optJSONArray("models")
            val available = mutableListOf<String>()
            if (modelsArr != null) {
                for (i in 0 until modelsArr.length()) {
                    val mObj = modelsArr.optJSONObject(i) ?: continue
                    val methods = mObj.optJSONArray("supportedGenerationMethods")
                    var supportsGen = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.optString(j) == "generateContent") {
                                supportsGen = true
                                break
                            }
                        }
                    }
                    if (!supportsGen) continue
                    val fullName = mObj.optString("name", "").removePrefix("models/")
                    if (fullName.isNotEmpty() && !fullName.contains("embedding", ignoreCase = true)) {
                        available.add(fullName)
                    }
                }
            }

            val preferred = listOf(
                "gemini-3.8-flash",
                "gemini-2.5-flash",
                "gemini-2.0-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash",
                "gemini-1.5-flash-latest"
            )
            val ordered = mutableListOf<String>()
            for (p in preferred) {
                val match = available.firstOrNull { it.equals(p, ignoreCase = true) }
                if (match != null && !ordered.contains(match)) ordered.add(match)
            }
            for (a in available) {
                if (!ordered.contains(a) && a.contains("flash", ignoreCase = true)) ordered.add(a)
            }
            for (a in available) {
                if (!ordered.contains(a)) ordered.add(a)
            }

            if (ordered.isEmpty()) {
                Pair(preferred, null)
            } else {
                Pair(ordered, null)
            }
        } catch (e: Exception) {
            Pair(emptyList(), "ListModels: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun extractApiErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val msg = JSONObject(body).optJSONObject("error")?.optString("message", "")?.trim()
            if (!msg.isNullOrEmpty()) msg else body.take(160)
        } catch (_: Exception) {
            body.take(160)
        }
    }

    private fun executeGenerateRequest(model: String, payload: String, apiKey: String): Pair<String?, String?> {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 45000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""

        if (code !in 200..299) {
            val detail = extractApiErrorMessage(body) ?: "HTTP $code"
            return Pair(null, "Gemini ($model): $detail")
        }

        val text = JSONObject(body)
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text", "")
            ?.trim()

        return if (text.isNullOrEmpty() || text.equals("НЕИЗВЕСТНО", ignoreCase = true)) {
            Pair(null, "Gemini: книга не распознана")
        } else {
            Pair(text, null)
        }
    }

    private fun callGeminiText(prompt: String, apiKey: String): Pair<String?, String?> {
        val (models, listErr) = listCandidateModels(apiKey)
        if (models.isEmpty()) {
            return Pair(null, listErr ?: "Gemini: нет доступных моделей")
        }

        val payload = JSONObject().put(
            "contents",
            JSONArray().put(
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", prompt))
                )
            )
        ).toString()

        var lastError: String? = null
        for (model in models) {
            var attempt = 0
            while (attempt < 2) {
                attempt++
                try {
                    val (res, err) = executeGenerateRequest(model, payload, apiKey)
                    if (res != null) {
                        getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                            .edit().putString("gemini_model_cache", model).apply()
                        return Pair(res, null)
                    }
                    lastError = err
                    val lowerErr = (err ?: "").lowercase()
                    val transient = lowerErr.contains("high demand") ||
                        lowerErr.contains("overloaded") ||
                        lowerErr.contains("rate limit") ||
                        lowerErr.contains("quota")
                    if (!transient) break
                } catch (_: SocketTimeoutException) {
                    lastError = "Gemini ($model): таймаут ответа"
                } catch (e: Exception) {
                    lastError = "Gemini ($model): ${e.javaClass.simpleName}: ${e.message}"
                    break
                }
            }
        }

        return Pair(null, lastError ?: "Gemini: ошибка запроса")
    }

    private fun fetchFromGoogleBooks(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://www.googleapis.com/books/v1/volumes?q=$encoded&langRestrict=ru&maxResults=5")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val items = JSONObject(body).optJSONArray("items") ?: return null
            if (items.length() == 0) return null

            var fallback: String? = null
            for (i in 0 until items.length()) {
                val info = items.getJSONObject(i).optJSONObject("volumeInfo") ?: continue
                val authorsArr = info.optJSONArray("authors")
                val authors = if (authorsArr != null && authorsArr.length() > 0) {
                    (0 until authorsArr.length()).joinToString(", ") { authorsArr.getString(it) }
                } else ""
                val year = info.optString("publishedDate", "").take(4)
                val description = info.optString("description", "")
                    .replace(Regex("<[^>]*>"), "")
                    .trim()

                val formatted = buildString {
                    if (authors.isNotEmpty()) append("Автор: $authors\n")
                    if (year.isNotEmpty()) append("Год: $year\n")
                    if (isNotEmpty() && description.isNotEmpty()) append("\n")
                    if (description.isNotEmpty()) append(description)
                }.trim()

                if (description.isNotEmpty()) return formatted
                if (fallback == null && formatted.isNotEmpty()) fallback = formatted
            }
            fallback
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromWikipedia(query: String): String? {
        return try {
            val encodedSearch = URLEncoder.encode(query, "UTF-8")
            val searchUrl = URL("https://ru.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedSearch&utf8=&format=json&srlimit=1")
            val searchConn = (searchUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "AudiobookPlayerApp/1.0")
            }
            if (searchConn.responseCode != 200) return null
            val searchBody = searchConn.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(searchBody)
                .optJSONObject("query")
                ?.optJSONArray("search") ?: return null
            if (results.length() == 0) return null

            val pageTitle = results.getJSONObject(0).optString("title", "").trim()
            if (pageTitle.isEmpty()) return null

            val encodedTitle = URLEncoder.encode(pageTitle.replace(" ", "_"), "UTF-8")
            val summaryUrl = URL("https://ru.wikipedia.org/api/rest_v1/page/summary/$encodedTitle")
            val summaryConn = (summaryUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "AudiobookPlayerApp/1.0")
            }
            if (summaryConn.responseCode != 200) return null
            val summaryBody = summaryConn.inputStream.bufferedReader().use { it.readText() }
            val extract = JSONObject(summaryBody).optString("extract", "").trim()
            extract.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyTheme() {
        val t = currentTheme
        rootLayout.setBackgroundColor(t.bg)
        window.statusBarColor = t.bg
        window.navigationBarColor = t.navBg

        topHeaderView.setTextColor(t.textPrimary)
        authorSubView.setTextColor(t.textPrimary)
        bookTitleView.setTextColor(t.textSecondary)
        val currentCoverBmp = (coverView.drawable as? BitmapDrawable)?.bitmap
        updateCoverGlowColor(currentCoverBmp)
        coverView.setBackgroundColor(t.surface)
        speedBtn.setTextColor(t.textPrimary)
        back15Btn.setColorFilter(t.textPrimary, PorterDuff.Mode.SRC_IN)
        fwd30Btn.setColorFilter(t.textPrimary, PorterDuff.Mode.SRC_IN)
        playPauseBtn.setColorFilter(t.accentText, PorterDuff.Mode.SRC_IN)
        playPauseBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(t.accentBg)
        }
        chapterBtn.setTextColor(t.textPrimary)
        bookmarkBtn.setColorFilter(t.textSecondary, PorterDuff.Mode.SRC_IN)
        elapsedTimeView.setTextColor(t.textSecondary)
        remainingTimeView.setTextColor(t.textSecondary)
        seekBar.progressTintList = ColorStateList.valueOf(t.accentBg)
        seekBar.thumbTintList = ColorStateList.valueOf(t.accentBg)
        bookProgressTextView.setTextColor(t.textSecondary)
        updateTimerButton()

        searchBarContainer.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(t.surface)
        }
        searchInput.setTextColor(t.textPrimary)
        searchInput.setHintTextColor(t.textSecondary)
        clearSearchBtn.setTextColor(t.textSecondary)
        rebuildFilterChips()
        libraryStatusView.setTextColor(t.textSecondary)

        settingsTitleView.setTextColor(t.textPrimary)
        librarySectionLabel.setTextColor(t.textSecondary)
        libraryFolderPathView.setTextColor(t.textSecondary)
        themeSectionLabel.setTextColor(t.textSecondary)
        badgeSectionLabel.setTextColor(t.textSecondary)
        rewindSectionLabel.setTextColor(t.textSecondary)
        aiSectionLabel.setTextColor(t.textSecondary)
        helpSectionLabel.setTextColor(t.textSecondary)
        helpCard.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(t.surface)
        }
        helpCardTitle.setTextColor(t.textPrimary)
        helpCardSubtitle.setTextColor(t.textSecondary)
        rebuildThemeButtons()
        rebuildBadgeButtons()
        updateRewindCard()
        updateShakeCard()
        updateAiKeyCard()

        bottomNavBar.setBackgroundColor(t.navBg)
        updateNavColors()
    }

    private fun themeSwatchColor(themeOption: AppTheme): Int {
        return when (themeOption.id) {
            "dark" -> Color.parseColor("#2A2A32")
            "light" -> Color.parseColor("#F5F6F8")
            "dark_green" -> Color.parseColor("#66BB6A")
            else -> themeOption.accentBg
        }
    }

    private fun rebuildThemeButtons() {
        themeButtonsContainer.removeAllViews()
        val t = currentTheme
        for (themeOption in themes) {
            val isSelected = themeOption.id == t.id
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(14), dp(18), dp(14))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (isSelected) t.activeCard else t.surface)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(8)
                }
                setOnClickListener {
                    currentTheme = themeOption
                    getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("app_theme", themeOption.id)
                        .apply()
                    applyTheme()
                }
            }

            val previewDot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(themeSwatchColor(themeOption))
                    setStroke(dp(2), t.textSecondary)
                }
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                    marginEnd = dp(14)
                }
            }

            val label = TextView(this).apply {
                text = themeOption.name
                textSize = 16f
                setTextColor(t.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val check = TextView(this).apply {
                text = if (isSelected) "✓" else ""
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(t.navActive)
            }

            card.addView(previewDot)
            card.addView(label)
            card.addView(check)
            themeButtonsContainer.addView(card)
        }
    }

    private fun rebuildBadgeButtons() {
        badgeButtonsContainer.removeAllViews()
        val t = currentTheme
        for (option in badges) {
            val isSelected = option.id == currentBadge.id
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (isSelected) t.activeCard else t.surface)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(12)
                }
                setOnClickListener {
                    currentBadge = option
                    getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("app_badge", option.id)
                        .apply()
                    applyLauncherIcon(option.id)
                    rebuildBadgeButtons()
                }
            }

            val thumb = ImageView(this).apply {
                setImageResource(option.drawableRes)
                scaleType = ImageView.ScaleType.CENTER_CROP
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            }

            card.addView(thumb)

            if (isSelected) {
                val check = TextView(this).apply {
                    text = "✓"
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(t.navActive)
                    setPadding(dp(10), 0, 0, 0)
                }
                card.addView(check)
            }

            badgeButtonsContainer.addView(card)
        }
    }

    private fun updateNavColors() {
        val t = currentTheme
        navPlayerIcon.setColorFilter(if (currentTab == 0) t.navActive else t.navInactive, PorterDuff.Mode.SRC_IN)
        navLibraryIcon.setColorFilter(if (currentTab == 1) t.navActive else t.navInactive, PorterDuff.Mode.SRC_IN)
        navSettingsIcon.setColorFilter(if (currentTab == 2) t.navActive else t.navInactive, PorterDuff.Mode.SRC_IN)
    }

    private fun showTab(tabIndex: Int) {
        currentTab = tabIndex
        playerContainer.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        libraryContainer.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        settingsScrollView.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        updateNavColors()
        syncGlowAnimationState(controller?.isPlaying == true)
        if (tabIndex == 1) scanLibrary()
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
                if (sleepTimerEndMs <= 0L) {
                    restorePlaybackVolumeIfNeeded()
                }
                syncShakeListener()
                updateTimerButton()
            }
            .show()
    }

    private fun checkSleepTimer() {
        if (sleepTimerEndMs <= 0L) {
            syncShakeListener()
            return
        }

        val now = System.currentTimeMillis()
        val remainingMs = sleepTimerEndMs - now

        if (remainingMs <= 0L) {
            sleepTimerEndMs = 0L
            controller?.pause()
            restorePlaybackVolumeIfNeeded()
            saveCurrentProgress()
            syncShakeListener()
            updateTimerButton()
            return
        }

        if (remainingMs <= sleepFadeDurationMs) {
            isFadingVolume = true
            val factor = (remainingMs.toFloat() / sleepFadeDurationMs.toFloat()).coerceIn(0.0f, 1.0f)
            controller?.volume = factor
        } else if (isFadingVolume) {
            restorePlaybackVolumeIfNeeded()
        }

        syncShakeListener()
        updateTimerButton()
    }

    private fun updateTimerButton() {
        if (sleepTimerEndMs <= 0L) {
            timerBtn.text = "⏱"
            timerBtn.setTextColor(currentTheme.textPrimary)
        } else {
            val leftMs = (sleepTimerEndMs - System.currentTimeMillis()).coerceAtLeast(0L)
            val label = if (leftMs < 60_000L) {
                "${((leftMs + 999L) / 1000L)}с"
            } else {
                "${(leftMs / 60_000L) + 1}м"
            }
            timerBtn.text = label
            timerBtn.setTextColor(currentTheme.timerActive)
        }
    }

    private fun showChaptersDialog() {
        val player = controller ?: return
        val count = player.mediaItemCount
        if (count == 0) return

        if (count == 1 && activeM4bChapters.size > 1) {
            val currentCh = currentM4bChapter(player.currentPosition.coerceAtLeast(0L))
            val labels = Array(activeM4bChapters.size) { i ->
                val ch = activeM4bChapters[i]
                val prefix = if (currentCh?.index == ch.index) "▶ " else ""
                "$prefix${ch.title} (${formatTime(ch.startMs)})"
            }
            AlertDialog.Builder(this)
                .setTitle("Главы M4B (${activeM4bChapters.size})")
                .setItems(labels) { _, which ->
                    suppressNextRewindOnce = true
                    val target = activeM4bChapters[which]
                    player.seekTo(0, target.startMs)
                    player.play()
                    saveCurrentProgress()
                    updateUI()
                }
                .show()
            return
        }

        val titles = Array(count) { i ->
            val title = player.getMediaItemAt(i).mediaMetadata.title?.toString() ?: "Глава ${i + 1}"
            if (i == player.currentMediaItemIndex) "▶ $title" else title
        }

        AlertDialog.Builder(this)
            .setTitle("Главы ($count)")
            .setItems(titles) { _, which ->
                suppressNextRewindOnce = true
                player.seekTo(which, 0L)
                player.play()
                saveCurrentProgress()
                updateUI()
            }
            .show()
    }

    override fun onStart() {
        super.onStart()
        try {
            startService(Intent(this, PlaybackService::class.java))
        } catch (_: Exception) {
        }
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.let { p ->
                wasPlayingPreviously = p.isPlaying
                if (sleepTimerEndMs <= 0L) {
                    p.volume = 1.0f
                }
                p.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        handlePlaybackStateTransition(player)
                        updateUI()
                    }
                })
            }
            restoreSavedBookIfEmpty()
            updateUI()
            syncShakeListener()
            handler.post(updateProgressTask)
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        saveCurrentProgress()
        restorePlaybackVolumeIfNeeded()
        glowAnimator?.pause()
        val player = controller
        if (player != null && !player.isPlaying && wasPlayingPreviously) {
            getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_pause_timestamp_ms", System.currentTimeMillis())
                .apply()
        }
        handler.removeCallbacks(updateProgressTask)
        if (isShakeRegistered) {
            sensorManager?.unregisterListener(this)
            isShakeRegistered = false
        }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        wasPlayingPreviously = false
        super.onStop()
    }

    override fun onDestroy() {
        libraryCoverFetchGeneration++
        glowAnimator?.cancel()
        glowAnimator = null
        val d = coverGlowView.drawable as? BitmapDrawable
        coverGlowView.setImageDrawable(null)
        d?.bitmap?.recycle()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200 && resultCode == RESULT_OK) {
            val treeUri = data?.data ?: return
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("library_uri", treeUri.toString()).apply()
            bookCoverCache.clear()
            updateLibraryFolderLabel()
            scanLibrary()
        } else if (requestCode == 201 && resultCode == RESULT_OK) {
            val imageUri = data?.data ?: return
            val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
            val bookUri = prefs.getString("folder_uri", null) ?: return
            Thread {
                try {
                    val bmp = contentResolver.openInputStream(imageUri)?.use {
                        BitmapFactory.decodeStream(it)
                    } ?: return@Thread
                    val customFile = getCoverFile(bookUri, custom = true)
                    saveBitmapToFile(bmp, customFile, 600)
                    val savedBmp = BitmapFactory.decodeFile(customFile.absolutePath) ?: bmp
                    bookCoverCache[bookUri] = savedBmp
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        val currentActiveUri = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                            .getString("folder_uri", null)
                        if (currentActiveUri == bookUri) {
                            syncWidgetCover(savedBmp)
                            setCoverBitmap(savedBmp)
                        }
                        if (isGridViewMode) renderFilteredLibrary()
                        Toast.makeText(this, "Обложка сохранена", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                }
            }.start()
        }
    }

    private fun renderFilteredLibrary() {
        if (!::libraryListLayout.isInitialized) return
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val libUriStr = prefs.getString("library_uri", null)
        if (libUriStr.isNullOrEmpty()) {
            libraryListLayout.removeAllViews()
            libraryStatusView.text = "Выберите папку Books в Настройках"
            return
        }
        val activeUri = prefs.getString("folder_uri", null)
        val rawQuery = searchInput.text?.toString()?.trim()?.lowercase() ?: ""
        val terms = rawQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val activeFilterObj = libraryFilters.firstOrNull { it.id == selectedFilterId }

        val filtered = cachedBooks.filter { book ->
            val savedIdx = prefs.getInt("idx_${book.uriString}", -1)
            val assignedStatus = getBookAssignedStatus(book.uriString)
            val matchesFilter = when {
                selectedFilterId == "all" || activeFilterObj == null -> true
                activeFilterObj.isSmartInProgress -> savedIdx >= 0 && assignedStatus == null
                activeFilterObj.isSmartNotStarted -> savedIdx < 0 && assignedStatus == null
                else -> assignedStatus == activeFilterObj.id
            }
            if (!matchesFilter) return@filter false

            if (terms.isEmpty()) return@filter true
            val haystack = "${book.title} ${book.subtitle}".lowercase()
            terms.all { haystack.contains(it) }
        }

        libraryListLayout.removeAllViews()
        if (cachedBooks.isEmpty()) {
            libraryStatusView.text = "В выбранной папке не найдено аудиокниг"
            return
        }

        val hasFilterOrSearch = selectedFilterId != "all" || terms.isNotEmpty()
        libraryStatusView.text = if (!hasFilterOrSearch) {
            "Найдено книг: ${cachedBooks.size}"
        } else {
            "Найдено: ${filtered.size} из ${cachedBooks.size}"
        }

        if (filtered.isEmpty()) {
            val emptyMsg = if (terms.isNotEmpty()) {
                "Ничего не найдено по запросу «${searchInput.text?.toString()?.trim()}»"
            } else if (activeFilterObj != null) {
                "Нет книг в фильтре «${activeFilterObj.label}» (долгое нажатие на книге задаёт статус)"
            } else {
                "Ничего не найдено"
            }
            val emptyTv = TextView(this).apply {
                text = emptyMsg
                textSize = 14f
                setTextColor(currentTheme.textSecondary)
                setPadding(dp(4), dp(12), dp(4), dp(12))
            }
            libraryListLayout.addView(emptyTv)
            return
        }

        if (isGridViewMode) {
            renderBooksGrid(filtered, activeUri)
            prefetchMissingLibraryCoversAsync(filtered)
        } else {
            for (book in filtered) {
                libraryListLayout.addView(createBookCard(book, book.uriString == activeUri))
            }
        }
    }

    private fun renderBooksGrid(books: List<BookEntry>, activeUri: String?) {
        val gap = dp(10)
        var i = 0
        while (i < books.size) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = gap
                }
            }

            val leftCard = createBookGridTile(books[i], books[i].uriString == activeUri).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = gap / 2
                }
            }
            row.addView(leftCard)

            if (i + 1 < books.size) {
                val rightCard = createBookGridTile(books[i + 1], books[i + 1].uriString == activeUri).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = gap / 2
                    }
                }
                row.addView(rightCard)
            } else {
                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f).apply {
                        marginStart = gap / 2
                    }
                }
                row.addView(spacer)
            }

            libraryListLayout.addView(row)
            i += 2
        }
    }

    private fun createBookGridTile(book: BookEntry, isActive: Boolean): View {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val savedIdx = prefs.getInt("idx_${book.uriString}", -1)
        val savedPos = prefs.getLong("pos_${book.uriString}", 0L)
        val assignedLabel = getBookAssignedStatus(book.uriString)?.let { id ->
            libraryFilters.firstOrNull { it.id == id }?.label
        }
        val t = currentTheme
        val tileHeight = dp(170)

        val frame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(t.surface)
                if (isActive) setStroke(dp(2), t.accentBg)
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(14).toFloat())
                }
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                tileHeight
            )
            setOnClickListener { switchToBook(book.uriString) }
            setOnLongClickListener {
                showAssignBookStatusDialog(book)
                true
            }
        }

        val coverBmp = loadCachedBookCoverBitmap(book.uriString)
        val img = ImageView(this).apply {
            if (coverBmp != null) {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(coverBmp)
            } else {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageResource(android.R.drawable.ic_media_play)
                setColorFilter(t.textSecondary, PorterDuff.Mode.SRC_IN)
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        frame.addView(img)

        val progressRatio = if (savedIdx >= 0) {
            ((savedIdx + if (savedPos > 0L) 0.5f else 0.0f) / 20.0f).coerceIn(0.08f, 0.95f)
        } else {
            0.0f
        }

        val overlayBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#B80B0D10"))
            setPadding(dp(10), dp(7), dp(10), dp(8))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        val titleTv = TextView(this).apply {
            text = if (isActive) "▶ ${book.title}" else book.title
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Color.parseColor("#FFFFFF"))
        }

        val statusText = when {
            assignedLabel != null -> assignedLabel
            savedIdx >= 0 -> "Гл. ${savedIdx + 1} • ${formatTime(savedPos)}"
            else -> "Не начата"
        }

        val metaTv = TextView(this).apply {
            text = statusText
            textSize = 10f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Color.parseColor("#D0D8E0"))
            setPadding(0, dp(2), 0, dp(4))
        }

        val barTrack = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#40FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(4)
            )
        }

        if (progressRatio > 0f) {
            val barFill = View(this).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(3).toFloat()
                    setColor(t.accentBg)
                }
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    post {
                        val fullW = barTrack.width
                        if (fullW > 0) {
                            layoutParams = FrameLayout.LayoutParams(
                                (fullW * progressRatio).toInt().coerceAtLeast(dp(6)),
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        }
                    }
                }
            }
            barTrack.addView(barFill)
        }

        overlayBox.addView(titleTv)
        overlayBox.addView(metaTv)
        overlayBox.addView(barTrack)
        frame.addView(overlayBox)

        return frame
    }

    private fun scanLibrary() {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val libUriStr = prefs.getString("library_uri", null)
        if (libUriStr == null) {
            libraryStatusView.text = "Выберите папку Books в Настройках"
            libraryListLayout.removeAllViews()
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
                if (isFinishing || isDestroyed) return@runOnUiThread
                cachedBooks = books
                renderFilteredLibrary()
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
        val assignedLabel = getBookAssignedStatus(book.uriString)?.let { id ->
            libraryFilters.firstOrNull { it.id == id }?.label
        }
        val t = currentTheme

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (isActive) t.activeCard else t.surface)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(10))
            }
            setOnClickListener {
                switchToBook(book.uriString)
            }
            setOnLongClickListener {
                showAssignBookStatusDialog(book)
                true
            }
        }

        val titleTv = TextView(this).apply {
            text = if (isActive) "▶ ${book.title}" else book.title
            textSize = 16f
            setTextColor(t.textPrimary)
        }

        val progressText = when {
            assignedLabel != null -> assignedLabel
            savedIdx >= 0 -> "Глава ${savedIdx + 1} • ${formatTime(savedPos)}"
            else -> "Не начата"
        }

        val subTv = TextView(this).apply {
            text = if (book.subtitle.isNotEmpty()) "${book.subtitle}  |  $progressText" else progressText
            textSize = 12f
            setTextColor(t.textSecondary)
            setPadding(0, dp(4), 0, 0)
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
            .remove("last_pause_timestamp_ms")
            .apply()

        suppressNextRewindOnce = true
        activeM4bChapters = emptyList()
        lastLoadedChapterUri = null
        lastArtworkSize = -1
        lastCoverBookUri = null
        detectedAuthorForCurrentBook = extractAuthorFromBookUri(bookUriStr)
        setCoverBitmap(null)
        showTab(0)
        loadFolder(Uri.parse(bookUriStr), savedIndex, savedPos, autoPlay = true, showLoadingHint = true)
    }

    private fun saveCurrentProgress() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) return
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val currentUri = prefs.getString("folder_uri", null) ?: return
        val bookTitle = player.currentMediaItem?.mediaMetadata?.artist?.toString()
        val editor = prefs.edit()
            .putInt("last_index", player.currentMediaItemIndex)
            .putLong("last_pos", player.currentPosition)
            .putInt("idx_$currentUri", player.currentMediaItemIndex)
            .putLong("pos_$currentUri", player.currentPosition)
        if (!bookTitle.isNullOrEmpty()) {
            editor.putString("last_book_title", bookTitle)
        }
        if (detectedAuthorForCurrentBook.isNotEmpty()) {
            editor.putString("last_book_author", detectedAuthorForCurrentBook)
        }
        editor.apply()
    }

    private fun restoreSavedBookIfEmpty() {
        val player = controller ?: return
        if (player.mediaItemCount > 0 || isLoadingFolder) return
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("folder_uri", null) ?: return
        val index = prefs.getInt("idx_$uriStr", prefs.getInt("last_index", 0))
        val pos = prefs.getLong("pos_$uriStr", prefs.getLong("last_pos", 0L))
        detectedAuthorForCurrentBook = extractAuthorFromBookUri(uriStr)
        loadFolder(Uri.parse(uriStr), index, pos, autoPlay = false, showLoadingHint = false)
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

    private fun extractAuthorFromFirstAudio(fileUri: Uri): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, fileUri)
            val author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)?.trim()
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                ?: ""
            author
        } catch (_: Exception) {
            ""
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun loadFolder(
        treeUri: Uri,
        startIndex: Int,
        startPos: Long,
        autoPlay: Boolean,
        showLoadingHint: Boolean
    ) {
        if (isLoadingFolder) return
        isLoadingFolder = true
        if (showLoadingHint) {
            chapterBtn.text = "Загружаю главы..."
        }

        Thread {
            try {
                val dir = DocumentFile.fromTreeUri(this, treeUri) ?: return@Thread
                val bookName = dir.name ?: "Аудиокнига"
                val files = mutableListOf<DocumentFile>()
                collectAudioFiles(dir, files)

                var authorName = extractAuthorFromBookUri(treeUri.toString())
                if (authorName.isEmpty() && files.isNotEmpty()) {
                    authorName = extractAuthorFromFirstAudio(files.first().uri)
                }
                detectedAuthorForCurrentBook = authorName

                runOnUiThread {
                    applyBookHeaderText(bookName, authorName)
                    getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("last_book_title", bookName)
                        .putString("last_book_author", authorName)
                        .apply()

                    if (files.isEmpty()) {
                        chapterBtn.text = "В этой папке нет аудиофайлов"
                        return@runOnUiThread
                    }

                    computeBookDurationsAsync(treeUri.toString(), files)

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
                    val activeController = controller
                    if (activeController != null) {
                        suppressNextRewindOnce = true
                        activeController.setMediaItems(items, safeIndex, startPos)
                        activeController.prepare()
                        if (autoPlay) {
                            activeController.playWhenReady = true
                            activeController.play()
                        }
                    }
                    updateUI()
                }
            } finally {
                isLoadingFolder = false
            }
        }.start()
    }

    private fun updateUI() {
        val player = controller ?: return
        refreshM4bChaptersIfNeeded(player)

        val bookUri = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
            .getString("folder_uri", null)
        if (detectedAuthorForCurrentBook.isEmpty() && !bookUri.isNullOrEmpty()) {
            detectedAuthorForCurrentBook = extractAuthorFromBookUri(bookUri)
        }

        val itemMeta = player.currentMediaItem?.mediaMetadata
        val bookTitle = itemMeta?.artist?.toString() ?: "Аудиокнига"
        if (itemMeta != null) {
            applyBookHeaderText(bookTitle, detectedAuthorForCurrentBook)
            val m4bCh = currentM4bChapter(player.currentPosition.coerceAtLeast(0L))
            val chapterName = m4bCh?.title
                ?: itemMeta.title?.toString()
                ?: "Глава ${player.currentMediaItemIndex + 1}"
            chapterBtn.text = "☰  $chapterName"
        }

        val artBytes = player.mediaMetadata.artworkData
        val artSize = artBytes?.size ?: 0
        val bookChanged = bookUri != lastCoverBookUri

        if (bookChanged || artSize != lastArtworkSize) {
            lastCoverBookUri = bookUri
            lastArtworkSize = artSize

            val customFile = bookUri?.let { getCoverFile(it, custom = true) }
            if (customFile != null && customFile.exists()) {
                Thread {
                    val bmp = BitmapFactory.decodeFile(customFile.absolutePath)
                    if (bmp != null) {
                        if (bookUri != null) bookCoverCache[bookUri] = bmp
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            val activeUriNow = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                                .getString("folder_uri", null)
                            if (activeUriNow == bookUri) {
                                syncWidgetCover(bmp)
                                setCoverBitmap(bmp)
                            }
                        }
                    }
                }.start()
            } else {
                val nativeBmp = bookUri?.let { findNativeBookCover(it) }
                if (nativeBmp != null && bookUri != null) {
                    val nativeFile = getCoverFile(bookUri, custom = false)
                    saveBitmapToFile(nativeBmp, nativeFile, 600)
                    val savedBmp = BitmapFactory.decodeFile(nativeFile.absolutePath) ?: nativeBmp
                    bookCoverCache[bookUri] = savedBmp
                    syncWidgetCover(savedBmp)
                    setCoverBitmap(savedBmp)
                } else if (artBytes != null) {
                    Thread {
                        val bmp = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                        runOnUiThread {
                            if (isFinishing || isDestroyed || bmp == null) return@runOnUiThread
                            val activeUriNow = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                                .getString("folder_uri", null)
                            if (activeUriNow == bookUri) {
                                setCoverBitmap(bmp)
                                if (bookUri != null) {
                                    val nativeFile = getCoverFile(bookUri, custom = false)
                                    saveBitmapToFile(bmp, nativeFile, 600)
                                    bookCoverCache[bookUri] = bmp
                                }
                            }
                        }
                    }.start()
                } else {
                    setCoverBitmap(null)
                    val lookup = getActiveBookLookupTitle()
                    if (bookUri != null && lookup.isNotEmpty()) {
                        loadFallbackCoverAsync(bookUri, lookup)
                    }
                }
            }
        }

        playPauseBtn.setImageResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        syncGlowAnimationState(player.isPlaying)
        updateProgressOnly()
    }

    private fun updateProgressOnly() {
        val player = controller ?: return
        val dur = player.duration.coerceAtLeast(0)
        val pos = player.currentPosition.coerceAtLeast(0)
        if (!isSeeking) {
            seekBar.max = dur.toInt()
            seekBar.progress = pos.toInt()
            elapsedTimeView.text = formatTime(pos)
            remainingTimeView.text = "-${formatTime((dur - pos).coerceAtLeast(0))}"
        }
        val m4bCh = currentM4bChapter(pos)
        if (m4bCh != null) {
            chapterBtn.text = "☰  ${m4bCh.title}"
        }
        updateBookProgressUI()
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
