package com.example.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

object FirebaseManager {
  private const val TAG = "FirebaseManager"

  fun getFirestore(context: Context): FirebaseFirestore {
    return try {
      val databaseId = context.getString(R.string.firestore_database_id)
      if (databaseId.isNotBlank()) {
        Firebase.firestore(databaseId)
      } else {
        Firebase.firestore
      }
    } catch (e: Exception) {
      Log.w(TAG, "Using default Firestore instance: ${e.message}")
      Firebase.firestore
    }
  }

  fun getAuth(): FirebaseAuth {
    return Firebase.auth
  }

  const val DEFAULT_USER_EMAIL = "tanushghosh10635@gmail.com"

  fun getCurrentUser(): FirebaseUser? {
    return try {
      Firebase.auth.currentUser
    } catch (_: Exception) {
      null
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
      val prefs = context.getSharedPreferences("reos_synced_state", Context.MODE_PRIVATE)
      val user = getCurrentUser()
      val effectiveEmail = user?.email?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_EMAIL
      val effectiveName = user?.displayName?.takeIf { it.isNotBlank() } ?: "Tanush Ghosh"
      val now = System.currentTimeMillis()

      // Local sync persistence of all app state & preferences
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

      // If user is authenticated with Firebase, sync to Firestore
      if (user != null) {
        val db = getFirestore(context)
        val data = mapOf(
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
        db.collection("users").document(user.uid).set(data, SetOptions.merge()).await()
      }
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Error in syncEverything", e)
      Result.failure(e)
    }
  }

  fun getLastSyncTimestamp(context: Context): Long {
    val prefs = context.getSharedPreferences("reos_synced_state", Context.MODE_PRIVATE)
    return prefs.getLong("last_sync_timestamp", 0L)
  }
}
