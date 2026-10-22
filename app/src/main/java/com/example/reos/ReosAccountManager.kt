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
  const val USERS_JSON_URL = "https://reostech.pagekite.me/users.json"

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
  }

  fun signOut(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().clear().apply()
  }

  /**
   * Appends ?user=username to the base URL if the user is authenticated in Reos Account.
   * Example: https://reostech.pagekite.me/emails.html?user=TEST
   */
  fun formatAppUrl(context: Context, baseUrl: String): String {
    val username = getLoggedInUsername(context)
    if (username.isNullOrBlank()) {
      return baseUrl
    }
    val encodedUser = try {
      URLEncoder.encode(username, "UTF-8")
    } catch (_: Exception) {
      username
    }
    return if (baseUrl.contains("?")) {
      "$baseUrl&user=$encodedUser"
    } else {
      "$baseUrl?user=$encodedUser"
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

        val url = URL(USERS_JSON_URL)
        connection = (url.openConnection() as HttpURLConnection).apply {
          requestMethod = "GET"
          connectTimeout = 8000
          readTimeout = 8000
          setRequestProperty("Accept", "application/json")
          setRequestProperty("User-Agent", "ReosAndroidApp/2.0")
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
          return@withContext Result.failure(Exception("Unable to reach authentication server (HTTP $responseCode)."))
        }

        val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
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
