package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.ChatMessage
import com.example.data.ChatThread
import com.example.data.ImageMediaUtils
import com.example.data.PublicProfile
import com.example.data.SavedContact
import com.example.data.TypingStatusConfig
import com.example.data.UiState
import com.example.data.VoiceNotePlayer
import com.example.data.VoiceNoteRecorder
import com.example.ui.components.ConfiguredTypingIndicator
import com.example.ui.components.ForwardTargetDialog
import com.example.ui.components.FullScreenImageDialog
import com.example.ui.components.MessageStatusTicks
import com.example.ui.components.MjAvatar
import com.example.ui.components.TypingDotsIndicator
import com.example.ui.components.formatDateSeparator
import com.example.ui.components.formatDurationSeconds
import com.example.ui.components.formatLastSeen
import com.example.ui.components.formatMessageTime
import com.example.ui.theme.LocalMjColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val QuickEmojiCategories = listOf(
    "🔥" to listOf("😀", "😂", "🥰", "😎", "🤩", "🥳", "🙌", "🔥", "⚡", "✨", "💙", "💚"),
    "👍" to listOf("👍", "👏", "🙏", "🤝", "✌️", "🤙", "💪", "🫶", "🎉", "🚀", "💯", "👑"),
    "💬" to listOf("😊", "😍", "🤔", "😅", "😭", "😮", "🤯", "😴", "☕", "🎵", "📸", "🎤"),
)

