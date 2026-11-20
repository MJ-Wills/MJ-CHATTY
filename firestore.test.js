const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const EVE_UID = "eve_uid_003";
const CHAT_ID = `dm_${ALICE_UID}_${BOB_UID}`;

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

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

// 1. PRIVATE USER ACCOUNT (PII ISOLATED)
test("UserAccount: owner can create, read, update; other users and unauth denied", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(10000);

  const validUser = {
    userId: ALICE_UID,
    displayName: "Alice Neon",
    username: "alice_neon",
    phoneNumber: "+15551234567",
    about: "Hey there! I am on MJ Chatty",
    online: true,
    lastSeenAt: now,
    createdAt: now,
    updatedAt: now,
  };

  // Unauthenticated denied
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validUser));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());

  // Owner create succeeds
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUser));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Cross-user PII read denied & list denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
  await assertFails(aliceDb.collection("users").get());

  // Valid profile partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice MJ",
      username: "alice_mj",
      about: "Updated bio",
      updatedAt: pastDate(1000),
    })
  );

  // Valid presence partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      online: false,
      lastSeenAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Shadow update (ghost field) & immutable createdAt update fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Future Alice",
      updatedAt: new Date(Date.now() + 120000),
    })
  );
});

// 2. PUBLIC PROFILES & DIRECTORY LOOKUP (NON-PII, GET-ONLY)
test("PublicProfile & DirectoryEntry: get allowed for signed-in, list forbidden, owner-only writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = pastDate(8000);

  const pubProfile = {
    ownerUid: ALICE_UID,
    displayName: "Alice Neon",
    username: "alice_neon",
    about: "Online now",
    online: true,
    lastSeenAt: now,
    createdAt: now,
    updatedAt: now,
  };

  await assertSucceeds(aliceDb.collection("publicProfiles").doc(ALICE_UID).set(pubProfile));
  await assertSucceeds(bobDb.collection("publicProfiles").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("publicProfiles").get()); // No scraping!
  await assertFails(unauthDb.collection("publicProfiles").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("publicProfiles").doc(ALICE_UID).update({ displayName: "Hacked", updatedAt: pastDate(1000) }));

  // Directory entry for username & hashed phone
  const dirEntry = {
    ownerUid: ALICE_UID,
    lookupType: "username",
    displayName: "Alice Neon",
    username: "alice_neon",
    createdAt: now,
    updatedAt: now,
  };
  await assertSucceeds(aliceDb.collection("directory").doc("u_alice_neon").set(dirEntry));
  await assertSucceeds(bobDb.collection("directory").doc("u_alice_neon").get());
  await assertFails(bobDb.collection("directory").get()); // No listing!
  await assertFails(bobDb.collection("directory").doc("u_alice_neon").delete());
  await assertSucceeds(aliceDb.collection("directory").doc("u_alice_neon").update({
    displayName: "Alice Updated",
    updatedAt: pastDate(1000),
  }));
});

// 3. SAVED CONTACTS SUBCOLLECTION
test("SavedContacts: owner-isolated list/create/update/delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(8000);

  const contact = {
    contactUid: BOB_UID,
    savedName: "Bob Bestie",
    contactUsername: "bob_chat",
    contactPhone: "+15559876543",
    registered: true,
    createdAt: now,
    updatedAt: now,
  };

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("contacts").doc(BOB_UID).set(contact)
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("contacts").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("contacts").doc(BOB_UID).update({
      savedName: "Bobby",
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user access denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("contacts").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("contacts").doc(BOB_UID).get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("contacts").doc(BOB_UID).delete()
  );
});

