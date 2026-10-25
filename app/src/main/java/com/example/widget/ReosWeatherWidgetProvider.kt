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

class ReosWeatherWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    for (widgetId in appWidgetIds) {
      updateAppWidget(context, appWidgetManager, widgetId)
    }
  }

  companion object {
    fun updateAppWidget(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetId: Int,
      cityName: String = "Reos Weather & Alerts",
      temp: String = "22°",
      desc: String = "Clear skies • Light wind",
      alertStatus: String = "Reos Services: Connected & Synced"
    ) {
      try {
        val views = RemoteViews(context.packageName, R.layout.widget_weather_glance)
        views.setTextViewText(R.id.weather_widget_city, cityName)
        views.setTextViewText(R.id.weather_widget_temp, temp)
        views.setTextViewText(R.id.weather_widget_desc, desc)
        views.setTextViewText(R.id.weather_widget_alert, alertStatus)

        // Open Weather tab
        val weatherIntent = Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
          putExtra("destination_tab", "weather")
        }
        val weatherPendingIntent = PendingIntent.getActivity(
          context,
          2001,
          weatherIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.weather_widget_btn_open, weatherPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_weather_root, weatherPendingIntent)

        // Open Reos Portal
        val reosIntent = Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
          putExtra("destination_tab", "reos")
        }
        val reosPendingIntent = PendingIntent.getActivity(
          context,
          2002,
          reosIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.weather_widget_btn_portal, reosPendingIntent)

        // Refresh button
        val syncIntent = Intent(context, ReosWeatherWidgetProvider::class.java).apply {
          action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
          putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        val syncPendingIntent = PendingIntent.getBroadcast(
          context,
          2003,
          syncIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.weather_widget_refresh, syncPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
      } catch (e: Exception) {
        android.util.Log.e("ReosWeatherWidget", "Failed to update weather widget: ${e.message}", e)
      }
    }

    fun notifyUpdate(
      context: Context,
      cityName: String = "Reos Weather & Alerts",
      temp: String = "22°",
      desc: String = "Clear skies • Light wind",
      alertStatus: String = "Reos Services: Connected & Synced"
    ) {
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, ReosWeatherWidgetProvider::class.java))
      for (id in ids) {
        updateAppWidget(context, manager, id, cityName, temp, desc, alertStatus)
      }
    }
  }
}
