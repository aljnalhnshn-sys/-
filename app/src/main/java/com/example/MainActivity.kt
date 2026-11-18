package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.TawasulRepository
import com.example.ui.screens.*
import com.example.ui.theme.TawasulTheme
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.TawasulViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class AppScreen {
    CHATS,
    SEARCH,
    CHAT,
    PROFILE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TawasulTheme {
                TawasulAppRoot()
            }
        }
    }
}

@Composable
fun TawasulAppRoot() {
    val context = LocalContext.current
    val databaseId = context.getString(R.string.firestore_database_id)
    val db = remember(databaseId) { FirebaseFirestore.getInstance(databaseId) }
    val repository = remember(db) { TawasulRepository(db) }

    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AuthViewModel(repository) }
        }
    )

    // Attempt silent auto-sign in on initial launch
    LaunchedEffect(Unit) {
        authViewModel.attemptAutoSignIn(context)
    }

    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    when (val state = authState) {
        is AuthUiState.Idle, is AuthUiState.Loading, is AuthUiState.Error -> {
            AuthScreen(
                uiState = state,
                onSignInClicked = { activity ->
                    authViewModel.onGoogleSignInClicked(activity)
                }
            )
        }
        is AuthUiState.AuthenticatedNoProfile -> {
            ProfileSetupScreen(
                user = state.user,
                onProfileCompleted = { profile ->
                    authViewModel.checkUserProfile(state.user)
                },
                onSubmitProfile = { username, displayName, bio, photoUrl, onSuccess, onError ->
                    authViewModel.completeProfileSetup(username, displayName, bio, photoUrl, onSuccess, onError)
                }
            )
        }
        is AuthUiState.AuthenticatedWithProfile -> {
            AuthenticatedApp(
                currentUserId = state.user.uid,
                repository = repository,
                onSignOut = {
                    authViewModel.signOut(context) {}
                }
            )
        }
    }
}

@Composable
fun AuthenticatedApp(
    currentUserId: String,
    repository: TawasulRepository,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val tawasulViewModel: TawasulViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer { TawasulViewModel(repository, currentUserId) }
        }
    )

    var currentScreen by remember { mutableStateOf(AppScreen.CHATS) }

    val userProfile by tawasulViewModel.userProfile.collectAsStateWithLifecycle()
    val conversations by tawasulViewModel.conversations.collectAsStateWithLifecycle()
    val searchQuery by tawasulViewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by tawasulViewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by tawasulViewModel.isSearching.collectAsStateWithLifecycle()
    val activePeerUser by tawasulViewModel.activePeerUser.collectAsStateWithLifecycle()
    val activeMessages by tawasulViewModel.activeMessages.collectAsStateWithLifecycle()
    val messageInput by tawasulViewModel.messageInput.collectAsStateWithLifecycle()
    val isSendingMessage by tawasulViewModel.isSendingMessage.collectAsStateWithLifecycle()
    val toastMessage by tawasulViewModel.toastMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            tawasulViewModel.clearToastMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.CHATS -> {
                    ChatsListScreen(
                        currentUserId = currentUserId,
                        currentUserProfile = userProfile,
                        conversations = conversations,
                        onOpenConversation = { convId, peerInfo, peerUid ->
                            tawasulViewModel.openConversation(convId, peerInfo, peerUid)
                            currentScreen = AppScreen.CHAT
                        },
                        onNavigateToSearch = { currentScreen = AppScreen.SEARCH },
                        onNavigateToProfile = { currentScreen = AppScreen.PROFILE }
                    )
                }
                AppScreen.SEARCH -> {
                    SearchUsersScreen(
                        searchQuery = searchQuery,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        onQueryChanged = { tawasulViewModel.onSearchQueryChanged(it) },
                        onStartChat = { peer ->
                            tawasulViewModel.startChatWithUser(peer) {
                                currentScreen = AppScreen.CHAT
                            }
                        },
                        onBack = { currentScreen = AppScreen.CHATS }
                    )
                }
                AppScreen.CHAT -> {
                    ChatScreen(
                        currentUserId = currentUserId,
                        peerUser = activePeerUser,
                        messages = activeMessages,
                        messageInput = messageInput,
                        isSending = isSendingMessage,
                        onMessageInputChanged = { tawasulViewModel.onMessageInputChanged(it) },
                        onSendMessage = { tawasulViewModel.sendCurrentMessage() },
                        onBlockUser = { peerUid ->
                            tawasulViewModel.blockUser(peerUid) {
                                currentScreen = AppScreen.CHATS
                            }
                        },
                        onReportUser = { reportedUid, reason ->
                            tawasulViewModel.reportUser(reportedUid, reason) {}
                        },
                        onBack = { currentScreen = AppScreen.CHATS }
                    )
                }
                AppScreen.PROFILE -> {
                    ProfileScreen(
                        userProfile = userProfile,
                        onUpdateProfile = { displayName, bio, photoUrl, onSuccess, onError ->
                            tawasulViewModel.updateProfile(displayName, bio, photoUrl, onSuccess, onError)
                        },
                        onSignOut = onSignOut,
                        onBack = { currentScreen = AppScreen.CHATS }
                    )
                }
            }
        }
    }
}
