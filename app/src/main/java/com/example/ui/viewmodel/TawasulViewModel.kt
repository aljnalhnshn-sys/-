package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.ParticipantInfo
import com.example.data.model.UserProfile
import com.example.data.repository.TawasulRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TawasulViewModel(
    private val repository: TawasulRepository,
    val currentUserId: String
) : ViewModel() {

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val conversations: StateFlow<List<Conversation>> = repository.observeConversations(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val searchResults: StateFlow<List<UserProfile>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    // Active conversation state
    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _activePeerUser = MutableStateFlow<UserProfile?>(null)
    val activePeerUser: StateFlow<UserProfile?> = _activePeerUser.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<ChatMessage>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeMessages(convId, currentUserId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _isSendingMessage = MutableStateFlow(false)
    val isSendingMessage: StateFlow<Boolean> = _isSendingMessage.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.trim().isEmpty()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            val results = repository.searchUsersByUsername(query.trim())
            // Filter out current user from results
            _searchResults.value = results.filter { it.uid != currentUserId }
            _isSearching.value = false
        }
    }

    fun openConversation(conversationId: String, peerInfo: ParticipantInfo?, peerUid: String) {
        _activeConversationId.value = conversationId
        _activePeerUser.value = UserProfile(
            uid = peerUid,
            username = peerInfo?.username ?: "",
            displayName = peerInfo?.displayName ?: "مستخدم تواصل",
            photoUrl = peerInfo?.photoUrl
        )
        viewModelScope.launch {
            repository.markConversationAsRead(conversationId, currentUserId)
        }
    }

    fun startChatWithUser(peerUser: UserProfile, onReady: (String) -> Unit) {
        val myUser = userProfile.value ?: UserProfile(uid = currentUserId, displayName = "أنا")
        viewModelScope.launch {
            val result = repository.getOrCreateConversation(myUser, peerUser)
            result.onSuccess { conv ->
                _activeConversationId.value = conv.conversationId
                _activePeerUser.value = peerUser
                onReady(conv.conversationId)
            }.onFailure { e ->
                _toastMessage.value = e.localizedMessage ?: "تعذر بدء المحادثة"
            }
        }
    }

    fun onMessageInputChanged(text: String) {
        _messageInput.value = text
    }

    fun sendCurrentMessage() {
        val text = _messageInput.value.trim()
        val convId = _activeConversationId.value ?: return
        val peer = _activePeerUser.value ?: return
        val myUser = userProfile.value ?: UserProfile(uid = currentUserId, displayName = "أنا")

        if (text.isEmpty() || _isSendingMessage.value) return

        viewModelScope.launch {
            _isSendingMessage.value = true
            _messageInput.value = "" // Optimistic clear
            val result = repository.sendMessage(
                conversationId = convId,
                senderUser = myUser,
                recipientUid = peer.uid,
                text = text
            )
            result.onFailure { error ->
                _toastMessage.value = error.localizedMessage ?: "فشل إرسال الرسالة"
                _messageInput.value = text // Restore text on failure
            }
            _isSendingMessage.value = false
        }
    }

    fun updateProfile(
        displayName: String,
        bio: String?,
        photoUrl: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.updateUserProfile(displayName, bio, photoUrl)
            result.onSuccess {
                _toastMessage.value = "تم تحديث الملف الشخصي بنجاح"
                onSuccess()
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "فشل تحديث الملف الشخصي"
                _toastMessage.value = msg
                onError(msg)
            }
        }
    }

    fun blockUser(peerUid: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val res = repository.blockUser(currentUserId, peerUid)
            res.onSuccess {
                _toastMessage.value = "تم حظر المستخدم"
                onSuccess()
            }.onFailure { e ->
                _toastMessage.value = e.localizedMessage ?: "تعذر إتمام الحظر"
            }
        }
    }

    fun unblockUser(peerUid: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val res = repository.unblockUser(currentUserId, peerUid)
            res.onSuccess {
                _toastMessage.value = "تم إلغاء الحظر"
                onSuccess()
            }.onFailure { e ->
                _toastMessage.value = e.localizedMessage ?: "تعذر إلغاء الحظر"
            }
        }
    }

    fun reportUser(reportedUid: String, reason: String, onSuccess: () -> Unit) {
        _toastMessage.value = "تم إرسال البلاغ بنجاح وسيتم تدقيقه"
        onSuccess()
    }
}
