package com.audiobook.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews

class GridWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.updateAll(context)
    }
}

class BarWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.updateAll(context)
    }
}

object WidgetUpdater {
    var lastBook: String = "Аудиокниги"
    var lastChapter: String = "Нажмите, чтобы открыть"
    var lastIsPlaying: Boolean = false
    var lastCover: Bitmap? = null

    fun setArtwork(bytes: ByteArray?) {
        if (bytes == null) {
            lastCover = null
            return
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (opts.outWidth / sample > 256 || opts.outHeight / sample > 256) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        lastCover = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val openAppIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun serviceIntent(action: String, code: Int): PendingIntent {
            val intent = Intent(context, PlaybackService::class.java).apply { this.action = action }
            return PendingIntent.getService(
                context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val playIcon = if (lastIsPlaying) "⏸" else "▶"

        // 1. Обновляем виджет 2x2
        val gridIds = manager.getAppWidgetIds(ComponentName(context, GridWidgetProvider::class.java))
        for (id in gridIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_grid)
            views.setTextViewText(R.id.widget_play_pause, playIcon)
            if (lastCover != null) {
                views.setImageViewBitmap(R.id.widget_cover, lastCover)
            } else {
                views.setImageViewResource(R.id.widget_cover, android.R.drawable.ic_media_play)
            }
            views.setOnClickPendingIntent(R.id.widget_cover, openAppIntent)
            views.setOnClickPendingIntent(R.id.widget_play_pause, serviceIntent(PlaybackService.ACTION_PLAY_PAUSE, 1))
            views.setOnClickPendingIntent(R.id.widget_rewind, serviceIntent(PlaybackService.ACTION_REWIND, 2))
            views.setOnClickPendingIntent(R.id.widget_forward, serviceIntent(PlaybackService.ACTION_FORWARD, 3))
            manager.updateAppWidget(id, views)
        }

        // 2. Обновляем компактный виджет-полоску
        val barIds = manager.getAppWidgetIds(ComponentName(context, BarWidgetProvider::class.java))
        for (id in barIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_bar)
            views.setTextViewText(R.id.widget_book, lastBook)
            views.setTextViewText(R.id.widget_chapter, lastChapter)
            views.setTextViewText(R.id.widget_play_pause, playIcon)
            if (lastCover != null) {
                views.setImageViewBitmap(R.id.widget_cover, lastCover)
            } else {
                views.setImageViewResource(R.id.widget_cover, android.R.drawable.ic_media_play)
            }
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)
            views.setOnClickPendingIntent(R.id.widget_play_pause, serviceIntent(PlaybackService.ACTION_PLAY_PAUSE, 4))
            manager.updateAppWidget(id, views)
        }
    }
}
