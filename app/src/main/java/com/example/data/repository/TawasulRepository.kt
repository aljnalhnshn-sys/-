package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.ParticipantInfo
import com.example.data.model.UserProfile
import com.example.data.model.getDeterministicConversationId
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TawasulRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول باستخدام حساب Google أولاً")
    }

    suspend fun getUserProfile(uid: String): UserProfile? {
        val path = "users/$uid"
        return try {
            val doc = db.collection("users").document(uid).get().await()
            doc.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> {
        val path = "users/$uid"
        return db.collection("users").document(uid)
            .snapshots()
            .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.GET, path)
                emit(null)
            }
    }

    suspend fun isUsernameAvailable(username: String): Boolean {
        val normalized = username.trim().lowercase()
        val path = "usernames/$normalized"
        return try {
            val doc = db.collection("usernames").document(normalized).get().await()
            !doc.exists()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            false
        }
    }

    suspend fun createUserProfile(
        username: String,
        displayName: String,
        bio: String?,
        photoUrl: String?
    ): Result<UserProfile> {
        val uid = requireUserId()
        val normalizedUsername = username.trim().lowercase()
        val usernamesPath = "usernames/$normalizedUsername"
        val usersPath = "users/$uid"

        return try {
            // First check if username already taken
            val existingUsernameDoc = db.collection("usernames").document(normalizedUsername).get().await()
            if (existingUsernameDoc.exists() && existingUsernameDoc.getString("uid") != uid) {
                return Result.failure(Exception("اسم المستخدم محجوز بالفعل"))
            }

            // Write username reservation and user profile in batch
            val batch = db.batch()

            val usernameRef = db.collection("usernames").document(normalizedUsername)
            batch.set(
                usernameRef,
                mapOf(
                    "uid" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            val userRef = db.collection("users").document(uid)
            val userPayload = mutableMapOf<String, Any>(
                "uid" to uid,
                "username" to normalizedUsername,
                "usernameLowercase" to normalizedUsername,
                "displayName" to displayName.trim(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!bio.isNullOrBlank()) {
                userPayload["bio"] = bio.trim()
            }
            if (!photoUrl.isNullOrBlank()) {
                userPayload["photoUrl"] = photoUrl.trim()
            }
            batch.set(userRef, userPayload)

            batch.commit().await()

            val profile = UserProfile(
                uid = uid,
                username = normalizedUsername,
                usernameLowercase = normalizedUsername,
                displayName = displayName.trim(),
                bio = bio?.trim(),
                photoUrl = photoUrl?.trim()
            )
            Result.success(profile)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "$usersPath / $usernamesPath")
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        displayName: String,
        bio: String?,
        photoUrl: String?
    ): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid"
        return try {
            val updates = mutableMapOf<String, Any>(
                "displayName" to displayName.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (bio != null) updates["bio"] = bio.trim()
            if (photoUrl != null) updates["photoUrl"] = photoUrl.trim()

            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun searchUsersByUsername(query: String): List<UserProfile> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()

        val path = "users"
        return try {
            val snapshot = db.collection("users")
                .orderBy("usernameLowercase")
                .startAt(normalized)
                .endAt(normalized + "\uf8ff")
                .limit(20)
                .get()
                .await()

            snapshot.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            emptyList()
        }
    }

    fun observeConversations(myUid: String): Flow<List<Conversation>> {
        val path = "conversations"
        return db.collection("conversations")
            .whereArrayContains("participantIds", myUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastMessageAt ?: it.createdAt }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                emit(emptyList())
            }
    }

    suspend fun getOrCreateConversation(
        myUser: UserProfile,
        peerUser: UserProfile
    ): Result<Conversation> {
        val convId = getDeterministicConversationId(myUser.uid, peerUser.uid)
        val path = "conversations/$convId"

        return try {
            val docRef = db.collection("conversations").document(convId)
            val doc = try {
                docRef.get().await()
            } catch (e: Exception) {
                null
            }

            if (doc != null && doc.exists()) {
                val conv = doc.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: Conversation(conversationId = convId, participantIds = listOf(myUser.uid, peerUser.uid))
                Result.success(conv)
            } else {
                val newConversation = mapOf(
                    "conversationId" to convId,
                    "participantIds" to listOf(myUser.uid, peerUser.uid),
                    "participantData" to mapOf(
                        myUser.uid to mapOf(
                            "displayName" to myUser.displayName,
                            "username" to myUser.username,
                            "photoUrl" to myUser.photoUrl
                        ),
                        peerUser.uid to mapOf(
                            "displayName" to peerUser.displayName,
                            "username" to peerUser.username,
                            "photoUrl" to peerUser.photoUrl
                        )
                    ),
                    "unreadCount" to mapOf(
                        myUser.uid to 0L,
                        peerUser.uid to 0L
                    ),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(newConversation).await()

                val createdConv = Conversation(
                    conversationId = convId,
                    participantIds = listOf(myUser.uid, peerUser.uid),
                    participantData = mapOf(
                        myUser.uid to ParticipantInfo(myUser.displayName, myUser.username, myUser.photoUrl),
                        peerUser.uid to ParticipantInfo(peerUser.displayName, peerUser.username, peerUser.photoUrl)
                    )
                )
                Result.success(createdConv)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeMessages(conversationId: String, currentUid: String? = auth.currentUser?.uid): Flow<List<ChatMessage>> {
        val path = "conversations/$conversationId/messages"
        val query = if (currentUid != null) {
            db.collection("conversations").document(conversationId)
                .collection("messages")
                .whereArrayContains("participantIds", currentUid)
        } else {
            db.collection("conversations").document(conversationId)
                .collection("messages")
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: com.google.firebase.Timestamp(0, 0) }
            }
            .catch { e ->
                e.printStackTrace()
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                emit(emptyList())
            }
    }

    suspend fun sendMessage(
        conversationId: String,
        senderUser: UserProfile,
        recipientUid: String,
        text: String,
        type: String = "text"
    ): Result<ChatMessage> {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) {
            return Result.failure(IllegalArgumentException("لا يمكن إرسال رسالة فارغة"))
        }

        val messageId = "msg_${UUID.randomUUID()}"
        val messagePath = "conversations/$conversationId/messages/$messageId"
        val convPath = "conversations/$conversationId"

        return try {
            val messagePayload = mapOf(
                "messageId" to messageId,
                "conversationId" to conversationId,
                "senderId" to senderUser.uid,
                "senderName" to senderUser.displayName,
                "participantIds" to listOf(senderUser.uid, recipientUid),
                "text" to trimmedText,
                "type" to type,
                "status" to "sent",
                "readBy" to listOf(senderUser.uid),
                "createdAt" to FieldValue.serverTimestamp()
            )

            val convUpdates = mapOf<String, Any>(
                "lastMessage" to trimmedText,
                "lastMessageSenderId" to senderUser.uid,
                "lastMessageSenderName" to senderUser.displayName,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "unreadCount.$recipientUid" to FieldValue.increment(1)
            )

            val batch = db.batch()
            val msgRef = db.collection("conversations").document(conversationId).collection("messages").document(messageId)
            val convRef = db.collection("conversations").document(conversationId)

            batch.set(msgRef, messagePayload)
            batch.update(convRef, convUpdates)

            batch.commit().await()

            val msg = ChatMessage(
                messageId = messageId,
                conversationId = conversationId,
                senderId = senderUser.uid,
                senderName = senderUser.displayName,
                participantIds = listOf(senderUser.uid, recipientUid),
                text = trimmedText,
                type = type,
                status = "sent",
                readBy = listOf(senderUser.uid)
            )
            Result.success(msg)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, messagePath)
            Result.failure(e)
        }
    }

    suspend fun markConversationAsRead(conversationId: String, myUid: String) {
        val path = "conversations/$conversationId"
        try {
            db.collection("conversations").document(conversationId)
                .update("unreadCount.$myUid", 0L)
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun blockUser(myUid: String, blockedUid: String): Result<Unit> {
        val path = "users/$myUid/blocked/$blockedUid"
        return try {
            db.collection("users").document(myUid)
                .collection("blocked").document(blockedUid)
                .set(mapOf("blockedUid" to blockedUid, "createdAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(myUid: String, blockedUid: String): Result<Unit> {
        val path = "users/$myUid/blocked/$blockedUid"
        return try {
            db.collection("users").document(myUid)
                .collection("blocked").document(blockedUid)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
