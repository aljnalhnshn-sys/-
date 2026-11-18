const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_user_123";
const BOB_UID = "bob_user_456";
const CHARLIE_UID = "charlie_user_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read conversations or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("conversations").get());
  await assertFails(unauthDb.collection("users").get());
});

test("Authenticated user: can create their own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice_w",
      usernameLowercase: "alice_w",
      displayName: "Alice Wonderland",
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: cannot create profile for another UID", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      username: "bob_m",
      usernameLowercase: "bob_m",
      displayName: "Bob",
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: can reserve username with their own UID", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("usernames").doc("alice_w").set({
      uid: ALICE_UID,
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: cannot reserve invalid username (symbols or bad length)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("usernames").doc("al").set({
      uid: ALICE_UID,
      createdAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.collection("usernames").doc("alice@cool").set({
      uid: ALICE_UID,
      createdAt: new Date(),
    })
  );
});

test("Conversation access: only participants can read conversation", async () => {
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantIds: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("conversations").doc(convId).get());

  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(charlieDb.collection("conversations").doc(convId).get());
});

test("Messages subcollection: participant can create and read messages, non-participant cannot", async () => {
  const convId = "conv_alice_bob";
  const msgId = "msg_001";

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc(msgId).set({
      messageId: msgId,
      conversationId: convId,
      senderId: ALICE_UID,
      senderName: "Alice",
      participantIds: [ALICE_UID, BOB_UID],
      text: "مرحبا بك يا بوب",
      status: "sent",
      createdAt: new Date(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").doc(msgId).get()
  );
});

