package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun Map<String, Any?>.stripNulls(): Map<String, Any> =
        this.filterValues { it != null } as Map<String, Any>

    // --- USER PROFILE & INITIALIZATION ---

    suspend fun ensureUserProfileInitialized(
        user: FirebaseUser,
        pendingPhoneNumber: String = "",
    ): Result<UserAccount> {
        val uid = user.uid
        val userPath = "users/$uid"
        return try {
            val userRef = db.collection("users").document(uid)
            val existingSnap = userRef.get().await()
            val normalizedPhone = ChatIdentityUtils.normalizePhoneNumber(pendingPhoneNumber)

            if (existingSnap.exists()) {
                val existing = existingSnap.toObject(
                    UserAccount::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                ) ?: UserAccount(id = uid, userId = uid)

                // Link pending phone from welcome screen if user didn't have one yet
                if (normalizedPhone.isNotBlank() && existing.phoneNumber != normalizedPhone) {
                    updateUserProfile(
                        uid = uid,
                        displayName = existing.displayName.ifBlank { user.displayName ?: "MJ User" },
                        rawUsername = existing.username.ifBlank {
                            ChatIdentityUtils.defaultUsernameFor(existing.displayName, uid)
                        },
                        rawPhone = normalizedPhone,
                        about = existing.about,
                        avatarData = existing.avatarData,
                    )
                } else {
                    updatePresence(uid, online = true)
                }
                val refreshed = userRef.get().await().toObject(
                    UserAccount::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                ) ?: existing
                return Result.success(refreshed)
            }

            val displayName = (user.displayName?.trim().takeIf { !it.isNullOrBlank() } ?: "MJ Chatty User")
                .take(60)
            var candidateUsername = ChatIdentityUtils.defaultUsernameFor(displayName, uid)
            val lookupDoc = db.collection("directory")
                .document(ChatIdentityUtils.usernameLookupKey(candidateUsername))
                .get()
                .await()
            if (lookupDoc.exists() && lookupDoc.getString("ownerUid") != uid) {
                candidateUsername = "mj_${uid.lowercase().filter { it.isLetterOrDigit() }.take(10)}"
            }
            val photoUrl = user.photoUrl?.toString().orEmpty().take(350000)
            val defaultAbout = "Hey there! I'm on MJ Chatty"

            val userPayload = mapOf(
                "userId" to uid,
                "displayName" to displayName,
                "username" to candidateUsername,
                "phoneNumber" to normalizedPhone.takeIf { it.isNotBlank() },
                "about" to defaultAbout,
                "avatarData" to photoUrl.takeIf { it.isNotBlank() },
                "online" to true,
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).stripNulls()

            val publicPayload = mapOf(
                "ownerUid" to uid,
                "displayName" to displayName,
                "username" to candidateUsername,
                "about" to defaultAbout,
                "avatarData" to photoUrl.takeIf { it.isNotBlank() },
                "online" to true,
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).stripNulls()

            val batch = db.batch()
            batch.set(userRef, userPayload)
            batch.set(db.collection("publicProfiles").document(uid), publicPayload)

            val usernameDirPayload = mapOf(
                "ownerUid" to uid,
                "lookupType" to "username",
                "displayName" to displayName,
                "username" to candidateUsername,
                "avatarData" to photoUrl.takeIf { it.isNotBlank() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).stripNulls()
            batch.set(
                db.collection("directory").document(ChatIdentityUtils.usernameLookupKey(candidateUsername)),
                usernameDirPayload,
            )

            if (normalizedPhone.isNotBlank()) {
                val phoneDirPayload = mapOf(
                    "ownerUid" to uid,
                    "lookupType" to "phone",
                    "displayName" to displayName,
                    "username" to candidateUsername,
                    "avatarData" to photoUrl.takeIf { it.isNotBlank() },
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).stripNulls()
                batch.set(
                    db.collection("directory").document(ChatIdentityUtils.phoneLookupKey(normalizedPhone)),
                    phoneDirPayload,
                )
            }

            batch.commit().await()
            val created = userRef.get().await().toObject(
                UserAccount::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ) ?: UserAccount(
                id = uid,
                userId = uid,
                displayName = displayName,
                username = candidateUsername,
                phoneNumber = normalizedPhone,
                about = defaultAbout,
                avatarData = photoUrl,
                online = true,
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userPath)
            Result.failure(e)
        }
    }

    fun observeCurrentUser(uid: String): Flow<UserAccount?> = flow {
        require(uid.isNotBlank())
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observePublicProfile(profileUid: String): Flow<PublicProfile?> = flow {
        require(profileUid.isNotBlank())
        val path = "publicProfiles/$profileUid"
        emitAll(
            db.collection("publicProfiles").document(profileUid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser != null) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getPublicProfile(profileUid: String): PublicProfile? {
        if (profileUid.isBlank()) return null
        return try {
            db.collection("publicProfiles").document(profileUid)
                .get()
                .await()
                .toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "publicProfiles/$profileUid")
            null
        }
    }

    suspend fun updatePresence(uid: String, online: Boolean): Result<Unit> {
        val path = "users/$uid"
        return try {
            val signedInUid = requireUserId()
            check(signedInUid == uid) { "Refusing cross-user presence update" }
            val presenceUpdate = mapOf(
                "online" to online,
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            val userDoc = db.collection("users").document(uid)
            val publicDoc = db.collection("publicProfiles").document(uid)
            val userSnap = userDoc.get().await()
            if (userSnap.exists()) {
                val publicSnap = publicDoc.get().await()
                val batch = db.batch()
                batch.update(userDoc, presenceUpdate)
                if (publicSnap.exists()) {
                    batch.update(publicDoc, presenceUpdate)
                } else {
                    val account = userSnap.toObject(
                        UserAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                    if (account != null && account.displayName.isNotBlank() && account.username.isNotBlank()) {
                        batch.set(
                            publicDoc,
                            mapOf(
                                "ownerUid" to uid,
                                "displayName" to account.displayName.take(60),
                                "username" to account.username.take(24),
                                "about" to account.about.take(160).takeIf { it.isNotBlank() },
                                "avatarData" to account.avatarData.take(350000).takeIf { it.isNotBlank() },
                                "online" to online,
                                "lastSeenAt" to FieldValue.serverTimestamp(),
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp(),
                            ).stripNulls(),
                        )
                    }
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser?.uid == uid) {
                handleFirestoreError(e, OperationType.UPDATE, path)
            }
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        uid: String,
        displayName: String,
        rawUsername: String,
        rawPhone: String,
        about: String,
        avatarData: String,
    ): Result<Unit> {
        val path = "users/$uid"
        return try {
            val signedInUid = requireUserId()
            check(signedInUid == uid) { "Refusing cross-user profile update" }

            val cleanName = displayName.trim().take(60)
            require(cleanName.isNotEmpty()) { "Profile name cannot be empty." }

            val cleanUsername = ChatIdentityUtils.sanitizeUsername(rawUsername)
            require(ChatIdentityUtils.isValidUsername(cleanUsername)) {
                "Username must be 3–24 characters using lowercase letters, numbers, or underscores."
            }

            val cleanPhone = if (rawPhone.isBlank()) "" else ChatIdentityUtils.normalizePhoneNumber(rawPhone)
            if (rawPhone.isNotBlank() && cleanPhone.isBlank()) {
                throw IllegalArgumentException("Enter a valid phone number with at least 7 digits.")
            }

            val cleanAbout = about.trim().take(160)
            val cleanAvatar = avatarData.take(350000)

            val userRef = db.collection("users").document(uid)
            val currentAccount = userRef.get().await().toObject(
                UserAccount::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )

            // Verify new username isn't claimed by someone else
            val newUsernameKey = ChatIdentityUtils.usernameLookupKey(cleanUsername)
            val newUsernameRef = db.collection("directory").document(newUsernameKey)
            val existingHandleSnap = newUsernameRef.get().await()
            if (existingHandleSnap.exists() && existingHandleSnap.getString("ownerUid") != uid) {
                throw IllegalStateException("@$cleanUsername is already taken. Try another username.")
            }

            // Check phone lookup ownership if provided
            val newPhoneKey = if (cleanPhone.isNotBlank()) ChatIdentityUtils.phoneLookupKey(cleanPhone) else null
            if (newPhoneKey != null) {
                val existingPhoneSnap = db.collection("directory").document(newPhoneKey).get().await()
                if (existingPhoneSnap.exists() && existingPhoneSnap.getString("ownerUid") != uid) {
                    throw IllegalStateException("This phone number is already linked to another MJ Chatty account.")
                }
            }

            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "displayName" to cleanName,
                    "username" to cleanUsername,
                    "phoneNumber" to cleanPhone,
                    "about" to cleanAbout,
                    "avatarData" to cleanAvatar,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            batch.update(
                db.collection("publicProfiles").document(uid),
                mapOf(
                    "displayName" to cleanName,
                    "username" to cleanUsername,
                    "about" to cleanAbout,
                    "avatarData" to cleanAvatar,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )

            val oldUsername = currentAccount?.username.orEmpty()
            if (oldUsername.isNotBlank() && oldUsername != cleanUsername) {
                val oldKey = ChatIdentityUtils.usernameLookupKey(oldUsername)
                val oldSnap = db.collection("directory").document(oldKey).get().await()
                if (oldSnap.exists() && oldSnap.getString("ownerUid") == uid) {
                    batch.delete(db.collection("directory").document(oldKey))
                }
            }

            if (existingHandleSnap.exists()) {
                batch.update(
                    newUsernameRef,
                    mapOf(
                        "displayName" to cleanName,
                        "username" to cleanUsername,
                        "avatarData" to cleanAvatar,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            } else {
                batch.set(
                    newUsernameRef,
                    mapOf(
                        "ownerUid" to uid,
                        "lookupType" to "username",
                        "displayName" to cleanName,
                        "username" to cleanUsername,
                        "avatarData" to cleanAvatar.takeIf { it.isNotBlank() },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ).stripNulls(),
                )
            }

            val oldPhone = currentAccount?.phoneNumber.orEmpty()
            if (oldPhone.isNotBlank() && oldPhone != cleanPhone) {
                val oldPhoneKey = ChatIdentityUtils.phoneLookupKey(oldPhone)
                val oldPhoneSnap = db.collection("directory").document(oldPhoneKey).get().await()
                if (oldPhoneSnap.exists() && oldPhoneSnap.getString("ownerUid") == uid) {
                    batch.delete(db.collection("directory").document(oldPhoneKey))
                }
            }

            if (newPhoneKey != null) {
                val newPhoneRef = db.collection("directory").document(newPhoneKey)
                val phoneExists = newPhoneRef.get().await().exists()
                if (phoneExists) {
                    batch.update(
                        newPhoneRef,
                        mapOf(
                            "displayName" to cleanName,
                            "username" to cleanUsername,
                            "avatarData" to cleanAvatar,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                } else {
                    batch.set(
                        newPhoneRef,
                        mapOf(
                            "ownerUid" to uid,
                            "lookupType" to "phone",
                            "displayName" to cleanName,
                            "username" to cleanUsername,
                            "avatarData" to cleanAvatar.takeIf { it.isNotBlank() },
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ).stripNulls(),
                    )
                }
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- AUTOMATIC REGISTERED CONTACT VERIFICATION ---

    suspend fun verifyRegisteredContact(rawQuery: String): ContactVerificationState {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return ContactVerificationState.Idle

        return try {
            requireUserId()
            val lookupId = if (ChatIdentityUtils.isLikelyPhoneQuery(trimmed)) {
                val normalizedPhone = ChatIdentityUtils.normalizePhoneNumber(trimmed)
                if (normalizedPhone.isBlank()) {
                    return ContactVerificationState.InvalidFormat("Keep typing a full phone number (7+ digits) or @username")
                }
                ChatIdentityUtils.phoneLookupKey(normalizedPhone)
            } else {
                val cleanUsername = ChatIdentityUtils.sanitizeUsername(trimmed)
                if (cleanUsername.length < 3) {
                    return ContactVerificationState.InvalidFormat("Type at least 3 characters for @username or 7+ digits for phone")
                }
                ChatIdentityUtils.usernameLookupKey(cleanUsername)
            }

            val snap = db.collection("directory").document(lookupId).get().await()
            if (snap.exists()) {
                val entry = snap.toObject(
                    DirectoryEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
                if (entry != null && entry.ownerUid.isNotBlank()) {
                    ContactVerificationState.Verified(entry = entry, matchedQuery = trimmed)
                } else {
                    ContactVerificationState.NotRegistered(trimmed)
                }
            } else {
                ContactVerificationState.NotRegistered(trimmed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "directory/$trimmed")
            ContactVerificationState.NotRegistered(trimmed)
        }
    }

    // --- SAVED CONTACTS ---

    fun observeContacts(uid: String): Flow<List<SavedContact>> = flow {
        require(uid.isNotBlank())
        val path = "users/$uid/contacts"
        emitAll(
            db.collection("users").document(uid).collection("contacts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedContact::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ).sortedBy { it.savedName.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveRegisteredContact(
        uid: String,
        verifiedEntry: DirectoryEntry,
        savedName: String,
        typedPhone: String,
    ): Result<SavedContact> {
        val contactUid = verifiedEntry.ownerUid
        val path = "users/$uid/contacts/$contactUid"
        return try {
            val signedInUid = requireUserId()
            check(signedInUid == uid) { "Refusing cross-user contact save" }
            val cleanSavedName = savedName.trim().ifBlank { verifiedEntry.displayName }.take(60)
            require(cleanSavedName.isNotEmpty()) { "Contact name cannot be empty." }

            val cleanPhone = if (typedPhone.isNotBlank() && ChatIdentityUtils.isLikelyPhoneQuery(typedPhone)) {
                ChatIdentityUtils.normalizePhoneNumber(typedPhone)
            } else ""

            val docRef = db.collection("users").document(uid).collection("contacts").document(contactUid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "savedName" to cleanSavedName,
                        "contactUsername" to verifiedEntry.username,
                        "contactPhone" to cleanPhone,
                        "avatarData" to verifiedEntry.avatarData,
                        "registered" to true,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "contactUid" to contactUid,
                        "savedName" to cleanSavedName,
                        "contactUsername" to verifiedEntry.username,
                        "contactPhone" to cleanPhone.takeIf { it.isNotBlank() },
                        "avatarData" to verifiedEntry.avatarData.takeIf { it.isNotBlank() },
                        "registered" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ).stripNulls(),
                ).await()
            }

            Result.success(
                SavedContact(
                    id = contactUid,
                    contactUid = contactUid,
                    savedName = cleanSavedName,
                    contactUsername = verifiedEntry.username,
                    contactPhone = cleanPhone,
                    avatarData = verifiedEntry.avatarData,
                    registered = true,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteContact(uid: String, contactUid: String): Result<Unit> {
        val path = "users/$uid/contacts/$contactUid"
        return try {
            val signedInUid = requireUserId()
            check(signedInUid == uid)
            db.collection("users").document(uid).collection("contacts").document(contactUid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- CHATS & REAL-TIME MESSAGES ---

    fun observeChats(uid: String): Flow<List<ChatThread>> = flow {
        require(uid.isNotBlank())
        val path = "chats"
        emitAll(
            db.collection("chats")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatThread::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ).sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getOrCreateDirectChat(
        currentUid: String,
        peerUid: String,
        customTitle: String = "",
    ): Result<String> {
        val chatId = ChatIdentityUtils.buildDirectChatId(currentUid, peerUid)
        val path = "chats/$chatId"
        var op = OperationType.GET
        return try {
            val signedInUid = requireUserId()
            check(signedInUid == currentUid)
            val chatRef = db.collection("chats").document(chatId)
            val snap = chatRef.get().await()
            if (!snap.exists()) {
                op = OperationType.CREATE
                val members = if (currentUid == peerUid) listOf(currentUid) else listOf(currentUid, peerUid).sorted()
                val title = customTitle.take(60)
                chatRef.set(
                    mapOf(
                        "memberUids" to members,
                        "chatType" to "direct",
                        "title" to title.takeIf { it.isNotBlank() },
                        "pinnedByUids" to emptyList<String>(),
                        "hiddenByUids" to emptyList<String>(),
                        "lockedByUids" to emptyList<String>(),
                        "lastSenderId" to currentUid,
                        "lastMessagePreview" to "Say hello on MJ Chatty!",
                        "lastMessageType" to "text",
                        "lastMessageStatus" to "sent",
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "typingUid" to "",
                        "unreadByUid" to "",
                        "unreadCount" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ).stripNulls(),
                ).await()
            } else {
                val existing = snap.toObject(
                    ChatThread::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
                if (existing != null && currentUid in existing.hiddenByUids) {
                    op = OperationType.UPDATE
                    chatRef.update(
                        mapOf(
                            "hiddenByUids" to existing.hiddenByUids.filterNot { it == currentUid },
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
            }
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, op, path)
            Result.failure(e)
        }
    }

    fun observeMessages(chatId: String, currentUid: String): Flow<List<ChatMessage>> = flow {
        require(chatId.isNotBlank() && currentUid.isNotBlank())
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("memberUids", currentUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == currentUid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendTextMessage(
        chatId: String,
        memberUids: List<String>,
        text: String,
    ): Result<Unit> = sendMessageInternal(
        chatId = chatId,
        memberUids = memberUids,
        messageType = "text",
        text = text.trim().take(2000),
        imageData = null,
        voiceData = null,
        voiceDurationMs = null,
        preview = text.trim().take(180),
    )

    suspend fun sendImageMessage(
        chatId: String,
        memberUids: List<String>,
        imageDataUri: String,
        caption: String = "",
    ): Result<Unit> = sendMessageInternal(
        chatId = chatId,
        memberUids = memberUids,
        messageType = "image",
        text = caption.trim().take(500),
        imageData = imageDataUri.take(500000),
        voiceData = null,
        voiceDurationMs = null,
        preview = if (caption.isNotBlank()) "📷 ${caption.trim().take(120)}" else "📷 Photo",
    )

    suspend fun sendVoiceMessage(
        chatId: String,
        memberUids: List<String>,
        voiceBase64: String,
        durationMs: Long,
    ): Result<Unit> {
        val secs = (durationMs / 1000L).coerceAtLeast(1L)
        return sendMessageInternal(
            chatId = chatId,
            memberUids = memberUids,
            messageType = "voice",
            text = "",
            imageData = null,
            voiceData = voiceBase64.take(500000),
            voiceDurationMs = durationMs.coerceIn(0L, 300_000L),
            preview = "🎤 Voice message (${secs}s)",
        )
    }

    private suspend fun sendMessageInternal(
        chatId: String,
        memberUids: List<String>,
        messageType: String,
        text: String,
        imageData: String?,
        voiceData: String?,
        voiceDurationMs: Long?,
        preview: String,
        forwarded: Boolean = false,
    ): Result<Unit> {
        val path = "chats/$chatId/messages"
        return try {
            val uid = requireUserId()
            require(chatId.isNotBlank())
            if (messageType == "text") require(text.isNotBlank())

            val chatRef = db.collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document()
            val peerUid = memberUids.firstOrNull { it != uid }.orEmpty()
            val isSelf = peerUid.isBlank()
            // Start at "sent" (single tick); transitions to "delivered" (double grey ticks) when received
            // and "read" (double blue ticks) when opened by the recipient.
            val initialStatus = if (isSelf) "read" else "sent"

            val msgPayload = mapOf(
                "chatId" to chatId,
                "senderId" to uid,
                "memberUids" to memberUids,
                "messageType" to messageType,
                "text" to text,
                "imageData" to imageData,
                "voiceData" to voiceData,
                "voiceDurationMs" to voiceDurationMs,
                "status" to initialStatus,
                "forwarded" to if (forwarded) true else null,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).stripNulls()

            val chatUpdate = mapOf(
                "lastSenderId" to uid,
                "lastMessagePreview" to preview.take(300),
                "lastMessageType" to messageType,
                "lastMessageStatus" to initialStatus,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "typingUid" to "",
                "typingUpdatedAt" to FieldValue.serverTimestamp(),
                "unreadByUid" to peerUid,
                "unreadCount" to if (isSelf) 0L else FieldValue.increment(1L),
                "hiddenByUids" to emptyList<String>(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )

            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(chatRef, chatUpdate)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun forwardMessage(
        targetChatId: String,
        targetMemberUids: List<String>,
        sourceMessage: ChatMessage,
    ): Result<Unit> {
        val preview = when (sourceMessage.messageType) {
            "image" -> if (sourceMessage.text.isNotBlank()) "↪ 📷 ${sourceMessage.text.take(120)}" else "↪ 📷 Photo"
            "voice" -> {
                val secs = (sourceMessage.voiceDurationMs / 1000L).coerceAtLeast(1L)
                "↪ 🎤 Voice message (${secs}s)"
            }
            else -> "↪ ${sourceMessage.text.trim().take(180)}"
        }
        return sendMessageInternal(
            chatId = targetChatId,
            memberUids = targetMemberUids,
            messageType = sourceMessage.messageType,
            text = sourceMessage.text.take(2000),
            imageData = sourceMessage.imageData.takeIf { it.isNotBlank() }?.take(500000),
            voiceData = sourceMessage.voiceData.takeIf { it.isNotBlank() }?.take(500000),
            voiceDurationMs = if (sourceMessage.messageType == "voice") {
                sourceMessage.voiceDurationMs.coerceIn(0L, 300_000L)
            } else null,
            preview = preview,
            forwarded = true,
        )
    }

    suspend fun setTypingState(chatId: String, currentUid: String, typing: Boolean): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == currentUid)
            db.collection("chats").document(chatId).update(
                mapOf(
                    "typingUid" to if (typing) uid else "",
                    "typingUpdatedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markChatAsDelivered(
        chatId: String,
        currentUid: String,
    ): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == currentUid)
            val chatRef = db.collection("chats").document(chatId)
            val messagesSnap = chatRef.collection("messages")
                .whereArrayContains("memberUids", uid)
                .get()
                .await()

            val pendingDelivered = messagesSnap.documents.filter { doc ->
                val sender = doc.getString("senderId").orEmpty()
                val status = doc.getString("status").orEmpty()
                sender.isNotBlank() && sender != uid && status == "sent"
            }

            val batch = db.batch()
            pendingDelivered.take(30).forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf(
                        "status" to "delivered",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            batch.update(
                chatRef,
                mapOf(
                    "lastMessageStatus" to "delivered",
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markChatAsRead(
        chatId: String,
        currentUid: String,
        unreadPeerMessages: List<ChatMessage>,
        shouldUpdateThreadHeader: Boolean,
    ): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == currentUid)
            if (unreadPeerMessages.isEmpty() && !shouldUpdateThreadHeader) {
                return Result.success(Unit)
            }
            val batch = db.batch()
            val chatRef = db.collection("chats").document(chatId)
            unreadPeerMessages.take(25).forEach { msg ->
                if (msg.id.isNotBlank()) {
                    batch.update(
                        chatRef.collection("messages").document(msg.id),
                        mapOf(
                            "status" to "read",
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                }
            }
            if (shouldUpdateThreadHeader) {
                batch.update(
                    chatRef,
                    mapOf(
                        "lastMessageStatus" to "read",
                        "unreadByUid" to "",
                        "unreadCount" to 0L,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setMessageReaction(chatId: String, messageId: String, emoji: String): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            requireUserId()
            db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "reaction" to emoji.take(16),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun editMessage(
        chatId: String,
        messageId: String,
        newText: String,
        isLatestMessage: Boolean,
    ): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            requireUserId()
            val cleanText = newText.trim().take(2000)
            require(cleanText.isNotEmpty()) { "Message cannot be empty." }

            val chatRef = db.collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document(messageId)
            val batch = db.batch()
            batch.update(
                msgRef,
                mapOf(
                    "text" to cleanText,
                    "edited" to true,
                    "editedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            if (isLatestMessage) {
                batch.update(
                    chatRef,
                    mapOf(
                        "lastMessagePreview" to cleanText.take(300),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun togglePinMessage(
        chatId: String,
        message: ChatMessage,
        pin: Boolean,
    ): Result<Unit> {
        val path = "chats/$chatId/messages/${message.id}"
        return try {
            requireUserId()
            val chatRef = db.collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document(message.id)
            val preview = when (message.messageType) {
                "image" -> if (message.text.isNotBlank()) "📷 ${message.text.take(120)}" else "📷 Photo"
                "voice" -> "🎤 Voice message"
                else -> message.text.trim().take(200)
            }
            val batch = db.batch()
            batch.update(
                msgRef,
                mapOf(
                    "pinned" to pin,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            batch.update(
                chatRef,
                mapOf(
                    "pinnedMessageId" to if (pin) message.id else "",
                    "pinnedMessagePreview" to if (pin) preview else "",
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(chatId: String, messageId: String, clearPinnedIfMatches: Boolean = false): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            requireUserId()
            val chatRef = db.collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document(messageId)
            val batch = db.batch()
            batch.delete(msgRef)
            if (clearPinnedIfMatches) {
                batch.update(
                    chatRef,
                    mapOf(
                        "pinnedMessageId" to "",
                        "pinnedMessagePreview" to "",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- CHAT THREAD MANAGEMENT (PIN / EDIT / DELETE) ---

    suspend fun togglePinChat(
        chatId: String,
        currentUid: String,
        currentPinnedBy: List<String>,
        pin: Boolean,
    ): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == currentUid)
            val updatedPinned = if (pin) {
                (currentPinnedBy + uid).distinct().take(20)
            } else {
                currentPinnedBy.filterNot { it == uid }
            }
            db.collection("chats").document(chatId).update(
                mapOf(
                    "pinnedByUids" to updatedPinned,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun renameChatTitle(chatId: String, newTitle: String): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            requireUserId()
            val clean = newTitle.trim().take(60)
            db.collection("chats").document(chatId).update(
                mapOf(
                    "title" to clean,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleLockChat(
        chatId: String,
        currentUid: String,
        currentLockedBy: List<String>,
        currentPinnedBy: List<String>,
        lock: Boolean,
    ): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == currentUid)
            val updatedLocked = if (lock) {
                (currentLockedBy + uid).distinct().take(20)
            } else {
                currentLockedBy.filterNot { it == uid }
            }
            val updatedPinned = if (lock) {
                currentPinnedBy.filterNot { it == uid }
            } else {
                currentPinnedBy
            }
            db.collection("chats").document(chatId).update(
                mapOf(
                    "lockedByUids" to updatedLocked,
                    "pinnedByUids" to updatedPinned,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteChatForUser(
        chatId: String,
        currentUid: String,
        currentHiddenBy: List<String>,
        currentPinnedBy: List<String>,
        currentLockedBy: List<String> = emptyList(),
    ): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == currentUid)
            val updatedHidden = (currentHiddenBy + uid).distinct().take(20)
            val updatedPinned = currentPinnedBy.filterNot { it == uid }
            val updatedLocked = currentLockedBy.filterNot { it == uid }
            db.collection("chats").document(chatId).update(
                mapOf(
                    "hiddenByUids" to updatedHidden,
                    "pinnedByUids" to updatedPinned,
                    "lockedByUids" to updatedLocked,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- DIRECT WEBRTC AUDIO CALLS & SIGNALING ---

    fun observeCalls(uid: String): Flow<List<CallSession>> = flow {
        require(uid.isNotBlank())
        val path = "calls"
        emitAll(
            db.collection("calls")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CallSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCall(callId: String): Flow<CallSession?> = flow {
        require(callId.isNotBlank())
        val path = "calls/$callId"
        emitAll(
            db.collection("calls").document(callId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser != null) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeIceCandidates(callId: String, currentUid: String): Flow<List<IceCandidateRecord>> = flow {
        require(callId.isNotBlank() && currentUid.isNotBlank())
        val path = "calls/$callId/iceCandidates"
        emitAll(
            db.collection("calls").document(callId)
                .collection("iceCandidates")
                .whereArrayContains("memberUids", currentUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        IceCandidateRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == currentUid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createOutgoingCall(
        chatId: String,
        callerUid: String,
        calleeUid: String,
        callerName: String,
        calleeName: String,
        callerAvatar: String,
        calleeAvatar: String,
        callType: String = "audio",
    ): Result<CallSession> {
        val callRef = db.collection("calls").document()
        val callId = callRef.id
        val path = "calls/$callId"
        return try {
            val uid = requireUserId()
            check(uid == callerUid)
            require(calleeUid.isNotBlank() && calleeUid != callerUid) {
                "Select a contact to place a WebRTC call."
            }
            val safeCallType = if (callType.equals("video", ignoreCase = true)) "video" else "audio"
            val members = listOf(callerUid, calleeUid).sorted()
            val resolvedChatId = chatId.ifBlank { ChatIdentityUtils.buildDirectChatId(callerUid, calleeUid) }
            val cleanCallerName = callerName.trim().ifBlank { "MJ User" }.take(60)
            val cleanCalleeName = calleeName.trim().ifBlank { "MJ Contact" }.take(60)

            val payload = mapOf(
                "chatId" to resolvedChatId,
                "callerUid" to callerUid,
                "calleeUid" to calleeUid,
                "callerName" to cleanCallerName,
                "calleeName" to cleanCalleeName,
                "callerAvatar" to callerAvatar.take(350000).takeIf { it.isNotBlank() },
                "calleeAvatar" to calleeAvatar.take(350000).takeIf { it.isNotBlank() },
                "memberUids" to members,
                "callType" to safeCallType,
                "status" to "ringing",
                "durationSeconds" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).stripNulls()

            callRef.set(payload).await()
            Result.success(
                CallSession(
                    id = callId,
                    chatId = resolvedChatId,
                    callerUid = callerUid,
                    calleeUid = calleeUid,
                    callerName = cleanCallerName,
                    calleeName = cleanCalleeName,
                    callerAvatar = callerAvatar,
                    calleeAvatar = calleeAvatar,
                    memberUids = members,
                    callType = safeCallType,
                    status = "ringing",
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCallOfferSdp(callId: String, offerSdp: String): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "offerSdp" to offerSdp.take(40000),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitCallAnswer(callId: String, answerSdp: String): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to "answered",
                    "answerSdp" to answerSdp.take(40000),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addIceCandidate(
        callId: String,
        memberUids: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        sdp: String,
    ): Result<Unit> {
        val path = "calls/$callId/iceCandidates"
        return try {
            val uid = requireUserId()
            db.collection("calls").document(callId)
                .collection("iceCandidates")
                .document()
                .set(
                    mapOf(
                        "senderUid" to uid,
                        "memberUids" to memberUids,
                        "sdpMid" to sdpMid.take(64),
                        "sdpMLineIndex" to sdpMLineIndex.coerceIn(0, 32),
                        "sdp" to sdp.take(4000),
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(
        callId: String,
        status: String,
        durationSeconds: Long = 0L,
    ): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to status,
                    "durationSeconds" to durationSeconds.coerceIn(0L, 86400L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCallLog(callId: String): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            db.collection("calls").document(callId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- LINK TO PC WEB SESSIONS ---

    fun observePcLinks(uid: String): Flow<List<PcLinkSession>> = flow {
        require(uid.isNotBlank())
        val path = "users/$uid/pcLinks"
        emitAll(
            db.collection("users").document(uid).collection("pcLinks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PcLinkSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ).sortedByDescending { it.createdAt ?: it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createPcLinkSession(
        uid: String,
        baseUrl: String,
        deviceLabel: String = "PC Web Browser",
    ): Result<PcLinkSession> {
        val linkRef = db.collection("users").document(uid).collection("pcLinks").document()
        val path = "users/$uid/pcLinks/${linkRef.id}"
        return try {
            val signedInUid = requireUserId()
            check(signedInUid == uid) { "Refusing cross-user PC link creation" }
            val cleanLabel = deviceLabel.trim().ifBlank { "PC Web Browser" }.take(40)
            val token = PcLinkUtils.generateLinkToken()
            val pairingCode = PcLinkUtils.generatePairingCode()
            val linkUrl = PcLinkUtils.buildPcLinkUrl(
                baseUrl = baseUrl,
                token = token,
                pairingCode = pairingCode,
                uid = uid,
            )

            val payload = mapOf(
                "ownerUid" to uid,
                "deviceLabel" to cleanLabel,
                "pairingCode" to pairingCode,
                "linkToken" to token,
                "linkUrl" to linkUrl,
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            linkRef.set(payload).await()
            Result.success(
                PcLinkSession(
                    id = linkRef.id,
                    ownerUid = uid,
                    deviceLabel = cleanLabel,
                    pairingCode = pairingCode,
                    linkToken = token,
                    linkUrl = linkUrl,
                    status = "pending",
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePcLinkSession(
        uid: String,
        linkId: String,
        deviceLabel: String? = null,
        status: String? = null,
    ): Result<Unit> {
        val path = "users/$uid/pcLinks/$linkId"
        return try {
            val signedInUid = requireUserId()
            check(signedInUid == uid) { "Refusing cross-user PC link update" }
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (deviceLabel != null) {
                val cleanLabel = deviceLabel.trim().take(40)
                require(cleanLabel.isNotEmpty()) { "Device label cannot be empty." }
                updates["deviceLabel"] = cleanLabel
            }
            if (status != null) {
                require(status in listOf("pending", "linked", "revoked")) { "Invalid link status" }
                updates["status"] = status
            }
            db.collection("users").document(uid).collection("pcLinks").document(linkId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePcLinkSession(uid: String, linkId: String): Result<Unit> {
        val path = "users/$uid/pcLinks/$linkId"
        return try {
            val signedInUid = requireUserId()
            check(signedInUid == uid) { "Refusing cross-user PC link deletion" }
            db.collection("users").document(uid).collection("pcLinks").document(linkId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
