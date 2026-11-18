package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.UserProfile
import com.example.data.repository.TawasulRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class AuthenticatedNoProfile(val user: FirebaseUser) : AuthUiState
    data class AuthenticatedWithProfile(val user: FirebaseUser, val profile: UserProfile) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(
    private val repository: TawasulRepository
) : ViewModel() {

    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            checkUserProfile(user)
        } else {
            _uiState.value = AuthUiState.Idle
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        val initialUser = auth.currentUser
        if (initialUser != null) {
            checkUserProfile(initialUser)
        }
    }

    fun checkUserProfile(user: FirebaseUser) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val profile = repository.getUserProfile(user.uid)
            if (profile != null && profile.username.isNotBlank()) {
                _uiState.value = AuthUiState.AuthenticatedWithProfile(user, profile)
            } else {
                _uiState.value = AuthUiState.AuthenticatedNoProfile(user)
            }
        }
    }

    fun onGoogleSignInClicked(activity: Activity) {
        _uiState.value = AuthUiState.Loading
        val credentialManager = CredentialManager.create(activity)
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            _uiState.value = AuthUiState.Error("لم يتم العثور على معرّف Google Web Client ID")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        checkUserProfile(user)
                    } else {
                        _uiState.value = AuthUiState.Error("تعذر إتمام تسجيل الدخول بحساب Google")
                    }
                } else {
                    _uiState.value = AuthUiState.Error("نوع المصادقة غير مدعوم")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In flow cancelled: ${e.message}")
                _uiState.value = AuthUiState.Idle
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In failed", e)
                _uiState.value = AuthUiState.Error(e.localizedMessage ?: "فشل تسجيل الدخول بواسطة Google")
            }
        }
    }

    fun attemptAutoSignIn(context: Context) {
        if (auth.currentUser != null) return
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
        val credentialManager = CredentialManager.create(context)

        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    authResult.user?.let { checkUserProfile(it) }
                }
            } catch (e: Exception) {
                // Auto sign-in fallback: stays on Idle for interactive Google sign-in
            }
        }
    }

    fun completeProfileSetup(
        username: String,
        displayName: String,
        bio: String?,
        photoUrl: String?,
        onSuccess: (UserProfile) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user == null) {
                onError("انتهت الجلسة، يرجى تسجيل الدخول مجدداً")
                return@launch
            }
            val result = repository.createUserProfile(username, displayName, bio, photoUrl)
            result.onSuccess { profile ->
                _uiState.value = AuthUiState.AuthenticatedWithProfile(user, profile)
                onSuccess(profile)
            }.onFailure { err ->
                onError(err.localizedMessage ?: "فشل حفظ الملف الشخصي")
            }
        }
    }

    fun signOut(context: Context, onComplete: () -> Unit) {
        auth.signOut()
        val credentialManager = CredentialManager.create(context)
        viewModelScope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e("Auth", "Failed to clear credentials", e)
            } finally {
                _uiState.value = AuthUiState.Idle
                onComplete()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
    }
}
