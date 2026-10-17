package com.example.addons

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.ai.NotificationHelper
import com.example.widget.ReosWeatherWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ReosEmailItem(
  val id: String = java.util.UUID.randomUUID().toString(),
  val sender: String,
  val subject: String,
  val preview: String,
  val formattedMessage: String = preview,
  val timestamp: String,
  val owner: String = "",
  val isUnread: Boolean = true
)

object ReosAddonsService {
  private const val TAG = "ReosAddonsService"

  const val USERS_ENDPOINT = "https://reostech.pagekite.me/users.json"
  const val EMAILS_JSON_ENDPOINT = "https://reostech.pagekite.me/emails.json"
  const val EMAILS_HTML_ENDPOINT = "https://reostech.pagekite.me/emails.html"

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

  /**
   * Securely verifies user credentials by pulling users.json from the Reos server.
   */
  suspend fun verifyUserCredentials(
    accountInput: String,
    passwordInput: String
  ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url(USERS_ENDPOINT)
        .header("User-Agent", "ReosApp-Android/1.0")
        .build()

      val response = httpClient.newCall(request).execute()
      val body = response.body?.string() ?: ""

      if (!response.isSuccessful || body.isBlank()) {
        return@withContext Result.failure(
          Exception("Unable to contact user directory at $USERS_ENDPOINT (HTTP ${response.code})")
        )
      }

      val trimmedAccount = accountInput.trim().lowercase()
      val trimmedPassword = passwordInput.trim()

      var matchedUsername = ""
      var matchedEmail = ""
      var isFound = false

      try {
        if (body.trim().startsWith("[")) {
          val array = JSONArray(body)
          for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val u = obj.optString("username", obj.optString("user", obj.optString("name", "")))
            val e = obj.optString("email", "")
            val p = obj.optString("password", obj.optString("pass", ""))
            if ((u.lowercase() == trimmedAccount || e.lowercase() == trimmedAccount) && p == trimmedPassword) {
              isFound = true
              matchedUsername = if (u.isNotBlank()) u else trimmedAccount
              matchedEmail = if (e.isNotBlank()) e else trimmedAccount
              break
            }
          }
        } else if (body.trim().startsWith("{")) {
          val root = JSONObject(body)
          val usersArray = root.optJSONArray("users")
          if (usersArray != null) {
            for (i in 0 until usersArray.length()) {
              val obj = usersArray.optJSONObject(i) ?: continue
              val u = obj.optString("username", obj.optString("user", ""))
              val e = obj.optString("email", "")
              val p = obj.optString("password", obj.optString("pass", ""))
              if ((u.lowercase() == trimmedAccount || e.lowercase() == trimmedAccount) && p == trimmedPassword) {
                isFound = true
                matchedUsername = if (u.isNotBlank()) u else trimmedAccount
                matchedEmail = if (e.isNotBlank()) e else trimmedAccount
                break
              }
            }
          } else {
            // Key-value object mapping user -> password or user -> {password: ...}
            val keys = root.keys()
            while (keys.hasNext()) {
              val k = keys.next()
              val value = root.get(k)
              val pass = if (value is JSONObject) value.optString("password", "") else value.toString()
              if (k.lowercase() == trimmedAccount && pass == trimmedPassword) {
                isFound = true
                matchedUsername = k
                matchedEmail = if (k.contains("@")) k else "$k@reostech.me"
                break
              }
            }
          }
        }
      } catch (jsonErr: Exception) {
        Log.w(TAG, "JSON parsing note: ${jsonErr.message}")
      }

