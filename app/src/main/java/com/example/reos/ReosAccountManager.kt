package com.example.reos

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ReosAccountManager {
  private const val PREFS_NAME = "reos_account_prefs"
  private const val KEY_LOGGED_IN = "is_logged_in"
  private const val KEY_USERNAME = "logged_username"
  private const val KEY_PICTURE = "logged_picture"
  const val USERS_JSON_PAGEKITE = "https://reostech.pagekite.me/users.json"
  const val USERS_JSON_FIREBASE = "https://reostech.web.app/users.json"

  // Render endpoint for users.json verification when the web app is on https://reostech.web.app
  // Dynamically configurable when custom Render URL is provided
  @Volatile
  var RENDER_USERS_URL: String = "https://reostech.onrender.com/users.json"

  // Tracking the active URL on Reos Screen to dynamically adapt verification endpoint
  @Volatile
  var activeWebScreenUrl: String = "https://reostech.pagekite.me"

  fun setCustomRenderUrl(rawUrl: String) {
    val clean = rawUrl.trim()
    RENDER_USERS_URL = if (clean.endsWith("/users.json")) {
      clean
    } else {
      clean.trimEnd('/') + "/users.json"
    }
  }

  fun isWebScreenOnWebApp(): Boolean {
    return activeWebScreenUrl.contains("reostech.web.app", ignoreCase = true)
  }

  fun isLoggedIn(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_LOGGED_IN, false) && !prefs.getString(KEY_USERNAME, null).isNullOrBlank()
  }

  fun getLoggedInUsername(context: Context): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_USERNAME, null)
  }

  fun getLoggedInPicture(context: Context): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_PICTURE, null)
  }

  fun saveAccount(context: Context, username: String, picture: String? = null) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
      .putBoolean(KEY_LOGGED_IN, true)
      .putString(KEY_USERNAME, username)
      .putString(KEY_PICTURE, picture)
      .apply()
    com.example.firebase.FirebaseManager.triggerSilentSync(context, username = username)
  }

  fun signOut(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().clear().apply()
    com.example.firebase.FirebaseManager.triggerSilentSync(context, username = "")
  }

  /**
   * Appends ?user=username to the base URL if the user is authenticated in Reos Account.
   * Seamlessly and reliably handles existing parameters, fragments, and duplicates.
   * Example: https://reostech.pagekite.me/emails.html -> https://reostech.pagekite.me/emails.html?user=actual_username
   */
  fun formatAppUrl(context: Context, baseUrl: String): String {
    val username = getLoggedInUsername(context)
    if (username.isNullOrBlank() || baseUrl.isBlank()) {
      return baseUrl
    }
    val trimmed = baseUrl.trim()
    if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
      return trimmed
    }
    return try {
      val uri = android.net.Uri.parse(trimmed)
      val existingUser = uri.getQueryParameter("user")
      if (existingUser == username) {
        return trimmed
      }
      val builder = uri.buildUpon()
      builder.clearQuery()
      for (paramName in uri.queryParameterNames) {
        if (!paramName.equals("user", ignoreCase = true)) {
          for (paramVal in uri.getQueryParameters(paramName)) {
            builder.appendQueryParameter(paramName, paramVal)
          }
        }
      }
      builder.appendQueryParameter("user", username)
      builder.build().toString()
    } catch (_: Exception) {
      val encodedUser = try {
        URLEncoder.encode(username, "UTF-8")
      } catch (_: Exception) {
        username
      }
      if (trimmed.contains("user=")) {
        trimmed.replace(Regex("([?&])user=[^&]*"), "$1user=$encodedUser")
      } else if (trimmed.contains("?")) {
        "$trimmed&user=$encodedUser"
      } else {
        "$trimmed?user=$encodedUser"
      }
    }
  }

  /**
   * Securely validates credentials against https://reostech.pagekite.me/users.json.
   * Handles string passwords (e.g. "Reos2026": "reos2026@reo18") as well as
   * object payloads (e.g. "TEST": {"password": "1234", "picture": "..."}).
   */
  suspend fun authenticate(context: Context, inputUser: String, inputPass: String): Result<String> {
    return withContext(Dispatchers.IO) {
      var connection: HttpURLConnection? = null
      try {
        val trimmedUser = inputUser.trim()
        if (trimmedUser.isEmpty()) {
          return@withContext Result.failure(IllegalArgumentException("Please enter your Reos username."))
        }
        if (inputPass.isEmpty()) {
          return@withContext Result.failure(IllegalArgumentException("Please enter your password."))
        }

        // When the URL in Reos screen is https://reostech.web.app, verify against Render
        val urlsToTry = if (isWebScreenOnWebApp()) {
          listOf(RENDER_USERS_URL, USERS_JSON_PAGEKITE, USERS_JSON_FIREBASE)
        } else {
          listOf(USERS_JSON_PAGEKITE, RENDER_USERS_URL, USERS_JSON_FIREBASE)
        }
        var responseText: String? = null
        var lastErr: Exception? = null

        for (endpoint in urlsToTry) {
          try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
              requestMethod = "GET"
              connectTimeout = 6000
              readTimeout = 6000
              setRequestProperty("Accept", "application/json")
              setRequestProperty("User-Agent", "ReosAndroidApp/2.0")
            }
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
              responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
              break
            } else {
              lastErr = Exception("Server returned HTTP $responseCode")
            }
          } catch (e: Exception) {
            lastErr = e
          } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
            connection = null
          }
        }

        if (responseText == null) {
          return@withContext Result.failure(Exception("Unable to reach authentication server: ${lastErr?.message}"))
        }

        val jsonObject = try {
          JSONObject(responseText)
        } catch (e: Exception) {
          return@withContext Result.failure(Exception("Invalid data received from authentication server."))
        }

        var matchedKey: String? = null
        var matchedPic: String? = null

        val keys = jsonObject.keys()
        while (keys.hasNext()) {
          val key = keys.next()
          // Check if username matches exactly or case-insensitively
          if (key.equals(trimmedUser, ignoreCase = false) || key.equals(trimmedUser, ignoreCase = true)) {
            val userVal = jsonObject.get(key)
            var actualPass: String? = null
            var pictureUrl: String? = null

            if (userVal is String) {
              actualPass = userVal
            } else if (userVal is JSONObject) {
              if (userVal.has("password")) {
                actualPass = userVal.optString("password")
              }
              if (userVal.has("picture")) {
                pictureUrl = userVal.optString("picture")
              }
            }

            // Check if password matches exactly
            if (actualPass != null && actualPass == inputPass) {
              matchedKey = key
              matchedPic = pictureUrl
              break
            }
          }
        }

        if (matchedKey != null) {
          saveAccount(context, matchedKey, matchedPic)
          Result.success(matchedKey)
        } else {
          Result.failure(Exception("Invalid username or password. Please verify your credentials."))
        }
      } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "Network error while connecting to authentication service."))
      } finally {
        connection?.disconnect()
      }
    }
  }
}
