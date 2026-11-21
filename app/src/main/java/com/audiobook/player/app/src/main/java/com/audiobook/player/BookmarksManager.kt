package com.audiobook.player

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

data class BookmarkItem(
    val id: Long,
    val chapterIndex: Int,
    val chapterTitle: String,
    val positionMs: Long,
    val note: String,
    val createdAt: Long
)

class BookmarksManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("audiobook_bookmarks", Context.MODE_PRIVATE)

    private fun storageKey(bookUri: String): String = "bm_${bookUri.hashCode()}"

    fun getBookmarks(bookUri: String): MutableList<BookmarkItem> {
        val raw = prefs.getString(storageKey(bookUri), null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<BookmarkItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    BookmarkItem(
                        id = obj.optLong("id", 0L),
                        chapterIndex = obj.optInt("chapterIndex", 0),
                        chapterTitle = obj.optString("chapterTitle", "Глава"),
                        positionMs = obj.optLong("positionMs", 0L),
                        note = obj.optString("note", ""),
                        createdAt = obj.optLong("createdAt", 0L)
                    )
                )
            }
            list.sortedBy { it.createdAt }.toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveBookmarks(bookUri: String, items: List<BookmarkItem>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("chapterIndex", item.chapterIndex)
                    .put("chapterTitle", item.chapterTitle)
                    .put("positionMs", item.positionMs)
                    .put("note", item.note)
                    .put("createdAt", item.createdAt)
            )
        }
        prefs.edit().putString(storageKey(bookUri), arr.toString()).apply()
    }

    fun addBookmark(
        bookUri: String,
        chapterIndex: Int,
        chapterTitle: String,
        positionMs: Long,
        note: String
    ): BookmarkItem {
        val items = getBookmarks(bookUri)
        val now = System.currentTimeMillis()
        val item = BookmarkItem(
            id = now,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            positionMs = positionMs.coerceAtLeast(0L),
            note = note.trim(),
            createdAt = now
        )
        items.add(item)
        saveBookmarks(bookUri, items)
        return item
    }

    fun deleteBookmark(bookUri: String, bookmarkId: Long) {
        val items = getBookmarks(bookUri).filterNot { it.id == bookmarkId }
        saveBookmarks(bookUri, items)
    }

    fun showAddBookmarkDialog(
        bookUri: String,
        chapterIndex: Int,
        chapterTitle: String,
        positionMs: Long,
        theme: AppTheme,
        onAdded: (() -> Unit)? = null
    ) {
        val dp = { v: Int -> (v * context.resources.displayMetrics.density).toInt() }
        val formattedTime = formatTime(positionMs)

        val metaView = TextView(context).apply {
            text = "$chapterTitle  •  $formattedTime"
            textSize = 13f
            setTextColor(theme.textSecondary)
            setPadding(0, 0, 0, dp(10))
        }

        val input = EditText(context).apply {
            hint = "Заметка (необязательно)"
            setTextColor(theme.textPrimary)
            setHintTextColor(theme.textSecondary)
            setSingleLine(false)
            maxLines = 3
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
            addView(metaView)
            addView(input)
        }

        AlertDialog.Builder(context)
            .setTitle("Новая закладка")
            .setView(container)
            .setPositiveButton("Сохранить") { _, _ ->
                addBookmark(
                    bookUri = bookUri,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapterTitle,
                    positionMs = positionMs,
                    note = input.text?.toString() ?: ""
                )
                Toast.makeText(context, "Закладка сохранена ($formattedTime)", Toast.LENGTH_SHORT).show()
                onAdded?.invoke()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun showBookmarksDialog(
        bookUri: String,
        bookTitle: String,
        currentChapterIndex: Int,
        currentChapterTitle: String,
        currentPositionMs: Long,
        theme: AppTheme,
        onJumpTo: (chapterIndex: Int, positionMs: Long) -> Unit
    ) {
        val dp = { v: Int -> (v * context.resources.displayMetrics.density).toInt() }
        val items = getBookmarks(bookUri)

        var dialogRef: AlertDialog? = null

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(8))
        }

        val addBtn = TextView(context).apply {
            text = "+ Добавить закладку (${formatTime(currentPositionMs)})"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(theme.accentText)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(theme.accentBg)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
            setOnClickListener {
                dialogRef?.dismiss()
                showAddBookmarkDialog(
                    bookUri = bookUri,
                    chapterIndex = currentChapterIndex,
                    chapterTitle = currentChapterTitle,
                    positionMs = currentPositionMs,
                    theme = theme
                ) {
                    showBookmarksDialog(
                        bookUri = bookUri,
                        bookTitle = bookTitle,
                        currentChapterIndex = currentChapterIndex,
                        currentChapterTitle = currentChapterTitle,
                        currentPositionMs = currentPositionMs,
                        theme = theme,
                        onJumpTo = onJumpTo
                    )
                }
            }
        }
        root.addView(addBtn)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(320)
            )
        }
        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (items.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "Закладок для этой книги пока нет"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(theme.textSecondary)
                setPadding(dp(8), dp(24), dp(8), dp(24))
            }
            listLayout.addView(emptyTv)
        } else {
            for (bm in items) {
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(if (theme.id == "light") Color.parseColor("#ECEEF2") else Color.parseColor("#22222A"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(8)
                    }
                    setOnClickListener {
                        dialogRef?.dismiss()
                        onJumpTo(bm.chapterIndex, bm.positionMs)
                    }
                    setOnLongClickListener {
                        AlertDialog.Builder(context)
                            .setTitle("Удалить закладку?")
                            .setMessage("${bm.chapterTitle} • ${formatTime(bm.positionMs)}")
                            .setPositiveButton("Удалить") { _, _ ->
                                deleteBookmark(bookUri, bm.id)
                                dialogRef?.dismiss()
                                showBookmarksDialog(
                                    bookUri = bookUri,
                                    bookTitle = bookTitle,
                                    currentChapterIndex = currentChapterIndex,
                                    currentChapterTitle = currentChapterTitle,
                                    currentPositionMs = currentPositionMs,
                                    theme = theme,
                                    onJumpTo = onJumpTo
                                )
                            }
                            .setNegativeButton("Отмена", null)
                            .show()
                        true
                    }
                }

                val timeHeader = TextView(context).apply {
                    text = "${formatTime(bm.positionMs)}  •  ${bm.chapterTitle}"
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(theme.textPrimary)
                }
                card.addView(timeHeader)

                if (bm.note.isNotEmpty()) {
                    val noteTv = TextView(context).apply {
                        text = bm.note
                        textSize = 13f
                        setTextColor(theme.textSecondary)
                        setPadding(0, dp(4), 0, 0)
                    }
                    card.addView(noteTv)
                }

                val hintTv = TextView(context).apply {
                    text = "Нажмите для перехода • Удерживайте для удаления"
                    textSize = 11f
                    setTextColor(theme.textSecondary)
                    setPadding(0, dp(4), 0, 0)
                }
                card.addView(hintTv)

                listLayout.addView(card)
            }
        }

        scroll.addView(listLayout)
        root.addView(scroll)

        dialogRef = AlertDialog.Builder(context)
            .setTitle("Закладки")
            .setView(root)
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun formatTime(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val totalSec = safe / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }
}