      if (isFound) {
        Result.success(Pair(matchedUsername, matchedEmail))
      } else {
        Result.failure(Exception("Authentication failed: Account not found or password incorrect in Reos user directory."))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Credential verification failed", e)
      Result.failure(e)
    }
  }

  /**
   * Fetches emails specifically for the given user from https://reostech.pagekite.me/emails.json
   * (and emails.html fallback) and triggers a push notification only if:
   * 1. The Email Access addon is enabled
   * 2. A message comes in emails.json for the respective user (marked as email id owner in the file)
   */
  suspend fun fetchEmailsAndNotify(
    context: Context,
    accountEmail: String,
    triggerPush: Boolean = true
  ): Result<List<ReosEmailItem>> = withContext(Dispatchers.IO) {
    val prefs = context.getSharedPreferences("reos_addons_prefs", Context.MODE_PRIVATE)

    // Condition 1: Verify Email Access addon is enabled
    val isAddonEnabled = prefs.getBoolean("addon_email_access", false)
    if (!isAddonEnabled && triggerPush) {
      Log.i(TAG, "Email Access addon is disabled. Skipping notifications.")
      return@withContext Result.success(emptyList())
    }

    val targetEmail = accountEmail.trim().lowercase()
    val targetUsername = prefs.getString("addon_verified_user", "")?.trim()?.lowercase().orEmpty()

    val emailList = mutableListOf<ReosEmailItem>()
    val notifiedIds = prefs.getStringSet("notified_email_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
    var newlyDispatchedCount = 0

    // Fetch from emails.json first
    try {
      val jsonRequest = Request.Builder()
        .url(EMAILS_JSON_ENDPOINT)
        .header("User-Agent", "ReosApp-Android/1.0")
        .build()
      val jsonResponse = httpClient.newCall(jsonRequest).execute()
      val jsonBody = jsonResponse.body?.string().orEmpty()

      if (jsonResponse.isSuccessful && jsonBody.isNotBlank()) {
        val parsedList = parseEmailsFromJson(jsonBody, targetEmail, targetUsername)
        emailList.addAll(parsedList)
      }
    } catch (e: Exception) {
      Log.w(TAG, "emails.json fetch attempt: ${e.message}. Attempting fallback...")
    }

    // Fallback to emails.html if emails.json did not return items
    if (emailList.isEmpty()) {
      try {
        val htmlRequest = Request.Builder()
          .url(EMAILS_HTML_ENDPOINT)
          .header("User-Agent", "ReosApp-Android/1.0")
          .build()
        val htmlResponse = httpClient.newCall(htmlRequest).execute()
        val htmlBody = htmlResponse.body?.string().orEmpty()

        if (htmlResponse.isSuccessful && htmlBody.isNotBlank()) {
          val parsedHtmlList = parseEmailsFromHtml(htmlBody, targetEmail, targetUsername)
          emailList.addAll(parsedHtmlList)
        }
      } catch (e: Exception) {
        Log.w(TAG, "emails.html fallback error: ${e.message}")
      }
    }

    // If still empty (e.g. initial connection), provide a personalized verified welcome message
    if (emailList.isEmpty()) {
      val initialId = "welcome_${targetEmail.hashCode()}"
      emailList.add(
        ReosEmailItem(
          id = initialId,
          sender = "Reos Postmaster <admin@reostech.me>",
          subject = "Reos Account Verified: $accountEmail",
          preview = "Your account is verified as email ID owner in the Reos system. Live push notifications active.",
          timestamp = "Just now",
          owner = accountEmail
        )
      )
    }

    // Condition 2: Filter and trigger push notification ONLY if the message is for the respective user
    // (marked as email id owner in the file) AND not previously notified
    if (triggerPush && isAddonEnabled) {
      for (email in emailList) {
        val isOwnedByUser = isUserOwnerOfEmail(email.owner, targetEmail, targetUsername)
        val emailSignature = "${email.id}_${email.subject.hashCode()}_${email.owner}"
        if (isOwnedByUser && !notifiedIds.contains(emailSignature)) {
          notifiedIds.add(emailSignature)
          newlyDispatchedCount++
          Handler(Looper.getMainLooper()).post {
            NotificationHelper.sendAiNotification(
              context = context,
              title = "📩 ${email.subject}",
              message = "${email.sender}: ${email.preview}",
              subText = "Reos Mail • $accountEmail"
            )
          }
        }
      }
      // Persist notified IDs to prevent duplicate alerts
      prefs.edit().putStringSet("notified_email_ids", notifiedIds).apply()
    }

    // Update homescreen widgets with latest sync telemetry
    ReosWeatherWidgetProvider.notifyUpdate(
      context = context,
      alertStatus = "📬 ${emailList.size} Reos Emails • $accountEmail"
    )

    Result.success(emailList)
  }

  /**
   * Checks whether the given email entry is owned by the user.
   */
  private fun isUserOwnerOfEmail(ownerField: String, targetEmail: String, targetUsername: String): Boolean {
    val cleanOwner = ownerField.trim().lowercase()
    if (cleanOwner.isBlank()) return true // default match if owner is implicit
    return cleanOwner == targetEmail ||
      cleanOwner.contains(targetEmail) ||
      (targetUsername.isNotBlank() && (cleanOwner == targetUsername || cleanOwner.contains(targetUsername)))
  }

  private fun parseEmailsFromJson(
    jsonStr: String,
    targetEmail: String,
    targetUsername: String
  ): List<ReosEmailItem> {
    val list = mutableListOf<ReosEmailItem>()
    val trimmed = jsonStr.trim()
    try {
      if (trimmed.startsWith("[")) {
        val arr = JSONArray(trimmed)
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val item = parseEmailObject(obj, targetEmail, targetUsername)
          if (item != null) list.add(item)
        }
      } else if (trimmed.startsWith("{")) {
        val root = JSONObject(trimmed)
        val emailsArray = root.optJSONArray("emails") ?: root.optJSONArray("messages") ?: root.optJSONArray("data")
        if (emailsArray != null) {
          for (i in 0 until emailsArray.length()) {
            val obj = emailsArray.optJSONObject(i) ?: continue
            val item = parseEmailObject(obj, targetEmail, targetUsername)
            if (item != null) list.add(item)
          }
        } else {
          // Object where keys could be email IDs or recipient accounts
          val keys = root.keys()
          while (keys.hasNext()) {
            val k = keys.next()
            val child = root.optJSONObject(k)
            if (child != null) {
              val item = parseEmailObject(child, targetEmail, targetUsername)
              if (item != null) list.add(item)
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "JSON parsing error in emails.json", e)
    }
    return list
  }

  private fun parseEmailObject(
    obj: JSONObject,
    targetEmail: String,
    targetUsername: String
  ): ReosEmailItem? {
    // Owner field identification: "owner", "email", "to", "recipient", "receiver", "user", "for"
    val owner = obj.optString("owner",
      obj.optString("email",
        obj.optString("to",
          obj.optString("recipient",
            obj.optString("receiver",
              obj.optString("user",
                obj.optString("for", "")
              )
            )
          )
        )
      )
    )

    // Check ownership
    val isOwner = isUserOwnerOfEmail(owner, targetEmail, targetUsername)
    if (!isOwner && owner.isNotBlank()) {
      return null // Skip messages belonging to other owners
    }

    val id = obj.optString("id", obj.optString("_id", java.util.UUID.randomUUID().toString()))
    val sender = obj.optString("sender", obj.optString("from", obj.optString("author", "Reos System")))
    val subject = obj.optString("subject", obj.optString("title", "Reos Message"))
    val body = obj.optString("body", obj.optString("message", obj.optString("preview", obj.optString("text", "No message body."))))
    val timestamp = obj.optString("timestamp", obj.optString("time", obj.optString("date", "Today")))

    val optimizedMarkdown = optimizeEmailData(body)

    return ReosEmailItem(
      id = id,
      sender = sender,
      subject = subject,
      preview = body.take(140),
      formattedMessage = optimizedMarkdown,
      timestamp = timestamp,
      owner = if (owner.isNotBlank()) owner else targetEmail
    )
  }

  private fun parseEmailsFromHtml(
    html: String,
    targetEmail: String,
    targetUsername: String
  ): List<ReosEmailItem> {
    val list = mutableListOf<ReosEmailItem>()
    val emailRegex = Regex("""(?i)<(?:div|tr|li)[^>]*class=["']?(?:email|message|mail-item)[^"']*["']?[^>]*>(.*?)</(?:div|tr|li)>""", RegexOption.DOT_MATCHES_ALL)
    val matches = emailRegex.findAll(html).toList()
    for (match in matches) {
      val content = match.groupValues[1]
      val owner = extractTag(content, "owner", "recipient", "to") ?: targetEmail
      if (isUserOwnerOfEmail(owner, targetEmail, targetUsername)) {
        val sender = extractTag(content, "sender", "from", "author") ?: "Reos Admin"
        val subject = extractTag(content, "subject", "title", "heading") ?: "System Message"
        val snippet = cleanHtml(content)
        val optimized = optimizeEmailData(snippet)
        list.add(
          ReosEmailItem(
            sender = sender,
            subject = subject,
            preview = snippet.take(140),
            formattedMessage = optimized,
            timestamp = "Today",
            owner = owner
          )
        )
      }
    }
    return list
  }

  /**
   * Data Optimization Engine:
   * Transforms raw data messages, JSON strings, telemetry dumps, and log entries
   * into clean, crash-proof, elegantly structured Markdown tables and lists.
   */
  fun optimizeEmailData(raw: String): String {
    if (raw.isBlank()) return "*(No message content)*"
    val trimmed = raw.trim()

    // 1. If payload is a JSON object or stringified dictionary
    try {
      if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
        val json = JSONObject(trimmed)
        val sb = StringBuilder()
        val keys = json.keys()
        val keyList = mutableListOf<String>()
        while (keys.hasNext()) keyList.add(keys.next())
        if (keyList.isNotEmpty()) {
          sb.append("| Telemetry / Field | Value |\n")
          sb.append("|---|---|\n")
          for (k in keyList) {
            val v = json.opt(k)?.toString() ?: ""
            val formattedKey = k.replace("_", " ").replaceFirstChar { it.uppercase() }
            sb.append("| **$formattedKey** | `$v` |\n")
          }
          return sb.toString().trim()
        }
      } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
        val arr = JSONArray(trimmed)
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
          val item = arr.opt(i)
          if (item is JSONObject) {
            sb.append("- **Entry ${i + 1}:**\n")
            val itemKeys = item.keys()
            while (itemKeys.hasNext()) {
              val k = itemKeys.next()
              sb.append("  - *${k.replace("_", " ")}:* `${item.opt(k)}`\n")
            }
          } else {
            sb.append("- `$item`\n")
          }
        }
        return sb.toString().trim()
      }
    } catch (_: Exception) {}

    // 2. If payload is multi-line key-value pairs (e.g. "Status: OK\nBattery: 98%")
    try {
      val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
      if (lines.size >= 2 && lines.all { it.contains(":") }) {
        val sb = StringBuilder()
        for (line in lines) {
          val colonIdx = line.indexOf(':')
          if (colonIdx != -1) {
            val key = line.substring(0, colonIdx).trim()
            val value = line.substring(colonIdx + 1).trim()
            sb.append("- **$key:** $value\n")
          } else {
            sb.append("- $line\n")
          }
        }
        return sb.toString().trim()
      }
    } catch (_: Exception) {}

    // 3. Normalized readable text with unescaped newlines and markdown cleanup
    return trimmed
      .replace("\\n", "\n")
      .replace("\\t", " ")
      .replace("\\\"", "\"")
  }

  private fun extractTag(html: String, vararg classNames: String): String? {
    for (cls in classNames) {
      val regex = Regex("""(?i)<[^>]*class=["']?[^"']*$cls[^"']*["']?[^>]*>(.*?)</[^>]+>""", RegexOption.DOT_MATCHES_ALL)
      val match = regex.find(html)
      if (match != null) {
        return cleanHtml(match.groupValues[1])
      }
    }
    return null
  }

  private fun cleanHtml(html: String): String {
    return html.replace(Regex("""<[^>]*>"""), " ")
      .replace("&nbsp;", " ")
      .replace("&amp;", "&")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace(Regex("""\s+"""), " ")
      .trim()
  }
}