// 4. CHAT THREADS & REAL-TIME MESSAGES
test("Chats & Messages: member-scoped queries, typing, delivery/read, reactions, outsider denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const now = pastDate(10000);
  const selfChatId = `dm_${ALICE_UID}_${ALICE_UID}`;

  // Probing non-existent direct & Saved Messages chats before creation succeeds for members, denied for outsider
  const nonExistentSelf = await assertSucceeds(aliceDb.collection("chats").doc(selfChatId).get());
  if (nonExistentSelf.exists) {
    throw new Error("Expected self chat not to exist before creation");
  }
  await assertSucceeds(aliceDb.collection("chats").doc(CHAT_ID).get());
  await assertSucceeds(bobDb.collection("chats").doc(CHAT_ID).get());
  await assertFails(eveDb.collection("chats").doc(CHAT_ID).get());

  // Creating Saved Messages (single-member self chat) succeeds
  await assertSucceeds(
    aliceDb.collection("chats").doc(selfChatId).set({
      memberUids: [ALICE_UID],
      chatType: "direct",
      title: "Saved Messages",
      lastSenderId: ALICE_UID,
      lastMessagePreview: "Your private cloud notes & drafts",
      lastMessageType: "text",
      lastMessageStatus: "read",
      lastMessageAt: now,
      typingUid: "",
      unreadByUid: "",
      unreadCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(eveDb.collection("chats").doc(selfChatId).get());

  const chatPayload = {
    memberUids: [ALICE_UID, BOB_UID],
    chatType: "direct",
    lastSenderId: ALICE_UID,
    lastMessagePreview: "Hey Bob!",
    lastMessageType: "text",
    lastMessageStatus: "sent",
    lastMessageAt: now,
    typingUid: "",
    unreadByUid: BOB_UID,
    unreadCount: 1,
    createdAt: now,
    updatedAt: now,
  };

  // Alice creates direct chat with Bob
  await assertSucceeds(aliceDb.collection("chats").doc(CHAT_ID).set(chatPayload));

  // Member-filtered list succeeds; unfiltered list fails; Eve denied
  await assertSucceeds(
    aliceDb.collection("chats").where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("chats").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertFails(aliceDb.collection("chats").get());
  await assertFails(eveDb.collection("chats").doc(CHAT_ID).get());

  // Typing indicator update by Bob succeeds
  await assertSucceeds(
    bobDb.collection("chats").doc(CHAT_ID).update({
      typingUid: BOB_UID,
      typingUpdatedAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  // Mark read receipt by Bob succeeds
  await assertSucceeds(
    bobDb.collection("chats").doc(CHAT_ID).update({
      lastMessageStatus: "read",
      unreadByUid: "",
      unreadCount: 0,
      updatedAt: pastDate(1000),
    })
  );

  // Create text message inside chat
  const msgPayload = {
    chatId: CHAT_ID,
    senderId: ALICE_UID,
    memberUids: [ALICE_UID, BOB_UID],
    messageType: "text",
    text: "Hey Bob! Ready for launch?",
    status: "sent",
    createdAt: now,
    updatedAt: now,
  };
  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_001").set(msgPayload)
  );

  // Voice message with duration succeeds
  await assertSucceeds(
    bobDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_002").set({
      chatId: CHAT_ID,
      senderId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "voice",
      text: "",
      voiceData: "UklGRg==",
      voiceDurationMs: 4200,
      status: "sent",
      createdAt: pastDate(3000),
      updatedAt: pastDate(3000),
    })
  );

  // Member-filtered message list succeeds; Eve cannot list unfiltered, spoof Alice's UID, or read doc directly
  await assertSucceeds(
    bobDb.collection("chats").doc(CHAT_ID).collection("messages")
      .where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertFails(
    eveDb.collection("chats").doc(CHAT_ID).collection("messages").get()
  );
  await assertFails(
    eveDb.collection("chats").doc(CHAT_ID).collection("messages")
      .where("memberUids", "array-contains", ALICE_UID).get()
  );
  const eveFiltered = await assertSucceeds(
    eveDb.collection("chats").doc(CHAT_ID).collection("messages")
      .where("memberUids", "array-contains", EVE_UID).get()
  );
  if (!eveFiltered.empty) {
    throw new Error("Expected Eve's filtered query to return 0 messages");
  }
  await assertFails(
    eveDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_001").get()
  );

  // Bob marks Alice's message as read & reacts with emoji
  await assertSucceeds(
    bobDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_001").update({
      status: "read",
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_001").update({
      reaction: "🔥",
      updatedAt: pastDate(500),
    })
  );

  // Bob cannot tamper with Alice's message text or delete Alice's message
  await assertFails(
    bobDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_001").update({
      text: "Tampered text",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_001").delete()
  );

  // Alice can edit her own text message; Bob cannot edit Alice's text
  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_001").update({
      text: "Hey Bob! Updated launch time!",
      edited: true,
      editedAt: pastDate(400),
      updatedAt: pastDate(400),
    })
  );

  // Either member can pin/unpin a message
  await assertSucceeds(
    bobDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_001").update({
      pinned: true,
      updatedAt: pastDate(300),
    })
  );

  // Pinning, renaming, and hiding a chat thread succeed for members, fail for Eve
  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_ID).update({
      pinnedByUids: [ALICE_UID],
      updatedAt: pastDate(250),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_ID).update({
      title: "Launch Squad",
      updatedAt: pastDate(200),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_ID).update({
      pinnedMessageId: "msg_001",
      pinnedMessagePreview: "Hey Bob! Updated launch time!",
      updatedAt: pastDate(150),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_ID).update({
      lockedByUids: [ALICE_UID],
      pinnedByUids: [],
      updatedAt: pastDate(120),
    })
  );
  await assertFails(
    eveDb.collection("chats").doc(CHAT_ID).update({
      lockedByUids: [EVE_UID],
      updatedAt: pastDate(110),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_ID).update({
      hiddenByUids: [ALICE_UID],
      pinnedByUids: [],
      lockedByUids: [],
      updatedAt: pastDate(100),
    })
  );
  await assertFails(
    eveDb.collection("chats").doc(CHAT_ID).update({
      title: "Hijacked",
      updatedAt: pastDate(100),
    })
  );

  // Alice can delete her own message
  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_001").delete()
  );
});

