package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val uid: String = "",
    val username: String = "",
    val usernameLowercase: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val bio: String? = null,
    val fcmToken: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ParticipantInfo(
    val displayName: String = "",
    val username: String = "",
    val photoUrl: String? = null
)

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val conversationId: String = "",
    val participantIds: List<String> = emptyList(),
    val participantData: Map<String, ParticipantInfo> = emptyMap(),
    val lastMessage: String? = null,
    val lastMessageSenderId: String? = null,
    val lastMessageSenderName: String? = null,
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val unreadCount: Map<String, Long> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val messageId: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val participantIds: List<String> = emptyList(),
    val text: String = "",
    val type: String = "text",
    val imageUrl: String? = null,
    val status: String = "sent",
    val readBy: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BlockedUser(
    @DocumentId val blockedUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

fun getDeterministicConversationId(uid1: String, uid2: String): String {
    return if (uid1 < uid2) "conv_${uid1}_${uid2}" else "conv_${uid2}_${uid1}"
}
