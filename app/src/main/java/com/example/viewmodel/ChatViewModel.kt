package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppNotificationHelper
import com.example.data.CallSession
import com.example.data.CallSettingsConfig
import com.example.data.ChatIdentityUtils
import com.example.data.ChatMessage
import com.example.data.ChatRepository
import com.example.data.ChatThread
import com.example.data.ContactVerificationState
import com.example.data.DirectoryEntry
import com.example.data.NotificationSettingsConfig
import com.example.data.PcLinkSession
import com.example.data.PublicProfile
import com.example.data.SavedContact
import com.example.data.TypingStatusConfig
import com.example.data.UiState
import com.example.data.UserAccount
import com.example.data.WebRtcAudioManager
import com.example.data.WebRtcConnectionStage
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    private val webRtcAudioManager: WebRtcAudioManager,
    private val notificationHelper: AppNotificationHelper,
    val currentUserId: String,
    private val pendingWelcomePhone: String = "",
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _pendingSelectedChat = MutableStateFlow<ChatThread?>(null)
    val pendingSelectedChat: StateFlow<ChatThread?> = _pendingSelectedChat.asStateFlow()

    private val _chatSearchQuery = MutableStateFlow("")
    val chatSearchQuery: StateFlow<String> = _chatSearchQuery.asStateFlow()

    private val _contactSearchQuery = MutableStateFlow("")
    val contactSearchQuery: StateFlow<String> = _contactSearchQuery.asStateFlow()

    // Automatic Registered Contact Verification live input & state
    private val _contactLookupInput = MutableStateFlow("")
    val contactLookupInput: StateFlow<String> = _contactLookupInput.asStateFlow()

    private val _contactVerificationState =
        MutableStateFlow<ContactVerificationState>(ContactVerificationState.Idle)
    val contactVerificationState: StateFlow<ContactVerificationState> =
        _contactVerificationState.asStateFlow()

    // Live cache of peer public profiles (for online status, avatars, display names)
    private val _peerProfiles = MutableStateFlow<Map<String, PublicProfile>>(emptyMap())
    val peerProfiles: StateFlow<Map<String, PublicProfile>> = _peerProfiles.asStateFlow()
    private val observedPeerJobs = mutableMapOf<String, Job>()

    // Direct WebRTC Audio Call state
    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

    private val _incomingRingingCall = MutableStateFlow<CallSession?>(null)
    val incomingRingingCall: StateFlow<CallSession?> = _incomingRingingCall.asStateFlow()

    private val _callStage = MutableStateFlow(WebRtcConnectionStage.IDLE)
    val callStage: StateFlow<WebRtcConnectionStage> = _callStage.asStateFlow()

    private val _isCallMuted = MutableStateFlow(false)
    val isCallMuted: StateFlow<Boolean> = _isCallMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(true)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _callElapsedSeconds = MutableStateFlow(0L)
    val callElapsedSeconds: StateFlow<Long> = _callElapsedSeconds.asStateFlow()

    val localVideoTrack: StateFlow<VideoTrack?> = webRtcAudioManager.localVideoTrack
    val remoteVideoTrack: StateFlow<VideoTrack?> = webRtcAudioManager.remoteVideoTrack
    val eglBaseContext: EglBase.Context? get() = webRtcAudioManager.eglBaseContext

    private var activeCallDocJob: Job? = null
    private var activeCallIceJob: Job? = null
    private var callTimerJob: Job? = null
    private val appliedIceCandidateIds = mutableSetOf<String>()
    private val dismissedIncomingCallIds = mutableSetOf<String>()

    private var typingStopJob: Job? = null
    private var lastTypingChatId: String? = null
    private var typingStatusConfig: TypingStatusConfig = TypingStatusConfig()
    private var callSettingsConfig: CallSettingsConfig = CallSettingsConfig()
    private var notificationSettingsConfig: NotificationSettingsConfig = NotificationSettingsConfig()
    private var isAppInForeground: Boolean = true
    private var chatsBaselineLoaded: Boolean = false
    private var callsBaselineLoaded: Boolean = false
    private val lastDeliveredKeyByChat = mutableMapOf<String, String>()
    private val lastNotifiedChatMessageKey = mutableMapOf<String, String>()
    private val activeIncomingCallNotifIds = mutableSetOf<String>()
    private val previousCallStatusById = mutableMapOf<String, String>()
    private val notifiedOutcomeCallIds = mutableSetOf<String>()

    val currentUserState: StateFlow<UiState<UserAccount?>> = repository.observeCurrentUser(currentUserId)
        .map<UserAccount?, UiState<UserAccount?>> { UiState.Success(it) }
        .catch { error -> emitStreamError("observeCurrentUser", error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val contactsState: StateFlow<UiState<List<SavedContact>>> = repository.observeContacts(currentUserId)
        .map<List<SavedContact>, UiState<List<SavedContact>>> { contacts ->
            contacts.forEach { ensureObservingPeer(it.contactUid) }
            UiState.Success(contacts)
        }
        .catch { error -> emitStreamError("observeContacts", error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val chatsState: StateFlow<UiState<List<ChatThread>>> = repository.observeChats(currentUserId)
        .map<List<ChatThread>, UiState<List<ChatThread>>> { chats ->
            val currentFingerprints = mutableMapOf<String, String>()
            chats.forEach { chat ->
                val peer = chat.peerUid(currentUserId)
                if (peer != currentUserId) ensureObservingPeer(peer)

                val fingerprint = "${chat.lastSenderId}_${chat.lastMessageAt?.seconds ?: 0L}_${chat.lastMessagePreview}_${chat.unreadCount}"
                currentFingerprints[chat.id] = fingerprint

                if (
                    chatsBaselineLoaded &&
                    !chat.isHiddenBy(currentUserId) &&
                    !chat.isSelfChat(currentUserId) &&
                    chat.lastSenderId.isNotBlank() &&
                    chat.lastSenderId != currentUserId &&
                    chat.lastMessagePreview.isNotBlank()
                ) {
                    val previousFingerprint = lastNotifiedChatMessageKey[chat.id]
                    val isCurrentlyViewingChat = isAppInForeground && _selectedChatId.value == chat.id
                    if (
                        previousFingerprint != fingerprint &&
                        !isCurrentlyViewingChat &&
                        notificationSettingsConfig.pushNotificationsEnabled &&
                        notificationSettingsConfig.receivedActivityNotifications &&
                        !notificationSettingsConfig.isChatMuted(chat.id)
                    ) {
                        val contacts = (contactsState.value as? UiState.Success)?.data.orEmpty()
                        val savedName = contacts.firstOrNull { it.contactUid == peer }?.savedName
                        val profileName = _peerProfiles.value[peer]?.displayName
                        val senderDisplayName = chat.title.takeIf { it.isNotBlank() }
                            ?: savedName?.takeIf { it.isNotBlank() }
                            ?: profileName?.takeIf { it.isNotBlank() }
                            ?: "MJ Chatty"
                        notificationHelper.showReceivedMessageNotification(
                            chatId = chat.id,
                            senderName = senderDisplayName,
                            previewText = chat.lastMessagePreview,
                            isLockedChat = chat.isLockedBy(currentUserId),
                        )
                    }
                }

                // Automatically transition incoming "sent" messages to "delivered" once received by this device
                if (
                    chat.lastSenderId.isNotBlank() &&
                    chat.lastSenderId != currentUserId &&
                    chat.lastMessageStatus == "sent" &&
                    chat.id != _selectedChatId.value
                ) {
                    val deliveryKey = "${chat.lastSenderId}_${chat.lastMessageAt?.seconds ?: 0L}_${chat.unreadCount}"
                    if (lastDeliveredKeyByChat[chat.id] != deliveryKey) {
                        lastDeliveredKeyByChat[chat.id] = deliveryKey
                        viewModelScope.launch {
                            repository.markChatAsDelivered(chat.id, currentUserId)
                        }
                    }
                }
            }
            lastNotifiedChatMessageKey.clear()
            lastNotifiedChatMessageKey.putAll(currentFingerprints)
            chatsBaselineLoaded = true
            UiState.Success(chats)
        }
        .catch { error -> emitStreamError("observeChats", error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val callsState: StateFlow<UiState<List<CallSession>>> = repository.observeCalls(currentUserId)
        .map<List<CallSession>, UiState<List<CallSession>>> { calls ->
            calls.forEach { call ->
                val peer = call.peerUid(currentUserId)
                if (peer.isNotBlank() && peer != currentUserId) ensureObservingPeer(peer)
            }
            processCallActivityNotifications(calls)
            updateIncomingCallCandidate(calls)
            UiState.Success(calls)
        }
        .catch { error -> emitStreamError("observeCalls", error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val pcLinksState: StateFlow<UiState<List<PcLinkSession>>> = repository.observePcLinks(currentUserId)
        .map<List<PcLinkSession>, UiState<List<PcLinkSession>>> { UiState.Success(it) }
        .catch { error -> emitStreamError("observePcLinks", error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val activeMessagesState: StateFlow<UiState<List<ChatMessage>>> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(chatId, currentUserId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error -> emitStreamError("observeMessages", error) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { notificationHelper.warmUpChannels() }
        }
        initializeAccount()
        observeLiveContactVerification()
    }

    private fun initializeAccount() {
        val firebaseUser = Firebase.auth.currentUser ?: return
        if (firebaseUser.uid != currentUserId) return
        viewModelScope.launch {
            val result = repository.ensureUserProfileInitialized(firebaseUser, pendingWelcomePhone)
            result.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to sync profile"
            }
        }
    }

    private fun observeLiveContactVerification() {
        viewModelScope.launch {
            _contactLookupInput
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(280L)
                .collect { query ->
                    if (query.isEmpty()) {
                        _contactVerificationState.value = ContactVerificationState.Idle
                        return@collect
                    }
                    _contactVerificationState.value = ContactVerificationState.Checking
                    _contactVerificationState.value = repository.verifyRegisteredContact(query)
                }
        }
    }

    private fun ensureObservingPeer(peerUid: String) {
        if (peerUid.isBlank() || observedPeerJobs.containsKey(peerUid)) return
        observedPeerJobs[peerUid] = viewModelScope.launch {
            repository.observePublicProfile(peerUid)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Peer profile stream warning for $peerUid", firestoreError ?: error)
                }
                .collect { profile ->
                    if (profile != null) {
                        _peerProfiles.update { current -> current + (peerUid to profile) }
                    }
                }
        }
    }

    private fun updateIncomingCallCandidate(calls: List<CallSession>) {
        val currentActiveId = _activeCall.value?.id
        val nowMillis = System.currentTimeMillis()
        val incoming = calls.firstOrNull { call ->
            call.calleeUid == currentUserId &&
                call.status == "ringing" &&
                call.id != currentActiveId &&
                call.id !in dismissedIncomingCallIds &&
                (call.createdAt == null || (nowMillis - call.createdAt.toDate().time) < 90_000L)
        }
        _incomingRingingCall.value = incoming
    }

    private fun processCallActivityNotifications(calls: List<CallSession>) {
        val currentCallIds = calls.map { it.id }.toSet()
        val nowMillis = System.currentTimeMillis()
        val contacts = (contactsState.value as? UiState.Success)?.data.orEmpty()

        // Cancel stale incoming notifications for calls no longer ringing
        val toCancel = activeIncomingCallNotifIds.filter { callId ->
            val call = calls.firstOrNull { it.id == callId }
            call == null || call.status != "ringing" || call.id in dismissedIncomingCallIds
        }
        toCancel.forEach { id ->
            notificationHelper.cancelIncomingCallNotification(id)
            activeIncomingCallNotifIds.remove(id)
        }

        calls.forEach { call ->
            val prevStatus = previousCallStatusById[call.id]
            val peerUid = call.peerUid(currentUserId)
            val resolvedPeerName = contacts.firstOrNull { it.contactUid == peerUid }?.savedName
                ?: _peerProfiles.value[peerUid]?.displayName?.takeIf { it.isNotBlank() }
                ?: call.peerName(currentUserId).ifBlank { "MJ Contact" }
            val mediaLabel = if (call.isVideoCall()) "Video" else "Audio"
            val isMuted = call.chatId.isNotBlank() && notificationSettingsConfig.isChatMuted(call.chatId)

            // 1. Incoming Ringing Call Push Notification
            val isFreshRingingIncoming = call.calleeUid == currentUserId &&
                call.status == "ringing" &&
                call.id != _activeCall.value?.id &&
                call.id !in dismissedIncomingCallIds &&
                (call.createdAt == null || (nowMillis - call.createdAt.toDate().time) < 90_000L)

            if (isFreshRingingIncoming) {
                if (
                    call.id !in activeIncomingCallNotifIds &&
                    notificationSettingsConfig.pushNotificationsEnabled &&
                    notificationSettingsConfig.incomingCallNotifications &&
                    !isMuted
                ) {
                    notificationHelper.showIncomingCallNotification(call, resolvedPeerName)
                    activeIncomingCallNotifIds.add(call.id)
                }
            }

            // 2. Received Call Outcome Notification (Missed / Received Ended while not actively looking)
            if (callsBaselineLoaded && call.calleeUid == currentUserId && call.id !in notifiedOutcomeCallIds) {
                if (call.status == "missed" && (prevStatus == "ringing" || prevStatus == null)) {
                    notifiedOutcomeCallIds.add(call.id)
                    if (
                        notificationSettingsConfig.pushNotificationsEnabled &&
                        notificationSettingsConfig.receivedActivityNotifications &&
                        !isMuted
                    ) {
                        notificationHelper.showReceivedCallActivityNotification(
                            call = call,
                            peerDisplayName = resolvedPeerName,
                            title = "Missed $mediaLabel Call",
                            body = "You missed a ${mediaLabel.lowercase()} call from $resolvedPeerName",
                        )
                    }
                } else if (call.status == "ended" && prevStatus == "answered" && !isAppInForeground) {
                    notifiedOutcomeCallIds.add(call.id)
                    if (
                        notificationSettingsConfig.pushNotificationsEnabled &&
                        notificationSettingsConfig.receivedActivityNotifications &&
                        !isMuted
                    ) {
                        notificationHelper.showReceivedCallActivityNotification(
                            call = call,
                            peerDisplayName = resolvedPeerName,
                            title = "Received $mediaLabel Call Ended",
                            body = "Call with $resolvedPeerName (${formatNotifDuration(call.durationSeconds)})",
                        )
                    }
                }
            }

            // 3. Outgoing Call Activity Summary when observed offline / status transitions externally
            if (callsBaselineLoaded && call.callerUid == currentUserId && prevStatus != null && prevStatus != call.status) {
                if (
                    call.status in listOf("ended", "declined", "missed") &&
                    call.id !in notifiedOutcomeCallIds &&
                    notificationSettingsConfig.pushNotificationsEnabled &&
                    notificationSettingsConfig.outgoingCallNotifications &&
                    !isMuted
                ) {
                    notifiedOutcomeCallIds.add(call.id)
                    val outcome = when (call.status) {
                        "declined" -> "Declined by $resolvedPeerName"
                        "missed" -> "No answer"
                        else -> if (call.durationSeconds > 0L) {
                            "Call ended • ${formatNotifDuration(call.durationSeconds)}"
                        } else {
                            "Call ended"
                        }
                    }
                    notificationHelper.showOutgoingCallCompletedNotification(call, resolvedPeerName, outcome)
                }
            }

            previousCallStatusById[call.id] = call.status
        }

        // Clean up tracked entries for deleted call logs
        previousCallStatusById.keys.retainAll(currentCallIds)
        callsBaselineLoaded = true
    }

    private fun formatNotifDuration(seconds: Long): String {
        val m = (seconds / 60L).toInt()
        val s = (seconds % 60L).toInt()
        return String.format(java.util.Locale.US, "%d:%02d", m, s)
    }

    fun setOnlinePresence(online: Boolean) {
        isAppInForeground = online
        viewModelScope.launch {
            repository.updatePresence(currentUserId, online)
        }
    }

    fun updateChatSearch(query: String) {
        _chatSearchQuery.value = query
    }

    fun updateContactSearch(query: String) {
        _contactSearchQuery.value = query
    }

    fun updateContactLookupInput(raw: String) {
        _contactLookupInput.value = raw
        if (raw.trim().isEmpty()) {
            _contactVerificationState.value = ContactVerificationState.Idle
        } else {
            _contactVerificationState.value = ContactVerificationState.Checking
        }
    }

    fun resetContactLookup() {
        _contactLookupInput.value = ""
        _contactVerificationState.value = ContactVerificationState.Idle
    }

    fun saveVerifiedContact(
        entry: DirectoryEntry,
        savedName: String,
        typedPhone: String,
        onSaved: (SavedContact) -> Unit,
    ) {
        viewModelScope.launch {
            repository.saveRegisteredContact(currentUserId, entry, savedName, typedPhone)
                .onSuccess { saved ->
                    ensureObservingPeer(saved.contactUid)
                    _statusMessage.value = "${saved.savedName} saved to your contacts"
                    resetContactLookup()
                    onSaved(saved)
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Unable to save contact"
                }
        }
    }

    fun deleteContact(contactUid: String) {
        viewModelScope.launch {
            repository.deleteContact(currentUserId, contactUid)
                .onSuccess { _statusMessage.value = "Contact removed" }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Failed to remove contact" }
        }
    }

    fun openChat(chatId: String?) {
        val previousChat = _selectedChatId.value
        if (previousChat != null && previousChat != chatId) {
            clearTypingIndicator(previousChat)
        }
        if (chatId == null || _pendingSelectedChat.value?.id != chatId) {
            _pendingSelectedChat.value = null
        }
        if (!chatId.isNullOrBlank()) {
            notificationHelper.cancelChatNotification(chatId)
        }
        _selectedChatId.value = chatId
    }

    fun openChatThread(chat: ChatThread) {
        val previousChat = _selectedChatId.value
        if (previousChat != null && previousChat != chat.id) {
            clearTypingIndicator(previousChat)
        }
        val peerUid = chat.peerUid(currentUserId)
        if (peerUid != currentUserId) {
            ensureObservingPeer(peerUid)
        }
        notificationHelper.cancelChatNotification(chat.id)
        _pendingSelectedChat.value = chat
        _selectedChatId.value = chat.id
    }

    fun openOrStartChatWithPeer(peerUid: String, title: String = "") {
        val chatId = ChatIdentityUtils.buildDirectChatId(currentUserId, peerUid)
        val isSelf = peerUid == currentUserId
        val members = if (isSelf) listOf(currentUserId) else listOf(currentUserId, peerUid).sorted()
        val optimisticChat = ChatThread(
            id = chatId,
            memberUids = members,
            chatType = "direct",
            title = title,
        )
        openChatThread(optimisticChat)

        viewModelScope.launch {
            if (!isSelf) ensureObservingPeer(peerUid)
            repository.getOrCreateDirectChat(currentUserId, peerUid, title)
                .onSuccess { resolvedChatId ->
                    _selectedChatId.value = resolvedChatId
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Could not open chat"
                }
        }
    }

    fun updateTypingStatusConfig(config: TypingStatusConfig) {
        typingStatusConfig = config
        if (!config.sendTypingIndicator) {
            lastTypingChatId?.let { activeChatId ->
                clearTypingIndicator(activeChatId)
            }
        }
    }

    fun updateCallSettingsConfig(config: CallSettingsConfig) {
        callSettingsConfig = config
    }

    fun updateNotificationSettingsConfig(config: NotificationSettingsConfig) {
        notificationSettingsConfig = config
    }

    fun sendTextMessage(chat: ChatThread, text: String) {
        if (text.isBlank()) return
        typingStopJob?.cancel()
        viewModelScope.launch {
            repository.sendTextMessage(chat.id, chat.memberUids, text)
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Failed to send message" }
        }
    }

    fun sendImageMessage(chat: ChatThread, imageDataUri: String, caption: String = "") {
        viewModelScope.launch {
            repository.sendImageMessage(chat.id, chat.memberUids, imageDataUri, caption)
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Failed to share photo" }
        }
    }

    fun sendVoiceMessage(chat: ChatThread, voiceBase64: String, durationMs: Long) {
        viewModelScope.launch {
            repository.sendVoiceMessage(chat.id, chat.memberUids, voiceBase64, durationMs)
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Failed to send voice message" }
        }
    }

    fun onComposerTextChanged(chatId: String, hasText: Boolean) {
        typingStopJob?.cancel()
        if (!typingStatusConfig.sendTypingIndicator || !hasText) {
            clearTypingIndicator(chatId)
            return
        }
        if (lastTypingChatId != chatId) {
            lastTypingChatId = chatId
            viewModelScope.launch { repository.setTypingState(chatId, currentUserId, true) }
        }
        val timeoutMillis = typingStatusConfig.typingTimeoutSeconds.coerceIn(2, 10) * 1000L
        typingStopJob = viewModelScope.launch {
            delay(timeoutMillis)
            clearTypingIndicator(chatId)
        }
    }

    private fun clearTypingIndicator(chatId: String) {
        typingStopJob?.cancel()
        if (lastTypingChatId == chatId) {
            lastTypingChatId = null
            viewModelScope.launch { repository.setTypingState(chatId, currentUserId, false) }
        }
    }

    fun markActiveChatRead(chat: ChatThread, messages: List<ChatMessage>) {
        val unreadPeerMessages = messages.filter { it.senderId != currentUserId && it.status != "read" }
        val shouldUpdateHeader = (chat.unreadByUid == currentUserId && chat.unreadCount > 0) ||
            (chat.lastSenderId.isNotBlank() && chat.lastSenderId != currentUserId && chat.lastMessageStatus != "read")
        if (unreadPeerMessages.isEmpty() && !shouldUpdateHeader) return
        viewModelScope.launch {
            repository.markChatAsRead(chat.id, currentUserId, unreadPeerMessages, shouldUpdateHeader)
        }
    }

    fun reactToMessage(chatId: String, messageId: String, emoji: String) {
        viewModelScope.launch {
            repository.setMessageReaction(chatId, messageId, emoji)
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Reaction failed" }
        }
    }

    // --- MESSAGE ACTIONS: EDIT / PIN / FORWARD / DELETE ---

    fun editMessage(chatId: String, messageId: String, newText: String, isLatestMessage: Boolean) {
        if (newText.isBlank()) return
        viewModelScope.launch {
            repository.editMessage(chatId, messageId, newText, isLatestMessage)
                .onSuccess { _statusMessage.value = "Message edited" }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Could not edit message" }
        }
    }

    fun togglePinMessage(chatId: String, message: ChatMessage) {
        val nextPin = !message.pinned
        viewModelScope.launch {
            repository.togglePinMessage(chatId, message, nextPin)
                .onSuccess { _statusMessage.value = if (nextPin) "Message pinned" else "Message unpinned" }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Could not update pin" }
        }
    }

    fun forwardMessageToChat(targetChat: ChatThread, sourceMessage: ChatMessage, targetLabel: String) {
        viewModelScope.launch {
            repository.forwardMessage(targetChat.id, targetChat.memberUids, sourceMessage)
                .onSuccess { _statusMessage.value = "Forwarded to $targetLabel" }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Could not forward message" }
        }
    }

    fun forwardMessageToContact(contact: SavedContact, sourceMessage: ChatMessage) {
        viewModelScope.launch {
            val chatResult = repository.getOrCreateDirectChat(currentUserId, contact.contactUid, contact.savedName)
            chatResult.onSuccess { targetChatId ->
                val members = if (contact.contactUid == currentUserId) {
                    listOf(currentUserId)
                } else {
                    listOf(currentUserId, contact.contactUid).sorted()
                }
                repository.forwardMessage(targetChatId, members, sourceMessage)
                    .onSuccess { _statusMessage.value = "Forwarded to ${contact.savedName}" }
                    .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Could not forward message" }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Could not open destination chat"
            }
        }
    }

    fun deleteMessage(chatId: String, messageId: String, isPinnedInChat: Boolean = false) {
        viewModelScope.launch {
            repository.deleteMessage(chatId, messageId, clearPinnedIfMatches = isPinnedInChat)
                .onSuccess { _statusMessage.value = "Message deleted" }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Could not delete message" }
        }
    }

    // --- CHAT ACTIONS: PIN / EDIT TITLE / FORWARD LATEST / DELETE ---

    fun togglePinChat(chat: ChatThread) {
        val nextPin = !chat.isPinnedBy(currentUserId)
        viewModelScope.launch {
            repository.togglePinChat(chat.id, currentUserId, chat.pinnedByUids, nextPin)
                .onSuccess { _statusMessage.value = if (nextPin) "Chat pinned to top" else "Chat unpinned" }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Could not pin chat" }
        }
    }

    fun renameChatTitle(chatId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameChatTitle(chatId, newTitle)
                .onSuccess {
                    _statusMessage.value = if (newTitle.isBlank()) "Chat name reset" else "Chat renamed"
                }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Could not rename chat" }
        }
    }

    fun forwardChatSummaryToChat(sourceChat: ChatThread, targetChat: ChatThread, targetLabel: String) {
        val previewText = sourceChat.lastMessagePreview.trim()
        if (previewText.isBlank()) {
            _statusMessage.value = "No message to forward from this chat"
            return
        }
        val syntheticMsg = ChatMessage(
            chatId = sourceChat.id,
            senderId = currentUserId,
            messageType = "text",
            text = previewText,
        )
        forwardMessageToChat(targetChat, syntheticMsg, targetLabel)
    }

    fun forwardChatSummaryToContact(sourceChat: ChatThread, contact: SavedContact) {
        val previewText = sourceChat.lastMessagePreview.trim()
        if (previewText.isBlank()) {
            _statusMessage.value = "No message to forward from this chat"
            return
        }
        val syntheticMsg = ChatMessage(
            chatId = sourceChat.id,
            senderId = currentUserId,
            messageType = "text",
            text = previewText,
        )
        forwardMessageToContact(contact, syntheticMsg)
    }

    fun toggleLockChat(chat: ChatThread, explicitLock: Boolean? = null) {
        val nextLock = explicitLock ?: !chat.isLockedBy(currentUserId)
        if (nextLock && _selectedChatId.value == chat.id) {
            openChat(null)
        }
        viewModelScope.launch {
            repository.toggleLockChat(
                chatId = chat.id,
                currentUid = currentUserId,
                currentLockedBy = chat.lockedByUids,
                currentPinnedBy = chat.pinnedByUids,
                lock = nextLock,
            ).onSuccess {
                _statusMessage.value = if (nextLock) "Chat moved to Locked Chats" else "Chat unlocked"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Could not update chat lock"
            }
        }
    }

    fun unlockAllLockedChats() {
        val locked = (chatsState.value as? UiState.Success)?.data.orEmpty()
            .filter { it.isLockedBy(currentUserId) }
        if (locked.isEmpty()) return
        viewModelScope.launch {
            locked.forEach { chat ->
                repository.toggleLockChat(
                    chatId = chat.id,
                    currentUid = currentUserId,
                    currentLockedBy = chat.lockedByUids,
                    currentPinnedBy = chat.pinnedByUids,
                    lock = false,
                )
            }
            _statusMessage.value = "All locked chats unlocked"
        }
    }

    fun deleteChat(chat: ChatThread) {
        if (_selectedChatId.value == chat.id) {
            openChat(null)
        }
        viewModelScope.launch {
            repository.deleteChatForUser(
                chatId = chat.id,
                currentUid = currentUserId,
                currentHiddenBy = chat.hiddenByUids,
                currentPinnedBy = chat.pinnedByUids,
                currentLockedBy = chat.lockedByUids,
            )
                .onSuccess { _statusMessage.value = "Chat deleted" }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Could not delete chat" }
        }
    }

    // --- DIRECT WEBRTC AUDIO & VIDEO CALLS ---

    fun startAudioCall(
        peerUid: String,
        peerName: String,
        peerAvatar: String,
        chatId: String = "",
    ) = startCall(
        peerUid = peerUid,
        peerName = peerName,
        peerAvatar = peerAvatar,
        chatId = chatId,
        callType = "audio",
    )

    fun startVideoCall(
        peerUid: String,
        peerName: String,
        peerAvatar: String,
        chatId: String = "",
    ) {
        if (!callSettingsConfig.videoCallingEnabled) {
            _statusMessage.value = "Video calling is disabled in Call Settings."
            return
        }
        startCall(
            peerUid = peerUid,
            peerName = peerName,
            peerAvatar = peerAvatar,
            chatId = chatId,
            callType = "video",
        )
    }

    fun startCall(
        peerUid: String,
        peerName: String,
        peerAvatar: String,
        chatId: String = "",
        callType: String = "audio",
    ) {
        val isVideo = callType.equals("video", ignoreCase = true)
        if (peerUid.isBlank() || peerUid == currentUserId) {
            _statusMessage.value = "Select another contact to start a call."
            return
        }
        if (_activeCall.value != null) {
            _statusMessage.value = "You are already on an active call."
            return
        }

        val myAccount = (currentUserState.value as? UiState.Success)?.data
        val myName = myAccount?.displayName?.ifBlank { Firebase.auth.currentUser?.displayName ?: "MJ User" }
            ?: Firebase.auth.currentUser?.displayName
            ?: "MJ User"
        val myAvatar = myAccount?.avatarData.orEmpty()
        val settings = callSettingsConfig
        val initialSpeaker = if (isVideo) settings.defaultSpeakerForVideo else false
        val initialCameraOn = if (isVideo) settings.startVideoWithCameraOn else false

        viewModelScope.launch {
            _isCallMuted.value = false
            _isSpeakerOn.value = initialSpeaker
            _isCameraEnabled.value = initialCameraOn
            _isFrontCamera.value = settings.preferFrontCamera
            _callElapsedSeconds.value = 0L
            _callStage.value = WebRtcConnectionStage.CALLING

            val createdResult = repository.createOutgoingCall(
                chatId = chatId,
                callerUid = currentUserId,
                calleeUid = peerUid,
                callerName = myName,
                calleeName = peerName,
                callerAvatar = myAvatar,
                calleeAvatar = peerAvatar,
                callType = if (isVideo) "video" else "audio",
            )

            createdResult.onFailure { err ->
                _callStage.value = WebRtcConnectionStage.IDLE
                _statusMessage.value = err.localizedMessage ?: "Could not start call"
                return@launch
            }

            val callSession = createdResult.getOrNull() ?: return@launch
            _activeCall.value = callSession
            observeActiveCallSignaling(callSession.id, callSession.memberUids)

            if (
                notificationSettingsConfig.pushNotificationsEnabled &&
                notificationSettingsConfig.outgoingCallNotifications &&
                !notificationSettingsConfig.isChatMuted(callSession.chatId)
            ) {
                notificationHelper.showOutgoingCallNotification(
                    call = callSession,
                    peerDisplayName = peerName,
                    stage = WebRtcConnectionStage.CALLING,
                    connected = false,
                )
            }

            webRtcAudioManager.startOutgoingCall(
                isVideo = isVideo,
                preferFrontCamera = settings.preferFrontCamera,
                hdVideo = settings.hdVideoQuality,
                startCameraOn = initialCameraOn,
                speakerOn = initialSpeaker,
                onLocalOfferSdp = { offerSdp ->
                    viewModelScope.launch {
                        repository.updateCallOfferSdp(callSession.id, offerSdp)
                        if (_callStage.value == WebRtcConnectionStage.CALLING) {
                            _callStage.value = WebRtcConnectionStage.RINGING
                            if (
                                notificationSettingsConfig.pushNotificationsEnabled &&
                                notificationSettingsConfig.outgoingCallNotifications &&
                                !notificationSettingsConfig.isChatMuted(callSession.chatId)
                            ) {
                                notificationHelper.showOutgoingCallNotification(
                                    call = callSession,
                                    peerDisplayName = peerName,
                                    stage = WebRtcConnectionStage.RINGING,
                                    connected = false,
                                )
                            }
                        }
                    }
                },
                onLocalIceCandidate = { sdpMid, sdpMLineIndex, sdp ->
                    viewModelScope.launch {
                        repository.addIceCandidate(
                            callId = callSession.id,
                            memberUids = callSession.memberUids,
                            sdpMid = sdpMid,
                            sdpMLineIndex = sdpMLineIndex,
                            sdp = sdp,
                        )
                    }
                },
                onStageChanged = { stage ->
                    _callStage.value = stage
                    if (
                        notificationSettingsConfig.pushNotificationsEnabled &&
                        notificationSettingsConfig.outgoingCallNotifications &&
                        !notificationSettingsConfig.isChatMuted(callSession.chatId)
                    ) {
                        notificationHelper.showOutgoingCallNotification(
                            call = callSession,
                            peerDisplayName = peerName,
                            stage = stage,
                            connected = stage == WebRtcConnectionStage.CONNECTED,
                        )
                    }
                    if (stage == WebRtcConnectionStage.CONNECTED) {
                        startCallDurationTimer()
                    }
                },
                onError = { msg ->
                    viewModelScope.launch {
                        _statusMessage.value = msg
                        hangUpActiveCall()
                    }
                },
            )
        }
    }

    fun acceptIncomingCall(call: CallSession) {
        val isVideo = call.isVideoCall() && callSettingsConfig.videoCallingEnabled
        val settings = callSettingsConfig
        val initialSpeaker = if (isVideo) settings.defaultSpeakerForVideo else false
        val initialCameraOn = if (isVideo) settings.startVideoWithCameraOn else false

        dismissedIncomingCallIds.add(call.id)
        activeIncomingCallNotifIds.remove(call.id)
        notificationHelper.cancelIncomingCallNotification(call.id)
        _incomingRingingCall.value = null
        _activeCall.value = call
        _isCallMuted.value = false
        _isSpeakerOn.value = initialSpeaker
        _isCameraEnabled.value = initialCameraOn
        _isFrontCamera.value = settings.preferFrontCamera
        _callElapsedSeconds.value = 0L
        _callStage.value = WebRtcConnectionStage.CONNECTING

        observeActiveCallSignaling(call.id, call.memberUids)

        viewModelScope.launch {
            var offerSdp = call.offerSdp
            var attempts = 0
            while (offerSdp.isBlank() && attempts < 15) {
                delay(200L)
                offerSdp = _activeCall.value?.offerSdp.orEmpty()
                attempts++
            }
            if (offerSdp.isBlank()) {
                _statusMessage.value = "Call offer expired before connecting."
                hangUpActiveCall()
                return@launch
            }

            webRtcAudioManager.answerIncomingCall(
                remoteOfferSdp = offerSdp,
                isVideo = isVideo,
                preferFrontCamera = settings.preferFrontCamera,
                hdVideo = settings.hdVideoQuality,
                startCameraOn = initialCameraOn,
                speakerOn = initialSpeaker,
                onLocalAnswerSdp = { answerSdp ->
                    viewModelScope.launch {
                        repository.submitCallAnswer(call.id, answerSdp)
                        startCallDurationTimer()
                    }
                },
                onLocalIceCandidate = { sdpMid, sdpMLineIndex, sdp ->
                    viewModelScope.launch {
                        repository.addIceCandidate(
                            callId = call.id,
                            memberUids = call.memberUids,
                            sdpMid = sdpMid,
                            sdpMLineIndex = sdpMLineIndex,
                            sdp = sdp,
                        )
                    }
                },
                onStageChanged = { stage ->
                    _callStage.value = stage
                    if (stage == WebRtcConnectionStage.CONNECTED) {
                        startCallDurationTimer()
                    }
                },
                onError = { msg ->
                    viewModelScope.launch {
                        _statusMessage.value = msg
                        hangUpActiveCall()
                    }
                },
            )
        }
    }

    fun declineIncomingCall(call: CallSession) {
        dismissedIncomingCallIds.add(call.id)
        activeIncomingCallNotifIds.remove(call.id)
        notificationHelper.cancelIncomingCallNotification(call.id)
        _incomingRingingCall.value = null
        viewModelScope.launch {
            repository.updateCallStatus(call.id, status = "declined", durationSeconds = 0L)
        }
    }

    fun hangUpActiveCall() {
        val current = _activeCall.value
        val elapsed = _callElapsedSeconds.value
        teardownActiveCall()

        if (current != null) {
            val finalStatus = when {
                current.status == "ringing" && current.callerUid == currentUserId -> "missed"
                else -> "ended"
            }
            if (
                current.callerUid == currentUserId &&
                current.id !in notifiedOutcomeCallIds &&
                notificationSettingsConfig.pushNotificationsEnabled &&
                notificationSettingsConfig.outgoingCallNotifications &&
                !notificationSettingsConfig.isChatMuted(current.chatId)
            ) {
                notifiedOutcomeCallIds.add(current.id)
                val outcome = when {
                    finalStatus == "missed" -> "Canceled before answer"
                    elapsed > 0L -> "Call completed • ${formatNotifDuration(elapsed)}"
                    else -> "Call ended"
                }
                notificationHelper.showOutgoingCallCompletedNotification(
                    call = current,
                    peerDisplayName = current.peerName(currentUserId),
                    outcomeLabel = outcome,
                )
            }
            viewModelScope.launch {
                repository.updateCallStatus(current.id, status = finalStatus, durationSeconds = elapsed)
            }
        }
    }

    fun toggleCallMute() {
        val next = !_isCallMuted.value
        _isCallMuted.value = next
        webRtcAudioManager.setMuted(next)
    }

    fun toggleCallSpeaker() {
        val next = !_isSpeakerOn.value
        _isSpeakerOn.value = next
        webRtcAudioManager.setSpeakerphone(next)
    }

    fun toggleCallCamera() {
        val next = !_isCameraEnabled.value
        _isCameraEnabled.value = next
        webRtcAudioManager.setCameraEnabled(next)
    }

    fun switchCallCamera() {
        webRtcAudioManager.switchCamera { isFront ->
            _isFrontCamera.value = isFront
        }
    }

    fun deleteCallLog(callId: String) {
        viewModelScope.launch {
            repository.deleteCallLog(callId)
                .onSuccess { _statusMessage.value = "Call removed from history" }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Could not delete call entry" }
        }
    }

    private fun observeActiveCallSignaling(callId: String, memberUids: List<String>) {
        activeCallDocJob?.cancel()
        activeCallIceJob?.cancel()
        appliedIceCandidateIds.clear()

        activeCallDocJob = viewModelScope.launch {
            repository.observeCall(callId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Call signaling observer error", firestoreError ?: error)
                }
                .collect { remote ->
                    if (remote == null) return@collect
                    _activeCall.value = remote

                    // Caller receives remote SDP Answer
                    if (remote.callerUid == currentUserId && remote.answerSdp.isNotBlank()) {
                        if (_callStage.value == WebRtcConnectionStage.CALLING ||
                            _callStage.value == WebRtcConnectionStage.RINGING
                        ) {
                            _callStage.value = WebRtcConnectionStage.CONNECTING
                        }
                        webRtcAudioManager.onRemoteAnswerReceived(remote.answerSdp) { errMsg ->
                            _statusMessage.value = errMsg
                        }
                        startCallDurationTimer()
                    }

                    // Remote peer ended or declined call
                    if (remote.status in listOf("ended", "declined", "missed")) {
                        val reason = when (remote.status) {
                            "declined" -> "${remote.peerName(currentUserId)} declined the call"
                            "missed" -> "Call missed"
                            else -> "Call ended"
                        }
                        teardownActiveCall()
                        _statusMessage.value = reason
                    }
                }
        }

        activeCallIceJob = viewModelScope.launch {
            repository.observeIceCandidates(callId, currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "ICE candidate stream warning", firestoreError ?: error)
                }
                .collect { candidates ->
                    candidates.forEach { candidate ->
                        if (
                            candidate.senderUid != currentUserId &&
                            candidate.id.isNotBlank() &&
                            appliedIceCandidateIds.add(candidate.id)
                        ) {
                            webRtcAudioManager.addRemoteIceCandidate(
                                sdpMid = candidate.sdpMid,
                                sdpMLineIndex = candidate.sdpMLineIndex,
                                sdp = candidate.sdp,
                            )
                        }
                    }
                }
        }
    }

    private fun startCallDurationTimer() {
        if (callTimerJob?.isActive == true) return
        callTimerJob = viewModelScope.launch {
            while (_activeCall.value != null) {
                delay(1000L)
                _callElapsedSeconds.update { it + 1L }
            }
        }
    }

    private fun teardownActiveCall() {
        activeCallDocJob?.cancel()
        activeCallDocJob = null
        activeCallIceJob?.cancel()
        activeCallIceJob = null
        callTimerJob?.cancel()
        callTimerJob = null
        appliedIceCandidateIds.clear()
        webRtcAudioManager.endCall()
        _activeCall.value = null
        _callStage.value = WebRtcConnectionStage.IDLE
        _isCallMuted.value = false
        _isSpeakerOn.value = false
        _callElapsedSeconds.value = 0L
    }

    fun saveProfile(
        displayName: String,
        username: String,
        phoneNumber: String,
        about: String,
        avatarData: String,
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            repository.updateUserProfile(
                uid = currentUserId,
                displayName = displayName,
                rawUsername = username,
                rawPhone = phoneNumber,
                about = about,
                avatarData = avatarData,
            ).onSuccess {
                _statusMessage.value = "Profile saved"
                onSuccess()
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Unable to save profile"
            }
        }
    }

    fun createPcLink(baseUrl: String, deviceLabel: String = "PC Web Browser") {
        viewModelScope.launch {
            repository.createPcLinkSession(currentUserId, baseUrl, deviceLabel)
                .onSuccess { _statusMessage.value = "New PC linking URL generated" }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Could not generate linking URL" }
        }
    }

    fun ensureDefaultPcLink(baseUrl: String) {
        val current = (pcLinksState.value as? UiState.Success)?.data ?: return
        if (current.none { it.status != "revoked" }) {
            viewModelScope.launch {
                repository.createPcLinkSession(currentUserId, baseUrl, "PC Web Browser")
            }
        }
    }

    fun updatePcLinkStatus(linkId: String, status: String) {
        viewModelScope.launch {
            repository.updatePcLinkSession(currentUserId, linkId, status = status)
                .onSuccess {
                    _statusMessage.value = when (status) {
                        "linked" -> "PC browser marked as linked"
                        "revoked" -> "PC link revoked"
                        else -> "PC link ready to pair"
                    }
                }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Could not update link status" }
        }
    }

    fun renamePcLink(linkId: String, newLabel: String) {
        viewModelScope.launch {
            repository.updatePcLinkSession(currentUserId, linkId, deviceLabel = newLabel)
                .onSuccess { _statusMessage.value = "PC device name updated" }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Could not rename device" }
        }
    }

    fun deletePcLink(linkId: String) {
        viewModelScope.launch {
            repository.deletePcLinkSession(currentUserId, linkId)
                .onSuccess { _statusMessage.value = "PC link removed" }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Could not remove PC link" }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun showMessage(message: String) {
        _statusMessage.value = message
    }

    private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<UiState<T>>.emitStreamError(
        opName: String,
        error: Throwable,
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error in $opName", rootError)
        emit(UiState.Error(rootError.localizedMessage ?: "Sync error occurred"))
    }

    override fun onCleared() {
        super.onCleared()
        typingStopJob?.cancel()
        teardownActiveCall()
        webRtcAudioManager.release()
        observedPeerJobs.values.forEach { it.cancel() }
        observedPeerJobs.clear()
    }

    private companion object {
        const val TAG = "ChatViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
