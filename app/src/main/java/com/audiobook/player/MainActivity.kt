package com.audiobook.player

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.Typeface
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class BookEntry(
    val title: String,
    val subtitle: String,
    val uriString: String
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

class MainActivity : Activity() {

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
    private lateinit var settingsContainer: LinearLayout

    private lateinit var bookTitleView: TextView
    private lateinit var coverView: ImageView
    private lateinit var speedBtn: TextView
    private lateinit var back15Btn: TextView
    private lateinit var playPauseBtn: ImageView
    private lateinit var fwd30Btn: TextView
    private lateinit var timerBtn: TextView
    private lateinit var chapterBtn: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var elapsedTimeView: TextView
    private lateinit var remainingTimeView: TextView

    private lateinit var pickLibraryBtn: Button
    private lateinit var libraryStatusView: TextView
    private lateinit var libraryListLayout: LinearLayout

    private lateinit var settingsTitleView: TextView
    private lateinit var themeSectionLabel: TextView
    private lateinit var themeButtonsContainer: LinearLayout
    private lateinit var badgeSectionLabel: TextView
    private lateinit var badgeButtonsContainer: LinearLayout

    private val audioExt = listOf(".mp3", ".m4b", ".m4a", ".ogg", ".opus", ".wav", ".flac", ".aac", ".wma")
    private val speeds = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private var currentSpeedIndex = 0

    private val handler = Handler(Looper.getMainLooper())
    private var isSeeking = false
    private var isLoadingFolder = false
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

        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val savedThemeId = prefs.getString("app_theme", "dark")
        currentTheme = themes.find { it.id == savedThemeId } ?: themes[0]

        val savedBadgeId = prefs.getString("app_badge", "v1")
        currentBadge = badges.find { it.id == savedBadgeId } ?: badges[0]

        buildUI()
        applyTheme()

        val savedBookTitle = prefs.getString("last_book_title", null)
        val savedPos = prefs.getLong("last_pos", 0L)
        if (!savedBookTitle.isNullOrEmpty()) {
            bookTitleView.text = savedBookTitle
            elapsedTimeView.text = formatTime(savedPos)
        }

        showTab(0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUI() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(12))
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

        bookTitleView = TextView(this).apply {
            text = "Книга не выбрана"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                val title = text?.toString()?.trim() ?: ""
                if (title.isNotEmpty() && title != "Книга не выбрана" && title != "Аудиокнига") {
                    showBookInfoDialog(title, forceRefresh = false)
                }
            }
        }

        val coverWrapper = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val coverSize = dp(280)
        coverView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(android.R.drawable.ic_media_play)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
                }
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(coverSize, coverSize)
        }
        coverWrapper.addView(coverView)

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 5f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(20)
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

        speedBtn = makeTextAction("1x", 16f) { cycleSpeed() }
        back15Btn = makeTextAction("↺ 15", 17f) {
            controller?.let {
                it.seekTo((it.currentPosition - 15_000).coerceAtLeast(0))
                saveCurrentProgress()
            }
        }

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
                controller?.let { if (it.isPlaying) it.pause() else it.play() }
            }
        }
        playContainer.addView(playPauseBtn)

        fwd30Btn = makeTextAction("30 ↻", 17f) {
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

        chapterBtn = TextView(this).apply {
            text = "☰  Выберите книгу в Библиотеке"
            textSize = 15f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(14))
            setOnClickListener { showChaptersDialog() }
        }

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

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(4), dp(8), dp(12))
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

        playerContainer.addView(bookTitleView)
        playerContainer.addView(coverWrapper)
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

        libraryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
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

        libraryStatusView = TextView(this).apply {
            text = "Папка библиотеки не выбрана"
            textSize = 14f
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

        settingsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        settingsTitleView = TextView(this).apply {
            text = "Настройки"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(8), dp(4), dp(20))
        }

        themeSectionLabel = TextView(this).apply {
            text = "Тема оформления"
            textSize = 14f
            setPadding(dp(4), 0, dp(4), dp(10))
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

        settingsContainer.addView(settingsTitleView)
        settingsContainer.addView(themeSectionLabel)
        settingsContainer.addView(themeButtonsContainer)
        settingsContainer.addView(badgeSectionLabel)
        settingsContainer.addView(badgeButtonsContainer)

        contentContainer.addView(playerContainer)
        contentContainer.addView(libraryContainer)
        contentContainer.addView(settingsContainer)

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

    private fun showBookInfoDialog(bookTitle: String, forceRefresh: Boolean) {
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val cacheKey = "book_info_$bookTitle"
        val cached = prefs.getString(cacheKey, null)

        if (!forceRefresh && !cached.isNullOrEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(bookTitle)
                .setMessage(cached)
                .setPositiveButton("Закрыть", null)
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

        Thread {
            val result = fetchFromGoogleBooks(bookTitle) ?: fetchFromWikipedia(bookTitle)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loadingDialog.dismiss()

                if (result != null) {
                    prefs.edit().putString(cacheKey, result).apply()
                    AlertDialog.Builder(this)
                        .setTitle(bookTitle)
                        .setMessage(result)
                        .setPositiveButton("Закрыть", null)
                        .setNeutralButton("Обновить") { _, _ ->
                            showBookInfoDialog(bookTitle, forceRefresh = true)
                        }
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle(bookTitle)
                        .setMessage("Не удалось найти описание. Проверьте подключение к интернету или название папки.")
                        .setPositiveButton("Закрыть", null)
                        .setNeutralButton("Повторить") { _, _ ->
                            showBookInfoDialog(bookTitle, forceRefresh = true)
                        }
                        .show()
                }
            }
        }.start()
    }

    private fun fetchFromGoogleBooks(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://www.googleapis.com/books/v1/volumes?q=$encoded&langRestrict=ru&maxResults=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                requestMethod = "GET"
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val items = JSONObject(body).optJSONArray("items") ?: return null
            if (items.length() == 0) return null

            val info = items.getJSONObject(0).optJSONObject("volumeInfo") ?: return null
            val authorsArr = info.optJSONArray("authors")
            val authors = if (authorsArr != null && authorsArr.length() > 0) {
                (0 until authorsArr.length()).joinToString(", ") { authorsArr.getString(it) }
            } else ""
            val year = info.optString("publishedDate", "").take(4)
            val description = info.optString("description", "")
                .replace(Regex("<[^>]*>"), "")
                .trim()

            if (description.isEmpty() && authors.isEmpty()) return null

            buildString {
                if (authors.isNotEmpty()) append("Автор: $authors\n")
                if (year.isNotEmpty()) append("Год: $year\n")
                if (isNotEmpty() && description.isNotEmpty()) append("\n")
                if (description.isNotEmpty()) append(description)
            }.trim()
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromWikipedia(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query.replace(" ", "_"), "UTF-8")
            val url = URL("https://ru.wikipedia.org/api/rest_v1/page/summary/$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "AudiobookPlayerApp/1.0")
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val extract = JSONObject(body).optString("extract", "").trim()
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

        bookTitleView.setTextColor(t.textPrimary)
        coverView.setBackgroundColor(t.surface)
        speedBtn.setTextColor(t.textPrimary)
        back15Btn.setTextColor(t.textPrimary)
        fwd30Btn.setTextColor(t.textPrimary)
        playPauseBtn.setColorFilter(t.accentText, PorterDuff.Mode.SRC_IN)
        playPauseBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(t.accentBg)
        }
        chapterBtn.setTextColor(t.textPrimary)
        elapsedTimeView.setTextColor(t.textSecondary)
        remainingTimeView.setTextColor(t.textSecondary)
        seekBar.progressTintList = ColorStateList.valueOf(t.accentBg)
        seekBar.thumbTintList = ColorStateList.valueOf(t.accentBg)
        updateTimerButton()

        libraryStatusView.setTextColor(t.textSecondary)

        settingsTitleView.setTextColor(t.textPrimary)
        themeSectionLabel.setTextColor(t.textSecondary)
        badgeSectionLabel.setTextColor(t.textSecondary)
        rebuildThemeButtons()
        rebuildBadgeButtons()

        bottomNavBar.setBackgroundColor(t.navBg)
        updateNavColors()
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
                    setColor(themeOption.accentBg)
                    setStroke(dp(2), themeOption.bg)
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
        settingsContainer.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        updateNavColors()
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
            timerBtn.setTextColor(currentTheme.textPrimary)
        } else {
            val leftMin = ((sleepTimerEndMs - System.currentTimeMillis()) / 60_000L) + 1
            timerBtn.text = "${leftMin}м"
            timerBtn.setTextColor(currentTheme.timerActive)
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

    override fun onStart() {
        super.onStart()
        ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java))
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
        }

        val titleTv = TextView(this).apply {
            text = if (isActive) "▶ ${book.title}" else book.title
            textSize = 16f
            setTextColor(t.textPrimary)
        }

        val progressText = if (savedIdx >= 0) {
            "Глава ${savedIdx + 1} • ${formatTime(savedPos)}"
        } else {
            "Не начата"
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
            .apply()

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
        editor.apply()
    }

    private fun restoreSavedBookIfEmpty() {
        val player = controller ?: return
        if (player.mediaItemCount > 0 || isLoadingFolder) return
        val prefs = getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("folder_uri", null) ?: return
        val index = prefs.getInt("idx_$uriStr", prefs.getInt("last_index", 0))
        val pos = prefs.getLong("pos_$uriStr", prefs.getLong("last_pos", 0L))
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

                runOnUiThread {
                    bookTitleView.text = bookName
                    getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("last_book_title", bookName)
                        .apply()

                    if (files.isEmpty()) {
                        chapterBtn.text = "В этой папке нет аудиофайлов"
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
                    val activeController = controller
                    if (activeController != null) {
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
        val itemMeta = player.currentMediaItem?.mediaMetadata
        if (itemMeta != null) {
            bookTitleView.text = itemMeta.artist ?: "Аудиокнига"
            val chapterName = itemMeta.title ?: "Глава ${player.currentMediaItemIndex + 1}"
            chapterBtn.text = "☰  $chapterName"
        }

        val artBytes = player.mediaMetadata.artworkData
        val artHash = artBytes?.contentHashCode() ?: 0
        if (artHash != lastArtworkHash) {
            lastArtworkHash = artHash
            if (artBytes != null) {
                val bmp = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                if (bmp != null) coverView.setImageBitmap(bmp)
            } else {
                coverView.setImageResource(android.R.drawable.ic_media_play)
            }
        }

        playPauseBtn.setImageResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

        val dur = player.duration.coerceAtLeast(0)
        val pos = player.currentPosition.coerceAtLeast(0)
        if (!isSeeking) {
            seekBar.max = dur.toInt()
            seekBar.progress = pos.toInt()
            elapsedTimeView.text = formatTime(pos)
            remainingTimeView.text = "-${formatTime((dur - pos).coerceAtLeast(0))}"
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
