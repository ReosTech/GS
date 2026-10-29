package com.example.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.example.reos.ReosAccountManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object FirebaseManager {
  private const val TAG = "FirebaseManager"
  private const val PREFS_NAME = "reos_synced_state"

  const val DEFAULT_USER_EMAIL = "tanushghosh10635@gmail.com"

  fun getFirestore(context: Context): FirebaseFirestore {
    val databaseId = context.getString(R.string.firestore_database_id)
    return Firebase.firestore(databaseId)
  }

  fun getAuth(): FirebaseAuth {
    return Firebase.auth
  }

  fun getCurrentUser(): FirebaseUser? {
    return try {
      Firebase.auth.currentUser
    } catch (_: Exception) {
      null
    }
  }

  // --- Scratchpad Local & Cloud Storage ---
  fun saveScratchpad(context: Context, note: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString("scratchpad_note", note).apply()
    triggerSilentSync(context, scratchpadNote = note)
  }

  fun getScratchpad(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString("scratchpad_note", "") ?: ""
  }

  // --- Web Cache Local & Cloud Storage ---
  fun saveWebCache(context: Context, cacheData: Map<String, Any>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    for ((k, v) in cacheData) {
      when (v) {
        is String -> editor.putString("web_cache_$k", v)
        is Long -> editor.putLong("web_cache_$k", v)
        is Int -> editor.putInt("web_cache_$k", v)
        is Boolean -> editor.putBoolean("web_cache_$k", v)
      }
    }
    editor.apply()
    triggerSilentSync(context, webCache = cacheData)
  }

  fun getWebCache(context: Context): Map<String, Any> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val map = mutableMapOf<String, Any>()
    for ((k, v) in prefs.all) {
      if (k.startsWith("web_cache_") && v != null) {
        map[k.removePrefix("web_cache_")] = v
      }
    }
    return map
  }

  // --- Silent Background Firestore Sync ---
  fun triggerSilentSync(
    context: Context,
    username: String? = null,
    webCache: Map<String, Any>? = null,
    scratchpadNote: String? = null
  ) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val resolvedUsername = username
          ?: ReosAccountManager.getLoggedInUsername(context)
          ?: prefs.getString("reos_username", "")
          ?: ""

        if (resolvedUsername.isNotBlank()) {
          editor.putString("reos_username", resolvedUsername)
        }

        if (scratchpadNote != null) {
          editor.putString("scratchpad_note", scratchpadNote)
        }
        val currentScratchpad = scratchpadNote ?: prefs.getString("scratchpad_note", "") ?: ""

        if (webCache != null) {
          for ((k, v) in webCache) {
            when (v) {
              is String -> editor.putString("web_cache_$k", v)
              is Long -> editor.putLong("web_cache_$k", v)
              is Int -> editor.putInt("web_cache_$k", v)
              is Boolean -> editor.putBoolean("web_cache_$k", v)
            }
          }
        }
        val now = System.currentTimeMillis()
        editor.putLong("last_sync_timestamp", now)
        editor.apply()

        val user = getCurrentUser()
        if (user != null) {
          val db = getFirestore(context)
          val payload = mutableMapOf<String, Any>(
            "updatedAt" to com.google.firebase.Timestamp.now(),
            "email" to (user.email ?: DEFAULT_USER_EMAIL),
            "displayName" to (user.displayName ?: resolvedUsername.ifBlank { "User" })
          )

          if (resolvedUsername.isNotBlank()) {
            payload["username"] = resolvedUsername
            payload["reosUsername"] = resolvedUsername
          }
          if (currentScratchpad.isNotBlank()) {
            payload["scratchpad"] = currentScratchpad
            payload["quickScratchpad"] = currentScratchpad
          }

          val fullWebCache = getWebCache(context).toMutableMap()
          if (webCache != null) {
            fullWebCache.putAll(webCache)
          }
          if (fullWebCache.isNotEmpty()) {
            payload["webCache"] = fullWebCache
          }

          db.collection("users").document(user.uid)
            .set(payload, SetOptions.merge())
            .await()
          Log.d(TAG, "Silent sync to Firestore completed successfully")
        }
      } catch (e: Exception) {
        Log.w(TAG, "Silent sync note: ${e.message}")
      }
    }
  }

  // --- Fetch remote data to hydrate cache & scratchpad on device ---
  fun fetchRemoteUserData(
    context: Context,
    onResult: ((username: String?, scratchpad: String?) -> Unit)? = null
  ) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val user = getCurrentUser() ?: return@launch
        val db = getFirestore(context)
        val doc = db.collection("users").document(user.uid).get().await()
        if (doc.exists()) {
          val remoteUsername = doc.getString("username") ?: doc.getString("reosUsername")
          val remoteScratchpad = doc.getString("scratchpad") ?: doc.getString("quickScratchpad")
          val remoteWebCache = doc.get("webCache") as? Map<*, *>

          val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
          val editor = prefs.edit()

          if (!remoteUsername.isNullOrBlank()) {
            editor.putString("reos_username", remoteUsername)
            if (!ReosAccountManager.isLoggedIn(context)) {
              ReosAccountManager.saveAccount(context, remoteUsername, null)
            }
          }
          if (!remoteScratchpad.isNullOrBlank()) {
            editor.putString("scratchpad_note", remoteScratchpad)
          }
          if (remoteWebCache != null) {
            for ((k, v) in remoteWebCache) {
              val key = k.toString()
              when (v) {
                is String -> editor.putString("web_cache_$key", v)
                is Long -> editor.putLong("web_cache_$key", v)
                is Number -> editor.putLong("web_cache_$key", v.toLong())
                is Boolean -> editor.putBoolean("web_cache_$key", v)
              }
            }
          }
          editor.apply()
          withContext(Dispatchers.Main) {
            onResult?.invoke(remoteUsername, remoteScratchpad)
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "fetchRemoteUserData note: ${e.message}")
      }
    }
  }

  suspend fun syncUserPreferences(context: Context, settings: Map<String, Any>): Result<Unit> {
    return syncEverything(context, settings)
  }

  suspend fun syncEverything(
    context: Context,
    extraData: Map<String, Any> = emptyMap()
  ): Result<Unit> {
    return try {
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      val user = getCurrentUser()
      val effectiveEmail = user?.email?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_EMAIL
      val effectiveName = user?.displayName?.takeIf { it.isNotBlank() } ?: "Tanush Ghosh"
      val now = System.currentTimeMillis()

      val editor = prefs.edit()
      editor.putLong("last_sync_timestamp", now)
      editor.putString("synced_email", effectiveEmail)
      editor.putString("synced_name", effectiveName)
      for ((k, v) in extraData) {
        when (v) {
          is Boolean -> editor.putBoolean("setting_$k", v)
          is String -> editor.putString("setting_$k", v)
          is Int -> editor.putInt("setting_$k", v)
          is Long -> editor.putLong("setting_$k", v)
          is Float -> editor.putFloat("setting_$k", v)
        }
      }
      editor.apply()

      if (user != null) {
        val db = getFirestore(context)
        val data = mutableMapOf<String, Any>(
          "displayName" to effectiveName,
          "email" to effectiveEmail,
          "updatedAt" to com.google.firebase.Timestamp.now(),
          "settings" to extraData,
          "defaultUrl" to "https://reostech.pagekite.me",
          "reosTabUrl" to "https://reostech.pgekite.me",
          "fallbackUrl" to "https://reostech.pagekite.me",
          "grounding" to mapOf(
            "googleSearch" to true,
            "googleMaps" to true,
            "model" to "gemini-3.8-flash"
          )
        )
        val savedScratchpad = getScratchpad(context)
        if (savedScratchpad.isNotBlank()) {
          data["scratchpad"] = savedScratchpad
        }
        val currentUsername = ReosAccountManager.getLoggedInUsername(context)
        if (!currentUsername.isNullOrBlank()) {
          data["username"] = currentUsername
        }
        val currentWebCache = getWebCache(context)
        if (currentWebCache.isNotEmpty()) {
          data["webCache"] = currentWebCache
        }
        db.collection("users").document(user.uid).set(data, SetOptions.merge()).await()
      }
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Error in syncEverything", e)
      Result.failure(e)
    }
  }

  fun getLastSyncTimestamp(context: Context): Long {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getLong("last_sync_timestamp", 0L)
  }
}
