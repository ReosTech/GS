package com.example.firebase

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object GoogleSignInHelper {
  private const val TAG = "GoogleSignInHelper"

  suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
    return try {
      val credentialManager = CredentialManager.create(context)
      val serverClientId = context.getString(R.string.default_web_client_id)

      val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId)
        .build()

      val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

      val result = credentialManager.getCredential(context = context, request = request)
      val credential = result.credential

      if (credential is androidx.credentials.CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
      ) {
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val idToken = googleIdTokenCredential.idToken
        val authCredential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = FirebaseAuth.getInstance().signInWithCredential(authCredential).await()
        val user = authResult.user
        if (user != null) {
          Result.success(user)
        } else {
          Result.failure(Exception("Firebase user sign in failed"))
        }
      } else {
        Result.failure(Exception("Unrecognized credential type received"))
      }
    } catch (e: GetCredentialCancellationException) {
      Log.i(TAG, "User cancelled Google Sign-In: ${e.message}")
      Result.failure(e)
    } catch (e: Exception) {
      Log.e(TAG, "Google Sign-In failed", e)
      Result.failure(e)
    }
  }

  fun signOut() {
    try {
      FirebaseAuth.getInstance().signOut()
    } catch (_: Exception) {}
  }
}
