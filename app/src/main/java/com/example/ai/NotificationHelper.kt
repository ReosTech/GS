package com.example.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.example.MainActivity

object NotificationHelper {
  const val CHANNEL_AI_FEATURES = "ai_features_channel"
  const val CHANNEL_AI_WEATHER = "ai_weather_channel"
  const val CHANNEL_AI_GEN = "ai_generation_channel"

  /**
   * Converts standard Markdown syntax (bold, italic, headers, inline code, bullets, links)
   * into rich Spanned text for NotificationCompat styling.
   */
  fun renderMarkdownToSpanned(markdown: String): CharSequence {
    if (markdown.isBlank()) return ""

    var html = markdown.replace("\r\n", "\n")

    // Escape raw HTML entities to prevent malformed tags while preserving structure
    html = html
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")

    // Headings (e.g. ### Title -> <b>Title</b><br>)
    html = html.replace(Regex("(?m)^#{1,6}\\s+(.+)$"), "<b>$1</b><br>")

    // Bold (**text** or __text__)
    html = html.replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
    html = html.replace(Regex("__(.+?)__"), "<b>$1</b>")

    // Italic (*text* or _text_)
    html = html.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "<i>$1</i>")
    html = html.replace(Regex("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)"), "<i>$1</i>")

    // Inline code (`code` -> <tt><font color="#0284c7">code</font></tt>)
    html = html.replace(Regex("`([^`]+)`"), "<tt><font color=\"#0284c7\">$1</font></tt>")

    // Bullet points (- item or * item -> • item<br>)
    html = html.replace(Regex("(?m)^[\\*\\-]\\s+(.+)$"), "• $1<br>")

    // Numbered lists (1. item -> 1. item<br>)
    html = html.replace(Regex("(?m)^(\\d+)\\.\\s+(.+)$"), "$1. $2<br>")

    // Markdown Links: [title](url) -> <a href="url"><b>title</b></a>
    html = html.replace(Regex("\\[([^\\]]+)\\]\\(([^\\)]+)\\)"), "<a href=\"$2\"><b>$1</b></a>")

    // Blockquotes (> quote)
    html = html.replace(Regex("(?m)^>\\s*(.+)$"), "<i>\"$1\"</i><br>")

    // Newlines: double break -> <br><br>, single -> <br>
    html = html.replace("\n\n", "<br><br>")
    html = html.replace("\n", "<br>")

    return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
  }

  fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

      // AI Features Channel
      val generalChannel = NotificationChannel(
        CHANNEL_AI_FEATURES,
        "AI Features & Recommendations",
        NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = "Push notifications for AI updates, chatbot answers, and smart advice"
        enableLights(true)
        enableVibration(true)
      }

      // Weather Alert Channel
      val weatherChannel = NotificationChannel(
        CHANNEL_AI_WEATHER,
        "AI Weather Briefings & Live Alerts",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Live local weather updates and AI meteorologist recommendations"
        enableLights(true)
        enableVibration(true)
      }

      // AI Generation Channel
      val genChannel = NotificationChannel(
        CHANNEL_AI_GEN,
        "AI Creations & Studio Notifications",
        NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = "Notifications when AI image, video, music, or maps are ready"
      }

      notificationManager.createNotificationChannels(listOf(generalChannel, weatherChannel, genChannel))
    }
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
  }

  fun sendAiNotification(
    context: Context,
    title: String,
    message: String,
    channelId: String = CHANNEL_AI_FEATURES,
    notificationId: Int = (System.currentTimeMillis() % 100000).toInt(),
    subText: String? = null,
    targetTab: String = "ai",
    aiSubFeature: String = "weather"
  ) {
    if (!hasNotificationPermission(context)) {
      return
    }
    createNotificationChannels(context)

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra("destination_tab", targetTab)
      putExtra("ai_feature", aiSubFeature)
    }

    val pendingIntent = PendingIntent.getActivity(
      context,
      notificationId,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
    )

    // Render markdown to rich spanned CharSequence for title & message
    val renderedTitle = renderMarkdownToSpanned(title)
    val renderedMessage = renderMarkdownToSpanned(message)

    val builder = NotificationCompat.Builder(context, channelId)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(renderedTitle)
      .setContentText(renderedMessage)
      .setStyle(NotificationCompat.BigTextStyle().bigText(renderedMessage))
      .setPriority(
        if (channelId == CHANNEL_AI_WEATHER) NotificationCompat.PRIORITY_HIGH
        else NotificationCompat.PRIORITY_DEFAULT
      )
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)

    if (subText != null) {
      builder.setSubText(subText)
    }

    try {
      NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    } catch (_: SecurityException) {
      // Notification permission not granted
    }
  }

  fun sendWeatherBriefingNotification(
    context: Context,
    cityName: String,
    temp: Double,
    condition: String,
    advice: String? = null
  ) {
    val title = "🌤️ **$cityName**: **${temp.toInt()}°C** *($condition)*"
    val body = if (!advice.isNullOrBlank()) {
      "$advice\n\n- **Live condition**: $condition\n- **Temperature**: ${temp.toInt()}°C\n\n*Tap to open 3D interactive weather map & 7-day forecast.*"
    } else {
      "Current real-time weather in **$cityName** is **${temp.toInt()}°C** with *$condition*.\n\n- Tap for live 3D atmosphere and radar."
    }
    sendAiNotification(
      context = context,
      title = title,
      message = body,
      channelId = CHANNEL_AI_WEATHER,
      notificationId = 2001,
      subText = "AI Weather Briefing",
      targetTab = "ai",
      aiSubFeature = "weather"
    )
  }

  fun sendAiCreationNotification(
    context: Context,
    title: String,
    message: String,
    feature: String
  ) {
    sendAiNotification(
      context = context,
      title = title,
      message = message,
      channelId = CHANNEL_AI_GEN,
      notificationId = (System.currentTimeMillis() % 100000).toInt(),
      subText = "AI Generation",
      targetTab = "ai",
      aiSubFeature = feature
    )
  }
}