// 5. DIRECT WEBRTC AUDIO CALLS & ICE CANDIDATES
test("WebRTC Calls & ICE Candidates: caller/callee signaling allowed, outsider denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const now = pastDate(5000);
  const callId = "call_001";

  const callPayload = {
    chatId: CHAT_ID,
    callerUid: ALICE_UID,
    calleeUid: BOB_UID,
    callerName: "Alice",
    calleeName: "Bob",
    memberUids: [ALICE_UID, BOB_UID],
    callType: "audio",
    status: "ringing",
    offerSdp: "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n",
    durationSeconds: 0,
    createdAt: now,
    updatedAt: now,
  };

  // Alice starts outgoing call
  await assertSucceeds(aliceDb.collection("calls").doc(callId).set(callPayload));
  // Eve cannot read or spoof
  await assertFails(eveDb.collection("calls").doc(callId).get());
  await assertFails(eveDb.collection("calls").doc(callId).update({ status: "ended", updatedAt: pastDate(1000) }));

  // Bob lists his calls and answers with SDP
  await assertSucceeds(bobDb.collection("calls").where("memberUids", "array-contains", BOB_UID).get());
  await assertSucceeds(
    bobDb.collection("calls").doc(callId).update({
      status: "answered",
      answerSdp: "v=0\r\no=- 456 2 IN IP4 127.0.0.1\r\n",
      updatedAt: pastDate(2000),
    })
  );

  // Both peers exchange ICE candidates; Eve blocked
  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).collection("iceCandidates").doc("cand_1").set({
      senderUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      sdpMid: "0",
      sdpMLineIndex: 0,
      sdp: "candidate:1 1 UDP 2122252543 192.0.2.1 54321 typ host",
      createdAt: pastDate(1500),
    })
  );
  await assertSucceeds(
    bobDb.collection("calls").doc(callId).collection("iceCandidates")
      .where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertFails(
    eveDb.collection("calls").doc(callId).collection("iceCandidates").doc("cand_eve").set({
      senderUid: EVE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      sdpMid: "0",
      sdpMLineIndex: 0,
      sdp: "candidate:99",
      createdAt: pastDate(1000),
    })
  );

  // End call with duration
  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).update({
      status: "ended",
      durationSeconds: 42,
      updatedAt: pastDate(500),
    })
  );

  // Direct WebRTC video call succeeds; invalid callType rejected
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_video_002").set({
      ...callPayload,
      callType: "video",
      createdAt: pastDate(400),
      updatedAt: pastDate(400),
    })
  );
  await assertFails(
    aliceDb.collection("calls").doc("call_invalid_003").set({
      ...callPayload,
      callType: "hologram",
      createdAt: pastDate(300),
      updatedAt: pastDate(300),
    })
  );
});

// 6. LINK TO PC WEB SESSIONS SUBCOLLECTION
test("PcLinkSession: owner-isolated create, list, status/label update, delete; immutable token/url", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(5000);

  const linkRef = aliceDb.collection("users").doc(ALICE_UID).collection("pcLinks").doc("link_pc_001");
  const validLink = {
    ownerUid: ALICE_UID,
    deviceLabel: "Work Laptop Chrome",
    pairingCode: "MJ4K-92PX",
    linkToken: "tkn_abc123def456ghi789jkl0",
    linkUrl: "https://ais-pre-5b67unt45s6jbqi2xakzh5-402680087625.europe-west1.run.app/link?token=tkn_abc123def456ghi789jkl0&code=MJ4K-92PX",
    status: "pending",
    createdAt: now,
    updatedAt: now,
  };

  // Unauthenticated and cross-user denied
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("pcLinks").doc("link_pc_001").set(validLink)
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("pcLinks").doc("link_pc_001").set(validLink)
  );

  // Owner create & list succeed
  await assertSucceeds(linkRef.set(validLink));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("pcLinks").get());

  // Cross-user read/list denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("pcLinks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("pcLinks").doc("link_pc_001").get());

  // Valid label & status update succeeds
  await assertSucceeds(
    linkRef.update({
      deviceLabel: "Home PC Edge",
      status: "linked",
      updatedAt: pastDate(1000),
    })
  );

  // Tampering linkUrl/linkToken or invalid URL scheme rejected
  await assertFails(
    linkRef.update({
      linkUrl: "https://evil.example.com/phish",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("pcLinks").doc("link_bad_002").set({
      ...validLink,
      linkUrl: "http://insecure.example.com/link",
      createdAt: pastDate(400),
      updatedAt: pastDate(400),
    })
  );

  // Owner delete succeeds
  await assertSucceeds(linkRef.delete());
});


