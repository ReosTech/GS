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

  fun getCurrentUser(): FirebaseUser? {
    return try {
      Firebase.auth.currentUser
    } catch (_: Exception) {
      null
    }
  }

  suspend fun syncUserPreferences(context: Context, settings: Map<String, Any>): Result<Unit> {
    return try {
      val user = getCurrentUser() ?: return Result.failure(Exception("User not authenticated with Google Sign-In"))
      val db = getFirestore(context)
      val data = mapOf(
        "displayName" to (user.displayName ?: "Reos User"),
        "email" to (user.email ?: ""),
        "updatedAt" to com.google.firebase.Timestamp.now(),
        "settings" to settings
      )
      db.collection("users").document(user.uid).set(data, SetOptions.merge()).await()
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Error syncing preferences to Firestore", e)
      Result.failure(e)
    }
  }
}
