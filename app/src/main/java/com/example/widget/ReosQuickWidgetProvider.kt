package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class ReosQuickWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    for (widgetId in appWidgetIds) {
      updateAppWidget(context, appWidgetManager, widgetId)
    }
  }

  companion object {
    fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
      val views = RemoteViews(context.packageName, R.layout.widget_reos_quick)

      // Intent to open MainActivity directly on Reos Portal tab
      val reosIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("destination_tab", "reos")
      }
      val reosPendingIntent = PendingIntent.getActivity(
        context,
        1001,
        reosIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
      views.setOnClickPendingIntent(R.id.btn_open_reos, reosPendingIntent)
      views.setOnClickPendingIntent(R.id.widget_root, reosPendingIntent)

      // Intent to open Weather tab
      val weatherIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("destination_tab", "weather")
      }
      val weatherPendingIntent = PendingIntent.getActivity(
        context,
        1002,
        weatherIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
      views.setOnClickPendingIntent(R.id.btn_open_weather, weatherPendingIntent)

      appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    fun notifyUpdate(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, ReosQuickWidgetProvider::class.java))
      for (id in ids) {
        updateAppWidget(context, manager, id)
      }
    }
  }
}
