package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TawasulRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createUserProfile_validUsername_succeeds() = runBlocking {
    val uid = signInTestUser("user_${UUID.randomUUID()}@test.com")
    val repository = TawasulRepository(firestore)

    val username = "user_${UUID.randomUUID().toString().take(6)}"
    val result = withTimeout(5000L) {
      repository.createUserProfile(
        username = username,
        displayName = "مستخدم تجريبي",
        bio = "أهلاً بتواصل",
        photoUrl = null
      )
    }

    assertTrue("Create profile should succeed", result.isSuccess)
    val profile = result.getOrThrow()
    assertEquals(username, profile.username)
    assertEquals(uid, profile.uid)

    val fetched = repository.getUserProfile(uid)
    assertNotNull("Profile should be retrievable", fetched)
    assertEquals(username, fetched?.username)
  }

  @Test
  fun getOrCreateConversation_and_sendMessage_succeeds() = runBlocking {
    val aliceEmail = "alice_${UUID.randomUUID()}@test.com"
    val uidA = signInTestUser(aliceEmail)
    val repo = TawasulRepository(firestore)
    val userA = UserProfile(uid = uidA, username = "alice_${UUID.randomUUID().toString().take(6)}", displayName = "أليس")
    val resA = repo.createUserProfile(userA.username, userA.displayName, null, null)
    assertTrue("User A creation should succeed", resA.isSuccess)

    val bobEmail = "bob_${UUID.randomUUID()}@test.com"
    val uidB = signInTestUser(bobEmail)
    val userB = UserProfile(uid = uidB, username = "bob_${UUID.randomUUID().toString().take(6)}", displayName = "بوب")
    val resB = repo.createUserProfile(userB.username, userB.displayName, null, null)
    assertTrue("User B creation should succeed", resB.isSuccess)

    // Sign back in as Alice
    signInTestUser(aliceEmail)

    val convResult = withTimeout(5000L) {
      repo.getOrCreateConversation(userA, userB)
    }
    assertTrue("Conversation creation should succeed: ${convResult.exceptionOrNull()?.message}", convResult.isSuccess)
    val conversation = convResult.getOrThrow()
    assertNotNull(conversation.conversationId)

    val sendResult = withTimeout(5000L) {
      repo.sendMessage(
        conversationId = conversation.conversationId,
        senderUser = userA,
        recipientUid = userB.uid,
        text = "مرحباً يا صديقي، رسالة تجريبية حقيقية"
      )
    }
    assertTrue("Sending message should succeed: ${sendResult.exceptionOrNull()?.message}", sendResult.isSuccess)
    val message = sendResult.getOrThrow()
    assertEquals("مرحباً يا صديقي، رسالة تجريبية حقيقية", message.text)

    val messages = withTimeout(5000L) {
      repo.observeMessages(conversation.conversationId, userA.uid).first { list ->
        list.any { it.messageId == message.messageId }
      }
    }
    assertTrue(messages.any { it.messageId == message.messageId })
  }

  @Test
  fun unauthenticatedAccess_failsGracefully() = runBlocking {
    auth.signOut()
    val repo = TawasulRepository(firestore)

    val result = repo.createUserProfile("anon", "Anon", null, null)
    assertTrue("Unauthenticated create should fail", result.isFailure)
  }
}
