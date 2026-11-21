package com.audiobook.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

const val ACTION_PLAY_PAUSE = "com.audiobook.player.ACTION_PLAY_PAUSE"
const val ACTION_FORWARD = "com.audiobook.player.ACTION_FORWARD"
const val ACTION_REWIND = "com.audiobook.player.ACTION_REWIND"
const val ACTION_UPDATE_WIDGET = "com.audiobook.player.ACTION_UPDATE_WIDGET"

private fun withSessionController(context: Context, block: (MediaController) -> Unit) {
    val serviceIntent = Intent(context, PlaybackService::class.java)
    ContextCompat.startForegroundService(context, serviceIntent)

    val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val future = MediaController.Builder(context, token).buildAsync()
    future.addListener({
        try {
            val controller = future.get()
            block(controller)
            MediaController.releaseFuture(future)
        } catch (_: Exception) {
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun handleWidgetAction(context: Context, action: String?) {
    when (action) {
        ACTION_PLAY_PAUSE -> withSessionController(context) { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
        ACTION_FORWARD -> withSessionController(context) { controller ->
            val target = (controller.currentPosition + 30_000L)
                .coerceAtMost(controller.duration.coerceAtLeast(0L))
            controller.seekTo(target)
        }
        ACTION_REWIND -> withSessionController(context) { controller ->
            val target = (controller.currentPosition - 15_000L).coerceAtLeast(0L)
            controller.seekTo(target)
        }
    }
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
            ACTION_PLAY_PAUSE, ACTION_FORWARD, ACTION_REWIND -> handleWidgetAction(context, intent.action)
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
            ACTION_PLAY_PAUSE, ACTION_FORWARD, ACTION_REWIND -> handleWidgetAction(context, intent.action)
            ACTION_UPDATE_WIDGET -> {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, BarWidgetProvider::class.java))
                onUpdate(context, mgr, ids)
            }
        }
    }
}

private fun updateGridWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
    val views = RemoteViews(context.packageName, R.layout.widget_grid)

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
    val bookTitle = prefs.getString("last_book_title", "Аудиокниги") ?: "Аудиокниги"

    val views = RemoteViews(context.packageName, R.layout.widget_bar)
    views.setTextViewText(R.id.widget_book, bookTitle)

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