private val ReactionOptions = listOf("❤️", "😂", "🔥", "👍", "😮", "🙌")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chat: ChatThread,
    currentUserId: String,
    peerTitle: String,
    peerAvatar: String,
    peerUsername: String,
    peerAbout: String,
    isPeerOnline: Boolean,
    peerProfile: PublicProfile?,
    allChats: List<ChatThread>,
    allContacts: List<SavedContact>,
    peerProfiles: Map<String, PublicProfile>,
    typingStatusConfig: TypingStatusConfig,
    videoCallingEnabled: Boolean,
    isChatMuted: Boolean,
    messagesState: UiState<List<ChatMessage>>,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onSendText: (String) -> Unit,
    onSendImage: (String, String) -> Unit,
    onSendVoice: (String, Long) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onMarkRead: (List<ChatMessage>) -> Unit,
    onReactMessage: (String, String) -> Unit,
    onEditMessage: (messageId: String, newText: String, isLatest: Boolean) -> Unit,
    onTogglePinMessage: (ChatMessage) -> Unit,
    onForwardMessageToChat: (ChatThread, ChatMessage, String) -> Unit,
    onForwardMessageToContact: (SavedContact, ChatMessage) -> Unit,
    onDeleteMessage: (messageId: String, isPinnedInChat: Boolean) -> Unit,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onToggleLockChat: () -> Unit,
    onTogglePinChat: () -> Unit,
    onToggleMuteChat: () -> Unit,
    onRenameChat: (String) -> Unit,
    onDeleteChat: () -> Unit,
    onShowError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (showBackButton) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mjColors = LocalMjColors.current
    val listState = rememberLazyListState()

    var draftText by remember(chat.id) { mutableStateOf("") }
    var editingMessage by remember(chat.id) { mutableStateOf<ChatMessage?>(null) }
    var forwardTargetMessage by remember(chat.id) { mutableStateOf<ChatMessage?>(null) }
    var showEmojiTray by remember(chat.id) { mutableStateOf(false) }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var previewImagePair by remember { mutableStateOf<Pair<String, String>?>(null) }
    var actionTargetMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var pendingPermissionAction by remember { mutableStateOf<String?>(null) }
    var showMoreMenu by remember(chat.id) { mutableStateOf(false) }
    var showRenameDialog by remember(chat.id) { mutableStateOf(false) }
    var renameDraft by remember(chat.id) { mutableStateOf("") }
    var showDeleteChatConfirm by remember(chat.id) { mutableStateOf(false) }
    var showContactInfoDialog by remember(chat.id) { mutableStateOf(false) }
    var showChatSearchBar by remember(chat.id) { mutableStateOf(false) }
    var messageSearchQuery by remember(chat.id) { mutableStateOf("") }

    // Voice recorder & playback state
    val voiceRecorder = remember(context) { VoiceNoteRecorder(context) }
    val voicePlayer = remember(context) { VoiceNotePlayer(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingElapsedMs by remember { mutableLongStateOf(0L) }
    var playingMessageId by remember { mutableStateOf<String?>(null) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }

    DisposableEffect(chat.id) {
        onDispose {
            voiceRecorder.stopAndDiscard()
            voicePlayer.stop()
            onTypingChanged(false)
        }
    }

    // Recording timer tick
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingElapsedMs = 0L
            while (isRecording && recordingElapsedMs < 60_000L) {
                delay(200L)
                recordingElapsedMs += 200L
            }
            if (isRecording) {
                val encoded = voiceRecorder.stopAndEncode()
                isRecording = false
                if (encoded != null) {
                    onSendVoice(encoded.first, encoded.second)
                }
            }
        }
    }

    // Playback progress tick
    LaunchedEffect(playingMessageId) {
        while (playingMessageId != null && voicePlayer.isPlaying()) {
            playbackProgress = (voicePlayer.currentPositionMs().toFloat() / voicePlayer.durationMs().toFloat())
                .coerceIn(0f, 1f)
            delay(120L)
        }
        if (playingMessageId != null && !voicePlayer.isPlaying()) {
            playingMessageId = null
            playbackProgress = 0f
        }
    }

    // Zero-permission Android Photo Picker for image sharing
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            isUploadingMedia = true
            scope.launch {
                val dataUri = ImageMediaUtils.encodeImageUriToDataUri(context, uri, maxDimension = 720, jpegQuality = 70)
                isUploadingMedia = false
                if (dataUri != null) {
                    onSendImage(dataUri, draftText.trim())
                    draftText = ""
                    onTypingChanged(false)
                } else {
                    onShowError("Could not process that image. Please try another photo.")
                }
            }
        }
    }

    // Runtime RECORD_AUDIO permission for real voice messages & direct WebRTC audio calls
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (granted) {
            if (action == "call") {
                onStartAudioCall()
            } else {
                val started = voiceRecorder.startRecording()
                if (started) {
                    isRecording = true
                    showEmojiTray = false
                } else {
                    onShowError("Microphone unavailable on this device.")
                }
            }
        } else {
            onShowError(context.getString(R.string.permission_audio_denied))
        }
    }

    // Runtime RECORD_AUDIO + CAMERA permissions for direct WebRTC video calls
    val videoCallPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val audioOk = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraOk = results[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (audioOk && cameraOk) {
            onStartVideoCall()
        } else {
            onShowError(context.getString(R.string.permission_video_call_denied))
        }
    }

    fun launchAudioCallWithPermission() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onStartAudioCall()
        } else {
            pendingPermissionAction = "call"
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun launchVideoCallWithPermission() {
        val audioOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        val cameraOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (audioOk && cameraOk) {
            onStartVideoCall()
        } else {
            videoCallPermissionsLauncher.launch(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            )
        }
    }

    val isPeerTyping = typingStatusConfig.showPeerTypingIndicator &&
        chat.typingUid.isNotBlank() &&
        chat.typingUid != currentUserId
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val pinnedMessage = remember(messages, chat.pinnedMessageId) {
        messages.firstOrNull { it.id == chat.pinnedMessageId && chat.pinnedMessageId.isNotBlank() }
            ?: messages.lastOrNull { it.pinned }
    }
    val unreadPeerCount = remember(messages, currentUserId) {
        messages.count { it.senderId != currentUserId && it.status != "read" }
    }

    // Reliably mark incoming messages & chat header as read whenever this chat is open
    LaunchedEffect(chat.id, messages.lastOrNull()?.id, unreadPeerCount, chat.unreadCount, chat.lastMessageStatus) {
        if (messages.isNotEmpty() || chat.unreadCount > 0 || (chat.lastSenderId != currentUserId && chat.lastMessageStatus != "read")) {
            onMarkRead(messages)
        }
    }

    LaunchedEffect(messages.size, chat.id) {
        if (messages.isNotEmpty()) {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems > 0) {
                listState.animateScrollToItem(totalItems - 1)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = mjColors.chatWallpaperBg,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("chat_detail_back_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to chats",
                                )
                            }
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = peerAvatar.isNotBlank()) {
                                    previewImagePair = peerAvatar to peerTitle
                                }
                                .padding(vertical = 2.dp, horizontal = 2.dp)
                                .testTag("chat_detail_header_profile"),
                        ) {
                            MjAvatar(
                                name = peerTitle,
                                avatarData = peerAvatar,
                                size = 42.dp,
                                online = isPeerOnline,
                                showOnlineIndicator = true,
                            )
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    if (chat.isLockedBy(currentUserId)) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = "Locked chat",
                                            tint = mjColors.neonEmerald,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                    Text(
                                        text = peerTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                when {
                                    isPeerTyping -> {
                                        ConfiguredTypingIndicator(
                                            style = typingStatusConfig.displayStyle,
                                            label = stringResource(R.string.status_typing),
                                            accentColor = mjColors.neonEmerald,
                                            textStyle = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    isPeerOnline -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(mjColors.neonEmerald.copy(alpha = 0.58f)),
                                            )
                                            Text(
                                                text = stringResource(R.string.status_online),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = mjColors.neonEmerald.copy(alpha = 0.72f),
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                    else -> {
                                        Text(
                                            text = formatLastSeen(peerProfile?.lastSeenAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        if (!chat.isSelfChat(currentUserId)) {
                            if (videoCallingEnabled) {
                                IconButton(
                                    onClick = { launchVideoCallWithPermission() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("chat_detail_video_call_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Videocam,
                                        contentDescription = "Start WebRTC video call with $peerTitle",
                                        tint = mjColors.neonCyan,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { launchAudioCallWithPermission() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_detail_audio_call_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = "Start WebRTC audio call with $peerTitle",
                                    tint = mjColors.neonEmerald,
                                )
                            }
                        }

                        // Three-dots (⋮) Chat Options Overflow Menu
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_detail_more_options_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Chat options",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }

                            val isLockedNow = chat.isLockedBy(currentUserId)
                            val isPinnedNow = chat.isPinnedBy(currentUserId)
                            val latestMessage = messages.lastOrNull()
                            val latestOwnTextMessage = messages.lastOrNull {
                                it.senderId == currentUserId && it.messageType == "text"
                            }
                            val latestOwnMessage = messages.lastOrNull { it.senderId == currentUserId }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier.testTag("chat_detail_options_dropdown"),
                            ) {
                                DropdownMenuItem(
                                    text = { Text("View Contact") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Person,
                                            contentDescription = null,
                                            tint = mjColors.neonCyan,
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showContactInfoDialog = true
                                    },
                                    modifier = Modifier.testTag("chat_detail_menu_view_contact"),
                                )

                                DropdownMenuItem(
                                    text = { Text(if (showChatSearchBar) "Close Search" else "Search Messages") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Search,
                                            contentDescription = null,
                                            tint = mjColors.neonCyan,
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showChatSearchBar = !showChatSearchBar
                                        if (!showChatSearchBar) messageSearchQuery = ""
                                    },
                                    modifier = Modifier.testTag("chat_detail_menu_search"),
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isChatMuted) "Unmute Notifications" else "Mute Notifications"
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isChatMuted) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                                            contentDescription = null,
                                            tint = if (isChatMuted) mjColors.neonEmerald else mjColors.neonAmber,
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onToggleMuteChat()
                                    },
                                    modifier = Modifier.testTag("chat_detail_menu_mute"),
                                )

                                if (!chat.isSelfChat(currentUserId)) {
                                    HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.35f))
                                    DropdownMenuItem(
                                        text = { Text("Audio Call") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Call,
                                                contentDescription = null,
                                                tint = mjColors.neonEmerald,
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            launchAudioCallWithPermission()
                                        },
                                        modifier = Modifier.testTag("chat_detail_menu_audio_call"),
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (videoCallingEnabled) "Video Call" else "Video Call (Off in Settings)"
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Videocam,
                                                contentDescription = null,
                                                tint = if (videoCallingEnabled) mjColors.neonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            if (videoCallingEnabled) {
                                                launchVideoCallWithPermission()
                                            } else {
                                                onStartVideoCall()
                                            }
                                        },
                                        modifier = Modifier.testTag("chat_detail_menu_video_call"),
                                    )
                                }

                                HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.35f))

                                DropdownMenuItem(
                                    text = { Text(if (isLockedNow) "Unlock Chat" else "Lock Chat") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isLockedNow) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = mjColors.neonEmerald,
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onToggleLockChat()
                                    },
                                    modifier = Modifier.testTag("chat_detail_menu_lock"),
                                )

                                DropdownMenuItem(
                                    text = { Text(if (isPinnedNow) "Unpin Chat" else "Pin Chat") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.PushPin,
                                            contentDescription = null,
                                            tint = mjColors.neonCyan,
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onTogglePinChat()
                                    },
                                    modifier = Modifier.testTag("chat_detail_menu_pin"),
                                )

                                DropdownMenuItem(
                                    text = { Text("Edit Chat Name") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = null,
                                            tint = mjColors.neonAmber,
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        renameDraft = chat.title.ifBlank { peerTitle }
                                        showRenameDialog = true
                                    },
                                    modifier = Modifier.testTag("chat_detail_menu_rename"),
                                )

                                DropdownMenuItem(
                                    text = { Text("Forward Latest Message") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Forward,
                                            contentDescription = null,
                                            tint = mjColors.neonBlue,
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        if (latestMessage != null) {
                                            forwardTargetMessage = latestMessage
                                        } else {
                                            onShowError("No messages in this chat to forward yet.")
                                        }
                                    },
                                    modifier = Modifier.testTag("chat_detail_menu_forward"),
                                )

                                if (latestMessage != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (latestMessage.pinned || latestMessage.id == chat.pinnedMessageId) {
                                                    "Unpin Latest Message"
                                                } else {
                                                    "Pin Latest Message"
                                                }
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.PushPin,
                                                contentDescription = null,
                                                tint = mjColors.neonCyan,
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onTogglePinMessage(latestMessage)
                                        },
                                        modifier = Modifier.testTag("chat_detail_menu_pin_msg"),
                                    )
                                }

                                if (latestOwnTextMessage != null) {
                                    DropdownMenuItem(
                                        text = { Text("Edit Latest Message") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = null,
                                                tint = mjColors.neonAmber,
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            editingMessage = latestOwnTextMessage
                                            draftText = latestOwnTextMessage.text
                                        },
                                        modifier = Modifier.testTag("chat_detail_menu_edit_msg"),
                                    )
                                }

                                if (latestOwnMessage != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Delete Latest Message",
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            val isPinnedMsg = latestOwnMessage.pinned || latestOwnMessage.id == chat.pinnedMessageId
                                            onDeleteMessage(latestOwnMessage.id, isPinnedMsg)
                                        },
                                        modifier = Modifier.testTag("chat_detail_menu_delete_msg"),
                                    )
                                }

                                HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.35f))

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Delete Chat",
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showDeleteChatConfirm = true
                                    },
                                    modifier = Modifier.testTag("chat_detail_menu_delete"),
                                )
                            }
                        }
                    },
                )
                HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.3f))

                // Inline Search Messages Bar (toggled from 3-dots menu)
                AnimatedVisibility(
                    visible = showChatSearchBar,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = messageSearchQuery,
                            onValueChange = { messageSearchQuery = it },
                            placeholder = { Text("Search messages in chat…") },
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = mjColors.neonCyan)
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (messageSearchQuery.isNotEmpty()) {
                                            messageSearchQuery = ""
                                        } else {
                                            showChatSearchBar = false
                                        }
                                    },
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear or close search")
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = mjColors.neonCyan,
                                unfocusedBorderColor = mjColors.subtleBorder,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("chat_detail_search_input"),
                        )
                    }
                }

                // Pinned Message Banner right under the chat header
                val pinnedPreviewText = pinnedMessage?.let { msg ->
                    when (msg.messageType) {
                        "image" -> if (msg.text.isNotBlank()) "📷 ${msg.text}" else "📷 Photo"
                        "voice" -> "🎤 Voice message"
                        else -> msg.text
                    }
                } ?: chat.pinnedMessagePreview

                AnimatedVisibility(
                    visible = pinnedPreviewText.isNotBlank(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val targetIdx = pinnedMessage?.let { pm ->
                                    messages.indexOfFirst { it.id == pm.id }
                                } ?: -1
                                if (targetIdx >= 0) {
                                    scope.launch {
                                        // +1 accounts for the top ChatOpenedProfileHeader item
                                        listState.animateScrollToItem(targetIdx + 1)
                                    }
                                }
                            }
                            .testTag("chat_pinned_message_banner"),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(mjColors.neonCyan),
                                )
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = null,
                                    tint = mjColors.neonCyan,
                                    modifier = Modifier.size(16.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.label_pinned),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = mjColors.neonCyan,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = pinnedPreviewText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (pinnedMessage != null) {
                                    IconButton(
                                        onClick = { onTogglePinMessage(pinnedMessage) },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Unpin message",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.25f))
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(mjColors.chatWallpaperBgEnd)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                if (isUploadingMedia) {
                    LinearProgressIndicator(
                        color = mjColors.neonEmerald,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Inline Editing Message Banner when editing an existing text message
                AnimatedVisibility(
                    visible = editingMessage != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    editingMessage?.let { editing ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_edit_message_banner"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = null,
                                    tint = mjColors.neonCyan,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Editing message",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = mjColors.neonCyan,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = editing.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editingMessage = null
                                        draftText = ""
                                        onTypingChanged(false)
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("chat_cancel_edit_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Cancel editing",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Built-in Quick Emoji Picker Tray
                AnimatedVisibility(
                    visible = showEmojiTray && !isRecording,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    EmojiPickerTray(
                        onEmojiSelected = { emoji ->
                            draftText += emoji
                            onTypingChanged(true)
                        },
                    )
                }

                // WhatsApp-inspired Floating Pill Composer Bar or Live Voice Recording Bar
                if (isRecording) {
                    VoiceRecordingBar(
                        elapsedMs = recordingElapsedMs,
                        onCancel = {
                            voiceRecorder.stopAndDiscard()
                            isRecording = false
                        },
                        onSend = {
                            val encoded = voiceRecorder.stopAndEncode()
                            isRecording = false
                            if (encoded != null) {
                                onSendVoice(encoded.first, encoded.second)
                            } else {
                                onShowError("Voice note was too short or failed to encode.")
                            }
                        },
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        // Rounded Pill Message Bar (Emoji | Text Input | Attach Photo)
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.45f)),
                            shadowElevation = 2.dp,
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = { showEmojiTray = !showEmojiTray },
                                    modifier = Modifier
                                        .size(46.dp)
                                        .testTag("chat_emoji_toggle_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.EmojiEmotions,
                                        contentDescription = "Toggle emoji picker",
                                        tint = if (showEmojiTray) mjColors.neonAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (draftText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.message_placeholder),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                        )
                                    }
                                    BasicTextField(
                                        value = draftText,
                                        onValueChange = { next ->
                                            draftText = next
                                            onTypingChanged(next.isNotBlank())
                                        },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        cursorBrush = SolidColor(mjColors.neonEmerald),
                                        maxLines = 4,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("chat_message_input"),
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .size(46.dp)
                                        .testTag("chat_attach_photo_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AttachFile,
                                        contentDescription = "Share image",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Detached Circular Send / Save-Edit / Mic Action Button
                        if (draftText.isNotBlank() || editingMessage != null) {
                            Surface(
                                onClick = {
                                    val toSend = draftText.trim()
                                    val currentEdit = editingMessage
                                    if (toSend.isNotEmpty()) {
                                        if (currentEdit != null) {
                                            val isLatest = messages.lastOrNull()?.id == currentEdit.id
                                            onEditMessage(currentEdit.id, toSend, isLatest)
                                            editingMessage = null
                                        } else {
                                            onSendText(toSend)
                                        }
                                        draftText = ""
                                        showEmojiTray = false
                                    }
                                },
                                shape = CircleShape,
                                color = if (editingMessage != null) mjColors.neonCyan else mjColors.neonEmerald,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .size(50.dp)
                                    .testTag("chat_send_button"),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (editingMessage != null) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send,
                                        contentDescription = if (editingMessage != null) "Save edited message" else "Send message",
                                        tint = Color(0xFF002410),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        } else {
                            Surface(
                                onClick = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        val started = voiceRecorder.startRecording()
                                        if (started) {
                                            isRecording = true
                                            showEmojiTray = false
                                        } else {
                                            onShowError("Microphone unavailable on this device.")
                                        }
                                    } else {
                                        pendingPermissionAction = "voice"
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                shape = CircleShape,
                                color = mjColors.neonEmerald,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .size(50.dp)
                                    .testTag("chat_record_voice_button"),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = "Record voice message",
                                        tint = Color(0xFF002410),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Luminous Neon Blue chat wallpaper matching the MJ Chatty app icon
            ChatWallpaperBackdrop(
                bgStart = mjColors.chatWallpaperBg,
                bgEnd = mjColors.chatWallpaperBgEnd,
                patternColor = mjColors.chatWallpaperPattern,
                isDark = mjColors.isDark,
                modifier = Modifier.fillMaxSize(),
            )

            when (messagesState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(
                        color = mjColors.neonEmerald,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                is UiState.Error -> {
                    Text(
                        text = messagesState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                is UiState.Success -> {
                    val normalizedMsgSearch = messageSearchQuery.trim().lowercase()
                    val displayedMessages = remember(messages, normalizedMsgSearch) {
                        if (normalizedMsgSearch.isEmpty()) {
                            messages
                        } else {
                            messages.filter { it.text.lowercase().contains(normalizedMsgSearch) }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        // Top contact profile banner & WhatsApp-style privacy notice
                        item(key = "chat_profile_header") {
                            ChatOpenedProfileHeader(
                                peerTitle = peerTitle,
                                peerAvatar = peerAvatar,
                                peerUsername = peerUsername,
                                peerAbout = peerAbout,
                                isPeerOnline = isPeerOnline,
                                onAvatarClick = {
                                    if (peerAvatar.isNotBlank()) {
                                        previewImagePair = peerAvatar to peerTitle
                                    }
                                },
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }

                        if (displayedMessages.isEmpty()) {
                            item(key = "empty_chat_prompt") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = mjColors.datePillBg.copy(alpha = 0.92f),
                                        border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.35f)),
                                    ) {
                                        Text(
                                            text = if (normalizedMsgSearch.isNotEmpty()) {
                                                "No messages matching \"$messageSearchQuery\"."
                                            } else {
                                                "Say hello to $peerTitle — send a text, photo, emoji, or voice note."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(displayedMessages, key = { _, msg -> msg.id }) { index, msg ->
                                val currentDayLabel = formatDateSeparator(msg.createdAt)
                                val previousDayLabel = if (index > 0) {
                                    formatDateSeparator(displayedMessages[index - 1].createdAt)
                                } else null

                                if (currentDayLabel != previousDayLabel) {
                                    DateSeparatorPill(
                                        label = currentDayLabel,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                }

                                val isOwn = msg.senderId == currentUserId
                                val isPlayingThis = playingMessageId == msg.id
                                val isFirstInGroup = index == 0 ||
                                    displayedMessages[index - 1].senderId != msg.senderId ||
                                    currentDayLabel != previousDayLabel

                                MessageBubbleItem(
                                    message = msg,
                                    isOwn = isOwn,
                                    isFirstInGroup = isFirstInGroup,
                                    isPlayingVoice = isPlayingThis,
                                    voiceProgress = if (isPlayingThis) playbackProgress else 0f,
                                    onImageClick = {
                                        if (msg.imageData.isNotBlank()) {
                                            previewImagePair = msg.imageData to msg.text
                                        }
                                    },
                                    onToggleVoicePlay = {
                                        if (isPlayingThis) {
                                            voicePlayer.stop()
                                            playingMessageId = null
                                            playbackProgress = 0f
                                        } else if (msg.voiceData.isNotBlank()) {
                                            playingMessageId = msg.id
                                            playbackProgress = 0f
                                            voicePlayer.playBase64Audio(
                                                base64Audio = msg.voiceData,
                                                onCompletion = {
                                                    playingMessageId = null
                                                    playbackProgress = 0f
                                                },
                                                onError = {
                                                    playingMessageId = null
                                                    playbackProgress = 0f
                                                    onShowError("Unable to play voice message.")
                                                },
                                            )
                                        }
                                    },
                                    onLongPress = { actionTargetMessage = msg },
                                )
                            }
                        }

                        if (isPeerTyping) {
                            item(key = "typing_bubble") {
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 4.dp,
                                        topEnd = 16.dp,
                                        bottomStart = 16.dp,
                                        bottomEnd = 16.dp,
                                    ),
                                    color = mjColors.incomingBubble,
                                    border = BorderStroke(1.dp, mjColors.incomingBubbleBorder),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    ConfiguredTypingIndicator(
                                        style = typingStatusConfig.displayStyle,
                                        label = "$peerTitle is typing",
                                        accentColor = mjColors.neonEmerald,
                                        textStyle = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewImagePair?.let { (imgData, caption) ->
        FullScreenImageDialog(
            imageData = imgData,
            caption = caption,
            onDismiss = { previewImagePair = null },
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Edit Chat Name", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(60) },
                    label = { Text("Chat Display Name") },
                    placeholder = { Text("Leave blank to use default name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_detail_rename_input"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameChat(renameDraft)
                        showRenameDialog = false
                    },
                    modifier = Modifier.testTag("chat_detail_rename_save"),
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showDeleteChatConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteChatConfirm = false },
            title = { Text("Delete Chat", fontWeight = FontWeight.Bold) },
            text = { Text("Remove \"$peerTitle\" from your chat list? New messages will restore it later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteChatConfirm = false
                        onDeleteChat()
                    },
                    modifier = Modifier.testTag("chat_detail_delete_confirm"),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChatConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showContactInfoDialog) {
        AlertDialog(
            onDismissRequest = { showContactInfoDialog = false },
            title = { Text("Contact Info", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_detail_contact_info_dialog"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MjAvatar(
                        name = peerTitle,
                        avatarData = peerAvatar,
                        size = 80.dp,
                        online = isPeerOnline,
                        showOnlineIndicator = true,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = peerAvatar.isNotBlank()) {
                                previewImagePair = peerAvatar to peerTitle
                            },
                    )
                    Text(
                        text = peerTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    if (peerUsername.isNotBlank()) {
                        Text(
                            text = "@$peerUsername",
                            style = MaterialTheme.typography.bodySmall,
                            color = mjColors.neonCyan,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = if (isPeerOnline) "Online now" else formatLastSeen(peerProfile?.lastSeenAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isPeerOnline) mjColors.neonEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (peerAbout.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = peerAbout,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                    if (!chat.isSelfChat(currentUserId)) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showContactInfoDialog = false
                                    launchAudioCallWithPermission()
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = null, tint = mjColors.neonEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Audio", fontWeight = FontWeight.Bold)
                            }
                            if (videoCallingEnabled) {
                                Button(
                                    onClick = {
                                        showContactInfoDialog = false
                                        launchVideoCallWithPermission()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = mjColors.neonCyan,
                                        contentColor = Color(0xFF001C2B),
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Video", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContactInfoDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    actionTargetMessage?.let { target ->
        val isOwnMessage = target.senderId == currentUserId
        val isTextEditable = isOwnMessage && target.messageType == "text"
        val isPinnedNow = target.pinned || target.id == chat.pinnedMessageId

        AlertDialog(
            onDismissRequest = { actionTargetMessage = null },
            title = { Text("Message Options", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isOwnMessage) {
                        val statusLabel = when (target.status) {
                            "read" -> "Read"
                            "delivered" -> "Delivered"
                            "sending" -> "Sending…"
                            else -> "Sent"
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Status",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    MessageStatusTicks(
                                        status = target.status,
                                        onOutgoingBubble = false,
                                    )
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (target.status == "read") mjColors.readTick else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ReactionOptions.forEach { emoji ->
                            Surface(
                                onClick = {
                                    onReactMessage(target.id, emoji)
                                    actionTargetMessage = null
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 19.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.35f))

                    MessageActionOptionRow(
                        icon = Icons.Filled.PushPin,
                        label = if (isPinnedNow) "Unpin Message" else "Pin Message",
                        tint = mjColors.neonCyan,
                        testTag = "msg_action_pin",
                        onClick = {
                            onTogglePinMessage(target)
                            actionTargetMessage = null
                        },
                    )

                    MessageActionOptionRow(
                        icon = Icons.AutoMirrored.Filled.Forward,
                        label = "Forward Message",
                        tint = mjColors.neonBlue,
                        testTag = "msg_action_forward",
                        onClick = {
                            forwardTargetMessage = target
                            actionTargetMessage = null
                        },
                    )

                    if (isTextEditable) {
                        MessageActionOptionRow(
                            icon = Icons.Filled.Edit,
                            label = "Edit Message",
                            tint = mjColors.neonAmber,
                            testTag = "msg_action_edit",
                            onClick = {
                                editingMessage = target
                                draftText = target.text
                                actionTargetMessage = null
                            },
                        )
                    }

                    if (isOwnMessage) {
                        MessageActionOptionRow(
                            icon = Icons.Filled.Delete,
                            label = "Delete Message",
                            tint = MaterialTheme.colorScheme.error,
                            testTag = "msg_action_delete",
                            onClick = {
                                onDeleteMessage(target.id, isPinnedNow)
                                actionTargetMessage = null
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTargetMessage = null }) {
                    Text("Close")
                }
            },
        )
    }

    forwardTargetMessage?.let { msgToForward ->
        val preview = when (msgToForward.messageType) {
            "image" -> if (msgToForward.text.isNotBlank()) "📷 ${msgToForward.text}" else "📷 Photo"
            "voice" -> "🎤 Voice message"
            else -> msgToForward.text
        }
        ForwardTargetDialog(
            title = "Forward Message",
            previewText = preview,
            currentUserId = currentUserId,
            chats = allChats,
            contacts = allContacts,
            peerProfiles = peerProfiles,
            excludeChatId = chat.id,
            onSelectChat = { targetChat, label ->
                onForwardMessageToChat(targetChat, msgToForward, label)
            },
            onSelectContact = { contact ->
                onForwardMessageToContact(contact, msgToForward)
            },
            onDismiss = { forwardTargetMessage = null },
        )
    }
}

@Composable
private fun MessageActionOptionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ChatWallpaperBackdrop(
    bgStart: Color,
    bgEnd: Color,
    patternColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.drawWithCache {
            val gradientBrush = Brush.verticalGradient(
                colors = listOf(bgStart, bgEnd, bgStart),
            )
            val glowBrush = Brush.radialGradient(
                colors = listOf(
                    patternColor.copy(alpha = if (isDark) 0.22f else 0.14f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.5f, size.height * 0.28f),
                radius = (size.maxDimension * 0.65f).coerceAtLeast(1f),
            )
            val step = 64.dp.toPx()
            val dotRadius = 2.dp.toPx()
            val ringRadius = 6.dp.toPx()
            val dotsPath = Path()
            val ringsPath = Path()
            var rowIndex = 0
            var y = step * 0.5f
            while (y < size.height + step) {
                var x = if (rowIndex % 2 == 0) step * 0.5f else step
                var colIndex = 0
                while (x < size.width + step) {
                    if ((rowIndex + colIndex) % 3 == 0) {
                        ringsPath.addOval(
                            Rect(x - ringRadius, y - ringRadius, x + ringRadius, y + ringRadius)
                        )
                    } else {
                        dotsPath.addOval(
                            Rect(x - dotRadius, y - dotRadius, x + dotRadius, y + dotRadius)
                        )
                    }
                    x += step
                    colIndex++
                }
                y += step * 0.95f
                rowIndex++
            }
            val ringStroke = Stroke(width = 1.2.dp.toPx())
            val ringColor = patternColor.copy(alpha = if (isDark) 0.14f else 0.11f)
            val dotColor = patternColor.copy(alpha = if (isDark) 0.10f else 0.08f)
            onDrawBehind {
                drawRect(brush = gradientBrush)
                drawRect(brush = glowBrush)
                drawPath(path = ringsPath, color = ringColor, style = ringStroke)
                drawPath(path = dotsPath, color = dotColor)
            }
        }
    )
}

@Composable
private fun DateSeparatorPill(
    label: String,
    modifier: Modifier = Modifier,
) {
    val mjColors = LocalMjColors.current
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = mjColors.datePillBg.copy(alpha = 0.94f),
            border = BorderStroke(0.5.dp, mjColors.subtleBorder.copy(alpha = 0.35f)),
            shadowElevation = 1.dp,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun ChatOpenedProfileHeader(
    peerTitle: String,
    peerAvatar: String,
    peerUsername: String,
    peerAbout: String,
    isPeerOnline: Boolean,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mjColors = LocalMjColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("chat_opened_profile_banner"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MjAvatar(
            name = peerTitle,
            avatarData = peerAvatar,
            size = 76.dp,
            online = isPeerOnline,
            showOnlineIndicator = true,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = peerAvatar.isNotBlank(), onClick = onAvatarClick)
                .testTag("chat_opened_profile_avatar"),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = peerTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (isPeerOnline) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(mjColors.neonEmerald.copy(alpha = 0.55f)),
                )
                Text(
                    text = stringResource(R.string.status_online),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = mjColors.neonEmerald.copy(alpha = 0.65f),
                )
            }
        }
        if (peerUsername.isNotBlank()) {
            Text(
                text = "@$peerUsername",
                style = MaterialTheme.typography.labelMedium,
                color = mjColors.neonCyan.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (peerAbout.isNotBlank()) {
            Text(
                text = peerAbout,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 20.dp, end = 20.dp),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // WhatsApp-inspired private chat sync notice pill
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = mjColors.datePillBg.copy(alpha = 0.92f),
            border = BorderStroke(0.5.dp, mjColors.neonAmber.copy(alpha = 0.25f)),
            modifier = Modifier.padding(horizontal = 18.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = mjColors.neonAmber.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "Messages in this chat are privately synced between participants on MJ Chatty.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleItem(
    message: ChatMessage,
    isOwn: Boolean,
    isFirstInGroup: Boolean,
    isPlayingVoice: Boolean,
    voiceProgress: Float,
    onImageClick: () -> Unit,
    onToggleVoicePlay: () -> Unit,
    onLongPress: () -> Unit,
) {
    val mjColors = LocalMjColors.current
    // WhatsApp-style speech bubble geometry with subtle corner notch on first message of a group
    val bubbleShape = if (isOwn) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = if (isFirstInGroup) 3.dp else 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp,
        )
    } else {
        RoundedCornerShape(
            topStart = if (isFirstInGroup) 3.dp else 16.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isFirstInGroup) 4.dp else 0.dp),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 90.dp, max = 308.dp)
                .shadow(
                    elevation = if (mjColors.isDark) 1.dp else 1.5.dp,
                    shape = bubbleShape,
                    ambientColor = Color.Black.copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = 0.16f),
                )
                .clip(bubbleShape)
                .then(
                    if (isOwn) {
                        Modifier.background(
                            Brush.linearGradient(
                                listOf(mjColors.outgoingBubbleStart, mjColors.outgoingBubbleEnd)
                            )
                        )
                    } else {
                        Modifier.background(mjColors.incomingBubble)
                    }
                )
                .combinedClickable(
                    onClick = {
                        if (message.messageType == "image") onImageClick()
                        else if (message.messageType == "voice") onToggleVoicePlay()
                    },
                    onLongClick = onLongPress,
                )
                .padding(
                    horizontal = if (message.messageType == "image") 5.dp else 11.dp,
                    vertical = if (message.messageType == "image") 5.dp else 7.dp,
                ),
        ) {
            val contentColor = if (isOwn) mjColors.outgoingBubbleText else MaterialTheme.colorScheme.onSurface
            val metaColor = if (isOwn) mjColors.outgoingBubbleMeta else MaterialTheme.colorScheme.onSurfaceVariant

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (message.forwarded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = if (message.messageType == "image") 6.dp else 0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Forward,
                            contentDescription = null,
                            tint = metaColor,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = stringResource(R.string.label_forwarded),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontStyle = FontStyle.Italic,
                            ),
                            color = metaColor,
                        )
                    }
                }

                when (message.messageType) {
                    "image" -> {
                        val bmp by produceState(
                            initialValue = ImageMediaUtils.getCachedBitmap(message.imageData),
                            key1 = message.imageData,
                        ) {
                            if (value == null && message.imageData.startsWith("data:image/")) {
                                value = ImageMediaUtils.decodeDataUriToBitmapAsync(message.imageData)
                            }
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp!!.asImageBitmap(),
                                contentDescription = message.text.ifBlank { "Shared photo" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                        }
                        if (message.text.isNotBlank()) {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    "voice" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.widthIn(min = 200.dp),
                        ) {
                            Surface(
                                onClick = onToggleVoicePlay,
                                shape = CircleShape,
                                color = if (isOwn) mjColors.neonEmerald.copy(alpha = 0.24f) else mjColors.neonCyan.copy(alpha = 0.16f),
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlayingVoice) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlayingVoice) "Pause voice note" else "Play voice note",
                                        tint = if (isOwn) mjColors.outgoingBubbleText else mjColors.neonCyan,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                LinearProgressIndicator(
                                    progress = { if (isPlayingVoice) voiceProgress else 0f },
                                    color = mjColors.neonEmerald,
                                    trackColor = metaColor.copy(alpha = 0.28f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.5.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Voice • ${formatDurationSeconds(message.voiceDurationMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = metaColor,
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 21.sp),
                            color = contentColor,
                        )
                    }
                }

                // Bottom-Right Pin / Edited / Timestamp + Delivery/Read Ticks
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = if (message.messageType == "image") 6.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (message.pinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned message",
                            tint = mjColors.neonCyan,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                    if (message.edited) {
                        Text(
                            text = stringResource(R.string.label_edited),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontStyle = FontStyle.Italic,
                            ),
                            color = metaColor,
                        )
                    }
                    Text(
                        text = formatMessageTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = metaColor,
                    )
                    if (isOwn) {
                        MessageStatusTicks(
                            status = message.status,
                            onOutgoingBubble = true,
                        )
                    }
                }
            }
        }

        if (message.reaction.isNotBlank()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, mjColors.subtleBorder),
                shadowElevation = 2.dp,
                modifier = Modifier.padding(top = 2.dp, start = 6.dp, end = 6.dp),
            ) {
                Text(
                    text = message.reaction,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun EmojiPickerTray(
    onEmojiSelected: (String) -> Unit,
) {
    val mjColors = LocalMjColors.current
    var selectedCategory by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            QuickEmojiCategories.forEachIndexed { idx, (iconEmoji, _) ->
                Surface(
                    onClick = { selectedCategory = idx },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedCategory == idx) {
                        mjColors.neonEmerald.copy(alpha = 0.22f)
                    } else {
                        Color.Transparent
                    },
                ) {
                    Text(
                        text = iconEmoji,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(QuickEmojiCategories[selectedCategory].second) { emoji ->
                Surface(
                    onClick = { onEmojiSelected(emoji) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = emoji, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceRecordingBar(
    elapsedMs: Long,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    val mjColors = LocalMjColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .size(48.dp)
                .testTag("voice_record_cancel"),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Discard voice recording",
                tint = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
            Text(
                text = "Recording ${formatDurationSeconds(elapsedMs)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            TypingDotsIndicator(dotColor = mjColors.neonEmerald)
        }

        Surface(
            onClick = onSend,
            shape = CircleShape,
            color = mjColors.neonEmerald,
            modifier = Modifier
                .size(48.dp)
                .testTag("voice_record_send"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Stop and send voice message",
                    tint = Color(0xFF002410),
                )
            }
        }
    }
}
