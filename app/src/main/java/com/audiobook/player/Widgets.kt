package com.audiobook.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import java.io.File

const val ACTION_PLAY_PAUSE = "com.audiobook.player.ACTION_PLAY_PAUSE"
const val ACTION_FORWARD = "com.audiobook.player.ACTION_FORWARD"
const val ACTION_REWIND = "com.audiobook.player.ACTION_REWIND"
const val ACTION_UPDATE_WIDGET = "com.audiobook.player.ACTION_UPDATE_WIDGET"

private fun dispatchToService(context: Context, action: String?) {
    if (action == null) return
    val serviceIntent = Intent(context, PlaybackService::class.java).apply {
        this.action = action
    }
    try {
        context.startService(serviceIntent)
    } catch (_: Exception) {
    }
}

private fun applyCover(context: Context, views: RemoteViews) {
    val coverFile = File(context.cacheDir, "widget_cover.jpg")
    if (coverFile.exists()) {
        val bmp = BitmapFactory.decodeFile(coverFile.absolutePath)
        if (bmp != null) {
            views.setImageViewBitmap(R.id.widget_cover, bmp)
            return
        }
    }
    views.setImageViewResource(R.id.widget_cover, android.R.drawable.ic_media_play)
}

class GridWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateGridWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE, ACTION_FORWARD, ACTION_REWIND -> dispatchToService(context, intent.action)
            ACTION_UPDATE_WIDGET -> {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, GridWidgetProvider::class.java))
                onUpdate(context, mgr, ids)
            }
        }
    }
}

class BarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateBarWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE, ACTION_FORWARD, ACTION_REWIND -> dispatchToService(context, intent.action)
            ACTION_UPDATE_WIDGET -> {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, BarWidgetProvider::class.java))
                onUpdate(context, mgr, ids)
            }
        }
    }
}

private fun updateGridWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
    val prefs = context.getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
    val isPlaying = prefs.getBoolean("is_playing", false)

    val views = RemoteViews(context.packageName, R.layout.widget_grid)
    views.setImageViewResource(R.id.widget_play_pause, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    applyCover(context, views)

    views.setOnClickPendingIntent(
        R.id.widget_play_pause,
        PendingIntent.getBroadcast(
            context,
            10,
            Intent(context, GridWidgetProvider::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    )

    views.setOnClickPendingIntent(
        R.id.widget_rewind,
        PendingIntent.getBroadcast(
            context,
            11,
            Intent(context, GridWidgetProvider::class.java).apply { action = ACTION_REWIND },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    )

    views.setOnClickPendingIntent(
        R.id.widget_forward,
        PendingIntent.getBroadcast(
            context,
            12,
            Intent(context, GridWidgetProvider::class.java).apply { action = ACTION_FORWARD },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    )

    val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (openAppIntent != null) {
        views.setOnClickPendingIntent(
            R.id.widget_cover,
            PendingIntent.getActivity(
                context,
                13,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
    }

    appWidgetManager.updateAppWidget(widgetId, views)
}

private fun updateBarWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
    val prefs = context.getSharedPreferences("audiobook_prefs", Context.MODE_PRIVATE)
    val isPlaying = prefs.getBoolean("is_playing", false)
    val bookTitle = prefs.getString("last_book_title", "Аудиокниги") ?: "Аудиокниги"
    val chapterTitle = prefs.getString("last_chapter_title", "Нажмите, чтобы открыть") ?: "Нажмите, чтобы открыть"

    val views = RemoteViews(context.packageName, R.layout.widget_bar)
    views.setTextViewText(R.id.widget_book, bookTitle)
    views.setTextViewText(R.id.widget_chapter, chapterTitle)
    views.setImageViewResource(R.id.widget_play_pause, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    applyCover(context, views)

    views.setOnClickPendingIntent(
        R.id.widget_play_pause,
        PendingIntent.getBroadcast(
            context,
            20,
            Intent(context, BarWidgetProvider::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    )

    val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (openAppIntent != null) {
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                21,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
    }

    appWidgetManager.updateAppWidget(widgetId, views)
}
