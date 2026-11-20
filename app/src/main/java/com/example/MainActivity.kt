package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AppNotificationHelper
import com.example.data.CallSettingsConfig
import com.example.data.ChatLockConfig
import com.example.data.ChatRepository
import com.example.data.ChatThread
import com.example.data.NotificationSettingsConfig
import com.example.data.ThemeMode
import com.example.data.ThemePreferences
import com.example.data.TypingDisplayStyle
import com.example.data.TypingStatusConfig
import com.example.data.UiState
import com.example.data.WebRtcAudioManager
import com.example.data.attemptAutoSignIn
import com.example.data.authStateFlow
import com.example.data.performSignOut
import com.example.ui.components.CallSettingsSheet
import com.example.ui.components.LockedChatsVaultSheet
import com.example.ui.components.NotificationSettingsSheet
import com.example.ui.components.SetChatLockPasscodeDialog
import com.example.ui.components.UnlockChatLockDialog
import com.example.ui.screens.AddRegisteredContactSheet
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatListScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.LinkToPcScreen
import com.example.ui.screens.SettingsProfileScreen
import com.example.ui.screens.WebRtcCallOverlay
import com.example.ui.screens.WelcomeScreen
import com.example.ui.screens.resolveChatTitle
import com.example.ui.theme.LocalMjColors
import com.example.ui.theme.MjChattyTheme
import com.example.viewmodel.ChatViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    CHATS("Chats", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat, "nav_tab_chats"),
    CALLS("Calls", Icons.Filled.Call, Icons.Outlined.Call, "nav_tab_calls"),
    CONTACTS("Contacts", Icons.Filled.Contacts, Icons.Outlined.Contacts, "nav_tab_contacts"),
    LINK_PC("Link PC", Icons.Filled.Devices, Icons.Outlined.Devices, "nav_tab_link_pc"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_tab_settings"),
}

private data class GlobalCallPermissionRequest(
    val peerUid: String,
    val peerName: String,
    val peerAvatar: String,
    val chatId: String,
    val isVideo: Boolean,
)

private data class NotificationDeepLink(
    val openTab: String? = null,
    val openChatId: String? = null,
    val token: Long = System.nanoTime(),
)

class MainActivity : ComponentActivity() {
    private var lastAppliedDarkBars: Boolean? = null
    private val pendingNotificationDeepLink = MutableStateFlow<NotificationDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extractNotificationDeepLink(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        lastAppliedDarkBars = true
        setContent {
            val context = LocalContext.current
            val themePrefs = remember(context) { ThemePreferences(context) }
            val themeMode by themePrefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)
            val deepLink by pendingNotificationDeepLink.collectAsStateWithLifecycle()
            val isDark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            LaunchedEffect(isDark) {
                if (lastAppliedDarkBars != isDark) {
                    applySystemBarAppearance(isDark = isDark)
                }
            }

            MjChattyTheme(themeMode = themeMode) {
                MjChattyRootApp(
                    themePrefs = themePrefs,
                    themeMode = themeMode,
                    deepLink = deepLink,
                    onConsumeDeepLink = { pendingNotificationDeepLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractNotificationDeepLink(intent)
    }

    private fun extractNotificationDeepLink(intent: Intent?) {
        if (intent == null) return
        val tab = intent.getStringExtra(AppNotificationHelper.EXTRA_OPEN_TAB)
        val chatId = intent.getStringExtra(AppNotificationHelper.EXTRA_OPEN_CHAT_ID)
        if (!tab.isNullOrBlank() || !chatId.isNullOrBlank()) {
            pendingNotificationDeepLink.value = NotificationDeepLink(openTab = tab, openChatId = chatId)
            intent.removeExtra(AppNotificationHelper.EXTRA_OPEN_TAB)
            intent.removeExtra(AppNotificationHelper.EXTRA_OPEN_CHAT_ID)
        }
    }

    private fun applySystemBarAppearance(isDark: Boolean) {
        lastAppliedDarkBars = isDark
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }
}

@Composable
private fun MjChattyRootApp(
    themePrefs: ThemePreferences,
    themeMode: ThemeMode,
    deepLink: NotificationDeepLink?,
    onConsumeDeepLink: () -> Unit,
) {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by Firebase.auth.authStateFlow().collectAsStateWithLifecycle(
        initialValue = Firebase.auth.currentUser
    )
    var pendingWelcomePhone by remember { mutableStateOf("") }

    // Wait until window focus + first frame + WindowManager SurfaceSyncGroup settle before opening Credential Manager
    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            withFrameNanos { }
            delay(1500L)
            if (Firebase.auth.currentUser == null && windowInfo.isWindowFocused) {
                attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {},
                    onUnauthenticated = {},
                    scope = scope,
                )
            }
        }
    }

    val typingStatusConfig by themePrefs.typingStatusConfigFlow.collectAsStateWithLifecycle(
        initialValue = TypingStatusConfig()
    )
    val chatLockConfig by themePrefs.chatLockConfigFlow.collectAsStateWithLifecycle(
        initialValue = ChatLockConfig()
    )
    val callSettingsConfig by themePrefs.callSettingsConfigFlow.collectAsStateWithLifecycle(
        initialValue = CallSettingsConfig()
    )
    val notificationSettingsConfig by themePrefs.notificationSettingsFlow.collectAsStateWithLifecycle(
        initialValue = NotificationSettingsConfig()
    )

    val user = currentUser
    if (user == null) {
        WelcomeScreen(
            onPreparePhoneForAuth = { normalizedPhone ->
                pendingWelcomePhone = normalizedPhone
                themePrefs.savePendingPhone(normalizedPhone)
            },
            onAuthSuccess = {
                scope.launch {
                    val saved = themePrefs.consumePendingPhone()
                    if (saved.isNotBlank()) pendingWelcomePhone = saved
                }
            },
        )
    } else {
        key(user.uid) {
            AuthenticatedMjChattyShell(
                currentUserId = user.uid,
                pendingWelcomePhone = pendingWelcomePhone,
                themeMode = themeMode,
                typingStatusConfig = typingStatusConfig,
                chatLockConfig = chatLockConfig,
                callSettingsConfig = callSettingsConfig,
                notificationSettingsConfig = notificationSettingsConfig,
                deepLink = deepLink,
                onConsumeDeepLink = onConsumeDeepLink,
                onThemeModeChange = { nextMode ->
                    scope.launch { themePrefs.setThemeMode(nextMode) }
                },
                onSendTypingIndicatorChange = { enabled ->
                    scope.launch { themePrefs.setSendTypingIndicator(enabled) }
                },
                onShowPeerTypingIndicatorChange = { enabled ->
                    scope.launch { themePrefs.setShowPeerTypingIndicator(enabled) }
                },
                onTypingTimeoutChange = { seconds ->
                    scope.launch { themePrefs.setTypingTimeoutSeconds(seconds) }
                },
                onTypingDisplayStyleChange = { style ->
                    scope.launch { themePrefs.setTypingDisplayStyle(style) }
                },
                onVideoCallingEnabledChange = { enabled ->
                    scope.launch { themePrefs.setVideoCallingEnabled(enabled) }
                },
                onStartVideoWithCameraOnChange = { enabled ->
                    scope.launch { themePrefs.setStartVideoWithCameraOn(enabled) }
                },
                onPreferFrontCameraChange = { front ->
                    scope.launch { themePrefs.setPreferFrontCamera(front) }
                },
                onDefaultSpeakerForVideoChange = { enabled ->
                    scope.launch { themePrefs.setDefaultSpeakerForVideo(enabled) }
                },
                onHdVideoQualityChange = { hd ->
                    scope.launch { themePrefs.setHdVideoQuality(hd) }
                },
                onPushNotificationsEnabledChange = { enabled ->
                    scope.launch { themePrefs.setPushNotificationsEnabled(enabled) }
                },
                onIncomingCallNotificationsChange = { enabled ->
                    scope.launch { themePrefs.setIncomingCallNotifications(enabled) }
                },
                onOutgoingCallNotificationsChange = { enabled ->
                    scope.launch { themePrefs.setOutgoingCallNotifications(enabled) }
                },
                onReceivedActivityNotificationsChange = { enabled ->
                    scope.launch { themePrefs.setReceivedActivityNotifications(enabled) }
                },
                onToggleChatMuted = { chatId, muted ->
                    scope.launch { themePrefs.setChatMuted(chatId, muted) }
                },
                onSaveChatLockCode = { code ->
                    scope.launch { themePrefs.setChatLockPasscode(code) }
                },
                onClearChatLockCode = {
                    scope.launch { themePrefs.clearChatLockPasscode() }
                },
                onHideLockedChatsChange = { hide ->
                    scope.launch { themePrefs.setHideLockedChats(hide) }
                },
                onPerformSignOut = { beforeSignOut ->
                    performSignOut(
                        credentialManager = credentialManager,
                        scope = scope,
                        beforeSignOut = beforeSignOut,
                    )
                },
            )
        }
    }
}

@Composable
private fun AuthenticatedMjChattyShell(
    currentUserId: String,
    pendingWelcomePhone: String,
    themeMode: ThemeMode,
    typingStatusConfig: TypingStatusConfig,
    chatLockConfig: ChatLockConfig,
    callSettingsConfig: CallSettingsConfig,
    notificationSettingsConfig: NotificationSettingsConfig,
    deepLink: NotificationDeepLink?,
    onConsumeDeepLink: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSendTypingIndicatorChange: (Boolean) -> Unit,
    onShowPeerTypingIndicatorChange: (Boolean) -> Unit,
    onTypingTimeoutChange: (Int) -> Unit,
    onTypingDisplayStyleChange: (TypingDisplayStyle) -> Unit,
    onVideoCallingEnabledChange: (Boolean) -> Unit,
    onStartVideoWithCameraOnChange: (Boolean) -> Unit,
    onPreferFrontCameraChange: (Boolean) -> Unit,
    onDefaultSpeakerForVideoChange: (Boolean) -> Unit,
    onHdVideoQualityChange: (Boolean) -> Unit,
    onPushNotificationsEnabledChange: (Boolean) -> Unit,
    onIncomingCallNotificationsChange: (Boolean) -> Unit,
    onOutgoingCallNotificationsChange: (Boolean) -> Unit,
    onReceivedActivityNotificationsChange: (Boolean) -> Unit,
    onToggleChatMuted: (chatId: String, muted: Boolean) -> Unit,
    onSaveChatLockCode: (String) -> Unit,
    onClearChatLockCode: () -> Unit,
    onHideLockedChatsChange: (Boolean) -> Unit,
    onPerformSignOut: (suspend () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val notificationHelper = remember(context) { AppNotificationHelper(context.applicationContext) }
    var hasNotificationPermission by remember {
        mutableStateOf(notificationHelper.hasNotificationPermission())
    }
    var promptedNotifPermissionOnce by rememberSaveable { mutableStateOf(false) }

    val chatViewModel: ChatViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ChatViewModel(
                    repository = ChatRepository(db),
                    webRtcAudioManager = WebRtcAudioManager(app.applicationContext),
                    notificationHelper = notificationHelper,
                    currentUserId = currentUserId,
                    pendingWelcomePhone = pendingWelcomePhone,
                )
            }
        },
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotificationPermission = granted || notificationHelper.hasNotificationPermission()
        if (!hasNotificationPermission) {
            chatViewModel.showMessage(context.getString(R.string.permission_notifications_denied))
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                hasNotificationPermission = notificationHelper.hasNotificationPermission()
            }
        } else {
            hasNotificationPermission = notificationHelper.hasNotificationPermission()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission &&
            !promptedNotifPermissionOnce &&
            notificationSettingsConfig.pushNotificationsEnabled
        ) {
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            withFrameNanos { }
            delay(1800L)
            if (
                !promptedNotifPermissionOnce &&
                !hasNotificationPermission &&
                windowInfo.isWindowFocused &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                promptedNotifPermissionOnce = true
                requestNotificationPermissionIfNeeded()
            }
        }
    }
    DisposableEffect(lifecycleOwner, currentUserId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    chatViewModel.setOnlinePresence(true)
                    hasNotificationPermission = notificationHelper.hasNotificationPermission()
                }
                Lifecycle.Event.ON_RESUME -> {
                    hasNotificationPermission = notificationHelper.hasNotificationPermission()
                }
                Lifecycle.Event.ON_STOP -> chatViewModel.setOnlinePresence(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentUserState by chatViewModel.currentUserState.collectAsStateWithLifecycle()
    val chatsState by chatViewModel.chatsState.collectAsStateWithLifecycle()
    val contactsState by chatViewModel.contactsState.collectAsStateWithLifecycle()
    val callsState by chatViewModel.callsState.collectAsStateWithLifecycle()
    val pcLinksState by chatViewModel.pcLinksState.collectAsStateWithLifecycle()
    val peerProfiles by chatViewModel.peerProfiles.collectAsStateWithLifecycle()
    val selectedChatId by chatViewModel.selectedChatId.collectAsStateWithLifecycle()
    val pendingSelectedChat by chatViewModel.pendingSelectedChat.collectAsStateWithLifecycle()
    val activeMessagesState by chatViewModel.activeMessagesState.collectAsStateWithLifecycle()
    val chatSearchQuery by chatViewModel.chatSearchQuery.collectAsStateWithLifecycle()
    val contactSearchQuery by chatViewModel.contactSearchQuery.collectAsStateWithLifecycle()
    val lookupInput by chatViewModel.contactLookupInput.collectAsStateWithLifecycle()
    val verificationState by chatViewModel.contactVerificationState.collectAsStateWithLifecycle()
    val statusMessage by chatViewModel.statusMessage.collectAsStateWithLifecycle()

    // Direct WebRTC Audio & Video Call live state
    val activeCall by chatViewModel.activeCall.collectAsStateWithLifecycle()
    val incomingRingingCall by chatViewModel.incomingRingingCall.collectAsStateWithLifecycle()
    val callStage by chatViewModel.callStage.collectAsStateWithLifecycle()
    val callElapsedSeconds by chatViewModel.callElapsedSeconds.collectAsStateWithLifecycle()
    val isCallMuted by chatViewModel.isCallMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by chatViewModel.isSpeakerOn.collectAsStateWithLifecycle()
    val isCameraEnabled by chatViewModel.isCameraEnabled.collectAsStateWithLifecycle()
    val isFrontCamera by chatViewModel.isFrontCamera.collectAsStateWithLifecycle()
    val localVideoTrack by chatViewModel.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoTrack by chatViewModel.remoteVideoTrack.collectAsStateWithLifecycle()

    // Chat Lock UI modal state
    var pendingLockChat by remember { mutableStateOf<ChatThread?>(null) }
    var showSetPasscodeDialog by remember { mutableStateOf(false) }
    var showUnlockVaultDialog by remember { mutableStateOf(false) }
    var showLockedChatsVaultSheet by remember { mutableStateOf(false) }

    var pendingCallRequest by remember { mutableStateOf<GlobalCallPermissionRequest?>(null) }
    val globalCallPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val req = pendingCallRequest
        pendingCallRequest = null
        if (req == null) return@rememberLauncherForActivityResult

        val audioOk = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraOk = !req.isVideo ||
            results[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (audioOk && cameraOk) {
            if (req.isVideo) {
                chatViewModel.startVideoCall(req.peerUid, req.peerName, req.peerAvatar, req.chatId)
            } else {
                chatViewModel.startAudioCall(req.peerUid, req.peerName, req.peerAvatar, req.chatId)
            }
        } else {
            chatViewModel.showMessage(
                context.getString(
                    if (req.isVideo) R.string.permission_video_call_denied else R.string.permission_audio_denied
                )
            )
        }
    }

    fun startCallWithPermission(
        peerUid: String,
        peerName: String,
        peerAvatar: String,
        chatId: String,
        isVideo: Boolean,
    ) {
        val audioOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        val cameraOk = !isVideo || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        if (audioOk && cameraOk) {
            if (isVideo) {
                chatViewModel.startVideoCall(peerUid, peerName, peerAvatar, chatId)
            } else {
                chatViewModel.startAudioCall(peerUid, peerName, peerAvatar, chatId)
            }
        } else {
            pendingCallRequest = GlobalCallPermissionRequest(peerUid, peerName, peerAvatar, chatId, isVideo)
            val perms = if (isVideo) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            globalCallPermissionsLauncher.launch(perms)
        }
    }

    fun handleToggleLockChat(chat: ChatThread) {
        val isCurrentlyLocked = chat.isLockedBy(currentUserId)
        if (!isCurrentlyLocked && !chatLockConfig.hasPasscode) {
            pendingLockChat = chat
            showSetPasscodeDialog = true
        } else {
            chatViewModel.toggleLockChat(chat)
        }
    }

    fun handleOpenLockedVault(bypassPrompt: Boolean) {
        when {
            !chatLockConfig.hasPasscode -> {
                pendingLockChat = null
                showSetPasscodeDialog = true
            }
            bypassPrompt -> {
                showLockedChatsVaultSheet = true
            }
            else -> {
                showUnlockVaultDialog = true
            }
        }
    }

    LaunchedEffect(typingStatusConfig) {
        chatViewModel.updateTypingStatusConfig(typingStatusConfig)
    }

    LaunchedEffect(callSettingsConfig) {
        chatViewModel.updateCallSettingsConfig(callSettingsConfig)
    }

    LaunchedEffect(notificationSettingsConfig) {
        chatViewModel.updateNotificationSettingsConfig(notificationSettingsConfig)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            chatViewModel.clearStatusMessage()
        }
    }

    var currentTab by rememberSaveable { mutableStateOf(MainTab.CHATS) }
    var showAddContactSheet by rememberSaveable { mutableStateOf(false) }
    var showCallSettingsSheet by remember { mutableStateOf(false) }
    var showNotificationSettingsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(deepLink) {
        val target = deepLink ?: return@LaunchedEffect
        when (target.openTab) {
            "CALLS" -> currentTab = MainTab.CALLS
            "CONTACTS" -> currentTab = MainTab.CONTACTS
            "LINK_PC" -> currentTab = MainTab.LINK_PC
            "SETTINGS" -> currentTab = MainTab.SETTINGS
            "CHATS" -> currentTab = MainTab.CHATS
        }
        if (!target.openChatId.isNullOrBlank()) {
            currentTab = MainTab.CHATS
            chatViewModel.openChat(target.openChatId)
        }
        onConsumeDeepLink()
    }

    val currentUserAccount = (currentUserState as? UiState.Success)?.data
    val currentUserAvatar = currentUserAccount?.avatarData.orEmpty()
    val currentUserOnline = currentUserAccount?.online ?: true

    val contacts = (contactsState as? UiState.Success)?.data.orEmpty()
    val chats = (chatsState as? UiState.Success)?.data.orEmpty()
    val lockedChats = remember(chats, currentUserId) {
        chats.filter { it.isLockedBy(currentUserId) && !it.isHiddenBy(currentUserId) && !it.isSelfChat(currentUserId) }
    }
    val contactsByUid = remember(contacts) { contacts.associateBy { it.contactUid } }
    val selectedChat = remember(chats, selectedChatId, pendingSelectedChat) {
        chats.firstOrNull { it.id == selectedChatId }
            ?: pendingSelectedChat?.takeIf { it.id == selectedChatId }
    }

    // Back gesture returns from Calls/Contacts/Settings to Chats when not inside an open chat
    if (selectedChat == null && currentTab != MainTab.CHATS) {
        BackHandler { currentTab = MainTab.CHATS }
    }

    val mjColors = LocalMjColors.current

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedLayout = maxWidth >= 720.dp

            if (!isExpandedLayout && selectedChat != null) {
                // Compact phone full-screen Chat Detail
                val isSelfChat = selectedChat.isSelfChat(currentUserId)
                val peerUid = selectedChat.peerUid(currentUserId)
                val peerTitle = resolveChatTitle(selectedChat, currentUserId, contactsByUid, peerProfiles)
                val peerProfile = peerProfiles[peerUid]
                val peerAvatar = if (isSelfChat) {
                    currentUserAvatar
                } else {
                    peerProfile?.avatarData?.ifBlank { contactsByUid[peerUid]?.avatarData.orEmpty() }
                        ?: contactsByUid[peerUid]?.avatarData.orEmpty()
                }
                val peerUsername = if (isSelfChat) {
                    currentUserAccount?.username.orEmpty()
                } else {
                    peerProfile?.username?.ifBlank { contactsByUid[peerUid]?.contactUsername.orEmpty() }
                        ?: contactsByUid[peerUid]?.contactUsername.orEmpty()
                }
                val peerAbout = if (isSelfChat) {
                    currentUserAccount?.about.orEmpty()
                } else {
                    peerProfile?.about.orEmpty()
                }
                val isPeerOnline = if (isSelfChat) currentUserOnline else peerProfile?.online == true

                Box(modifier = Modifier.fillMaxSize()) {
                    ChatDetailScreen(
                        chat = selectedChat,
                        currentUserId = currentUserId,
                        peerTitle = peerTitle,
                        peerAvatar = peerAvatar,
                        peerUsername = peerUsername,
                        peerAbout = peerAbout,
                        isPeerOnline = isPeerOnline,
                        peerProfile = peerProfile,
                        allChats = chats,
                        allContacts = contacts,
                        peerProfiles = peerProfiles,
                        typingStatusConfig = typingStatusConfig,
                        videoCallingEnabled = callSettingsConfig.videoCallingEnabled,
                        isChatMuted = notificationSettingsConfig.isChatMuted(selectedChat.id),
                        messagesState = activeMessagesState,
                        showBackButton = true,
                        onBack = { chatViewModel.openChat(null) },
                        onSendText = { text -> chatViewModel.sendTextMessage(selectedChat, text) },
                        onSendImage = { img, caption -> chatViewModel.sendImageMessage(selectedChat, img, caption) },
                        onSendVoice = { voice, dur -> chatViewModel.sendVoiceMessage(selectedChat, voice, dur) },
                        onTypingChanged = { typing -> chatViewModel.onComposerTextChanged(selectedChat.id, typing) },
                        onMarkRead = { msgs -> chatViewModel.markActiveChatRead(selectedChat, msgs) },
                        onReactMessage = { msgId, emoji -> chatViewModel.reactToMessage(selectedChat.id, msgId, emoji) },
                        onEditMessage = { msgId, newText, isLatest ->
                            chatViewModel.editMessage(selectedChat.id, msgId, newText, isLatest)
                        },
                        onTogglePinMessage = { msg -> chatViewModel.togglePinMessage(selectedChat.id, msg) },
                        onForwardMessageToChat = { targetChat, msg, label ->
                            chatViewModel.forwardMessageToChat(targetChat, msg, label)
                        },
                        onForwardMessageToContact = { contact, msg ->
                            chatViewModel.forwardMessageToContact(contact, msg)
                        },
                        onDeleteMessage = { msgId, isPinned ->
                            chatViewModel.deleteMessage(selectedChat.id, msgId, isPinned)
                        },
                        onStartAudioCall = {
                            chatViewModel.startAudioCall(peerUid, peerTitle, peerAvatar, selectedChat.id)
                        },
                        onStartVideoCall = {
                            chatViewModel.startVideoCall(peerUid, peerTitle, peerAvatar, selectedChat.id)
                        },
                        onToggleLockChat = { handleToggleLockChat(selectedChat) },
                        onTogglePinChat = { chatViewModel.togglePinChat(selectedChat) },
                        onToggleMuteChat = {
                            val nextMuted = !notificationSettingsConfig.isChatMuted(selectedChat.id)
                            onToggleChatMuted(selectedChat.id, nextMuted)
                            chatViewModel.showMessage(
                                if (nextMuted) "Notifications muted for this chat" else "Notifications unmuted"
                            )
                        },
                        onRenameChat = { newTitle -> chatViewModel.renameChatTitle(selectedChat.id, newTitle) },
                        onDeleteChat = { chatViewModel.deleteChat(selectedChat) },
                        onShowError = { err -> chatViewModel.showMessage(err) },
                    )
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            } else if (isExpandedLayout) {
                // Medium / Expanded Tablet & Foldable Layout: NavigationRail + List-Detail Split Pane
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            MainTab.entries.forEach { tab ->
                                val isSelected = currentTab == tab
                                NavigationRailItem(
                                    selected = isSelected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label,
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = mjColors.neonCyan,
                                        selectedTextColor = mjColors.neonCyan,
                                        indicatorColor = mjColors.neonCyan.copy(alpha = 0.16f),
                                    ),
                                    modifier = Modifier.testTag(tab.testTag),
                                )
                            }
                        }
                        VerticalDivider(color = mjColors.subtleBorder.copy(alpha = 0.45f))

                        when (currentTab) {
                            MainTab.CHATS -> {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    ChatListScreen(
                                        currentUserId = currentUserId,
                                        chatsState = chatsState,
                                        contacts = contacts,
                                        peerProfiles = peerProfiles,
                                        typingStatusConfig = typingStatusConfig,
                                        chatLockConfig = chatLockConfig,
                                        videoCallingEnabled = callSettingsConfig.videoCallingEnabled,
                                        mutedChatIds = notificationSettingsConfig.mutedChatIds,
                                        searchQuery = chatSearchQuery,
                                        selectedChatId = selectedChatId,
                                        onSearchQueryChange = chatViewModel::updateChatSearch,
                                        onChatSelected = { chat -> chatViewModel.openChatThread(chat) },
                                        onStartChatWithContact = { contact ->
                                            chatViewModel.openOrStartChatWithPeer(contact.contactUid, contact.savedName)
                                        },
                                        onAddContactClick = { showAddContactSheet = true },
                                        onTogglePinChat = chatViewModel::togglePinChat,
                                        onToggleLockChat = ::handleToggleLockChat,
                                        onToggleMuteChat = { chat ->
                                            val nextMuted = !notificationSettingsConfig.isChatMuted(chat.id)
                                            onToggleChatMuted(chat.id, nextMuted)
                                            chatViewModel.showMessage(
                                                if (nextMuted) "Notifications muted for this chat" else "Notifications unmuted"
                                            )
                                        },
                                        onRequestOpenLockedVault = ::handleOpenLockedVault,
                                        onOpenNotificationSettings = { showNotificationSettingsSheet = true },
                                        onOpenCallSettings = { showCallSettingsSheet = true },
                                        onRenameChat = { chat, newTitle ->
                                            chatViewModel.renameChatTitle(chat.id, newTitle)
                                        },
                                        onForwardChatToChat = chatViewModel::forwardChatSummaryToChat,
                                        onForwardChatToContact = chatViewModel::forwardChatSummaryToContact,
                                        onDeleteChat = chatViewModel::deleteChat,
                                        onCallPeer = { uid, name, avatar, chatId ->
                                            startCallWithPermission(uid, name, avatar, chatId, isVideo = false)
                                        },
                                        onVideoCallPeer = { uid, name, avatar, chatId ->
                                            startCallWithPermission(uid, name, avatar, chatId, isVideo = true)
                                        },
                                        modifier = Modifier
                                            .width(360.dp)
                                            .fillMaxHeight(),
                                    )
                                    VerticalDivider(color = mjColors.subtleBorder.copy(alpha = 0.45f))
                                    if (selectedChat != null) {
                                        val isSelfChat = selectedChat.isSelfChat(currentUserId)
                                        val peerUid = selectedChat.peerUid(currentUserId)
                                        val peerTitle = resolveChatTitle(selectedChat, currentUserId, contactsByUid, peerProfiles)
                                        val peerProfile = peerProfiles[peerUid]
                                        val peerAvatar = if (isSelfChat) {
                                            currentUserAvatar
                                        } else {
                                            peerProfile?.avatarData?.ifBlank { contactsByUid[peerUid]?.avatarData.orEmpty() }
                                                ?: contactsByUid[peerUid]?.avatarData.orEmpty()
                                        }
                                        val peerUsername = if (isSelfChat) {
                                            currentUserAccount?.username.orEmpty()
                                        } else {
                                            peerProfile?.username?.ifBlank { contactsByUid[peerUid]?.contactUsername.orEmpty() }
                                                ?: contactsByUid[peerUid]?.contactUsername.orEmpty()
                                        }
                                        val peerAbout = if (isSelfChat) {
                                            currentUserAccount?.about.orEmpty()
                                        } else {
                                            peerProfile?.about.orEmpty()
                                        }
                                        val isPeerOnline = if (isSelfChat) currentUserOnline else peerProfile?.online == true

                                        ChatDetailScreen(
                                            chat = selectedChat,
                                            currentUserId = currentUserId,
                                            peerTitle = peerTitle,
                                            peerAvatar = peerAvatar,
                                            peerUsername = peerUsername,
                                            peerAbout = peerAbout,
                                            isPeerOnline = isPeerOnline,
                                            peerProfile = peerProfile,
                                            allChats = chats,
                                            allContacts = contacts,
                                            peerProfiles = peerProfiles,
                                            typingStatusConfig = typingStatusConfig,
                                            videoCallingEnabled = callSettingsConfig.videoCallingEnabled,
                                            isChatMuted = notificationSettingsConfig.isChatMuted(selectedChat.id),
                                            messagesState = activeMessagesState,
                                            showBackButton = false,
                                            onBack = { chatViewModel.openChat(null) },
                                            onSendText = { text -> chatViewModel.sendTextMessage(selectedChat, text) },
                                            onSendImage = { img, caption -> chatViewModel.sendImageMessage(selectedChat, img, caption) },
                                            onSendVoice = { voice, dur -> chatViewModel.sendVoiceMessage(selectedChat, voice, dur) },
                                            onTypingChanged = { typing -> chatViewModel.onComposerTextChanged(selectedChat.id, typing) },
                                            onMarkRead = { msgs -> chatViewModel.markActiveChatRead(selectedChat, msgs) },
                                            onReactMessage = { msgId, emoji -> chatViewModel.reactToMessage(selectedChat.id, msgId, emoji) },
                                            onEditMessage = { msgId, newText, isLatest ->
                                                chatViewModel.editMessage(selectedChat.id, msgId, newText, isLatest)
                                            },
                                            onTogglePinMessage = { msg -> chatViewModel.togglePinMessage(selectedChat.id, msg) },
                                            onForwardMessageToChat = { targetChat, msg, label ->
                                                chatViewModel.forwardMessageToChat(targetChat, msg, label)
                                            },
                                            onForwardMessageToContact = { contact, msg ->
                                                chatViewModel.forwardMessageToContact(contact, msg)
                                            },
                                            onDeleteMessage = { msgId, isPinned ->
                                                chatViewModel.deleteMessage(selectedChat.id, msgId, isPinned)
                                            },
                                            onStartAudioCall = {
                                                chatViewModel.startAudioCall(peerUid, peerTitle, peerAvatar, selectedChat.id)
                                            },
                                            onStartVideoCall = {
                                                chatViewModel.startVideoCall(peerUid, peerTitle, peerAvatar, selectedChat.id)
                                            },
                                            onToggleLockChat = { handleToggleLockChat(selectedChat) },
                                            onTogglePinChat = { chatViewModel.togglePinChat(selectedChat) },
                                            onToggleMuteChat = {
                                                val nextMuted = !notificationSettingsConfig.isChatMuted(selectedChat.id)
                                                onToggleChatMuted(selectedChat.id, nextMuted)
                                                chatViewModel.showMessage(
                                                    if (nextMuted) "Notifications muted for this chat" else "Notifications unmuted"
                                                )
                                            },
                                            onRenameChat = { newTitle -> chatViewModel.renameChatTitle(selectedChat.id, newTitle) },
                                            onDeleteChat = { chatViewModel.deleteChat(selectedChat) },
                                            onShowError = { err -> chatViewModel.showMessage(err) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "Select a conversation to start chatting",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            MainTab.CALLS -> {
                                CallsScreen(
                                    currentUserId = currentUserId,
                                    callsState = callsState,
                                    contacts = contacts,
                                    peerProfiles = peerProfiles,
                                    callSettingsConfig = callSettingsConfig,
                                    onStartAudioCall = chatViewModel::startAudioCall,
                                    onStartVideoCall = chatViewModel::startVideoCall,
                                    onVideoCallingEnabledChange = onVideoCallingEnabledChange,
                                    onStartVideoWithCameraOnChange = onStartVideoWithCameraOnChange,
                                    onPreferFrontCameraChange = onPreferFrontCameraChange,
                                    onDefaultSpeakerForVideoChange = onDefaultSpeakerForVideoChange,
                                    onHdVideoQualityChange = onHdVideoQualityChange,
                                    onDeleteCallLog = chatViewModel::deleteCallLog,
                                    onAddContactClick = { showAddContactSheet = true },
                                    onShowError = chatViewModel::showMessage,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            MainTab.CONTACTS -> {
                                ContactsScreen(
                                    contactsState = contactsState,
                                    peerProfiles = peerProfiles,
                                    searchQuery = contactSearchQuery,
                                    onSearchQueryChange = chatViewModel::updateContactSearch,
                                    onOpenAddContactSheet = { showAddContactSheet = true },
                                    onMessageContact = { contact ->
                                        currentTab = MainTab.CHATS
                                        chatViewModel.openOrStartChatWithPeer(contact.contactUid, contact.savedName)
                                    },
                                    onCallContact = { contact ->
                                        val avatar = peerProfiles[contact.contactUid]?.avatarData?.ifBlank { contact.avatarData }
                                            ?: contact.avatarData
                                        startCallWithPermission(contact.contactUid, contact.savedName, avatar, "", isVideo = false)
                                    },
                                    onDeleteContact = { contact -> chatViewModel.deleteContact(contact.contactUid) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            MainTab.LINK_PC -> {
                                LinkToPcScreen(
                                    pcLinksState = pcLinksState,
                                    userDisplayName = currentUserAccount?.displayName.orEmpty().ifBlank { "MJ Chatty User" },
                                    userHandle = currentUserAccount?.username.orEmpty(),
                                    onEnsureDefaultLink = chatViewModel::ensureDefaultPcLink,
                                    onCreateLink = chatViewModel::createPcLink,
                                    onUpdateLinkStatus = chatViewModel::updatePcLinkStatus,
                                    onRenameLink = chatViewModel::renamePcLink,
                                    onDeleteLink = chatViewModel::deletePcLink,
                                    onNotify = chatViewModel::showMessage,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            MainTab.SETTINGS -> {
                                SettingsProfileScreen(
                                    userState = currentUserState,
                                    themeMode = themeMode,
                                    typingStatusConfig = typingStatusConfig,
                                    callSettingsConfig = callSettingsConfig,
                                    notificationSettingsConfig = notificationSettingsConfig,
                                    hasNotificationPermission = hasNotificationPermission,
                                    chatLockConfig = chatLockConfig,
                                    lockedChatsCount = lockedChats.size,
                                    onThemeModeChange = onThemeModeChange,
                                    onSendTypingIndicatorChange = onSendTypingIndicatorChange,
                                    onShowPeerTypingIndicatorChange = onShowPeerTypingIndicatorChange,
                                    onTypingTimeoutChange = onTypingTimeoutChange,
                                    onTypingDisplayStyleChange = onTypingDisplayStyleChange,
                                    onVideoCallingEnabledChange = onVideoCallingEnabledChange,
                                    onStartVideoWithCameraOnChange = onStartVideoWithCameraOnChange,
                                    onPreferFrontCameraChange = onPreferFrontCameraChange,
                                    onDefaultSpeakerForVideoChange = onDefaultSpeakerForVideoChange,
                                    onHdVideoQualityChange = onHdVideoQualityChange,
                                    onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded,
                                    onPushNotificationsEnabledChange = onPushNotificationsEnabledChange,
                                    onIncomingCallNotificationsChange = onIncomingCallNotificationsChange,
                                    onOutgoingCallNotificationsChange = onOutgoingCallNotificationsChange,
                                    onReceivedActivityNotificationsChange = onReceivedActivityNotificationsChange,
                                    onSetOrChangeChatLockCode = {
                                        pendingLockChat = null
                                        showSetPasscodeDialog = true
                                    },
                                    onOpenLockedChatsVault = { handleOpenLockedVault(bypassPrompt = false) },
                                    onHideLockedChatsChange = onHideLockedChatsChange,
                                    onUnlockAllAndClearCode = {
                                        chatViewModel.unlockAllLockedChats()
                                        onClearChatLockCode()
                                    },
                                    onSaveProfile = { name, handle, phone, bio, avatar ->
                                        chatViewModel.saveProfile(name, handle, phone, bio, avatar)
                                    },
                                    onCopyHandleNotice = chatViewModel::showMessage,
                                    onSignOut = {
                                        onPerformSignOut {
                                            chatViewModel.setOnlinePresence(false)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            } else {
                // Compact Phone Layout with M3 NavigationBar (Chats, Calls, Contacts, Settings)
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        Box {
                            HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.4f))
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                windowInsets = WindowInsets.navigationBars,
                            ) {
                                MainTab.entries.forEach { tab ->
                                    val isSelected = currentTab == tab
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label,
                                            )
                                        },
                                        label = { Text(tab.label, maxLines = 1) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = mjColors.neonCyan,
                                            selectedTextColor = mjColors.neonCyan,
                                            indicatorColor = mjColors.neonCyan.copy(alpha = 0.16f),
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        modifier = Modifier.testTag(tab.testTag),
                                    )
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding()),
                    ) {
                        when (currentTab) {
                            MainTab.CHATS -> {
                                ChatListScreen(
                                    currentUserId = currentUserId,
                                    chatsState = chatsState,
                                    contacts = contacts,
                                    peerProfiles = peerProfiles,
                                    typingStatusConfig = typingStatusConfig,
                                    chatLockConfig = chatLockConfig,
                                    videoCallingEnabled = callSettingsConfig.videoCallingEnabled,
                                    mutedChatIds = notificationSettingsConfig.mutedChatIds,
                                    searchQuery = chatSearchQuery,
                                    selectedChatId = selectedChatId,
                                    onSearchQueryChange = chatViewModel::updateChatSearch,
                                    onChatSelected = { chat -> chatViewModel.openChatThread(chat) },
                                    onStartChatWithContact = { contact ->
                                        chatViewModel.openOrStartChatWithPeer(contact.contactUid, contact.savedName)
                                    },
                                    onAddContactClick = { showAddContactSheet = true },
                                    onTogglePinChat = chatViewModel::togglePinChat,
                                    onToggleLockChat = ::handleToggleLockChat,
                                    onToggleMuteChat = { chat ->
                                        val nextMuted = !notificationSettingsConfig.isChatMuted(chat.id)
                                        onToggleChatMuted(chat.id, nextMuted)
                                        chatViewModel.showMessage(
                                            if (nextMuted) "Notifications muted for this chat" else "Notifications unmuted"
                                        )
                                    },
                                    onRequestOpenLockedVault = ::handleOpenLockedVault,
                                    onOpenNotificationSettings = { showNotificationSettingsSheet = true },
                                    onOpenCallSettings = { showCallSettingsSheet = true },
                                    onRenameChat = { chat, newTitle ->
                                        chatViewModel.renameChatTitle(chat.id, newTitle)
                                    },
                                    onForwardChatToChat = chatViewModel::forwardChatSummaryToChat,
                                    onForwardChatToContact = chatViewModel::forwardChatSummaryToContact,
                                    onDeleteChat = chatViewModel::deleteChat,
                                    onCallPeer = { uid, name, avatar, chatId ->
                                        startCallWithPermission(uid, name, avatar, chatId, isVideo = false)
                                    },
                                    onVideoCallPeer = { uid, name, avatar, chatId ->
                                        startCallWithPermission(uid, name, avatar, chatId, isVideo = true)
                                    },
                                )
                            }
                            MainTab.CALLS -> {
                                CallsScreen(
                                    currentUserId = currentUserId,
                                    callsState = callsState,
                                    contacts = contacts,
                                    peerProfiles = peerProfiles,
                                    callSettingsConfig = callSettingsConfig,
                                    onStartAudioCall = chatViewModel::startAudioCall,
                                    onStartVideoCall = chatViewModel::startVideoCall,
                                    onVideoCallingEnabledChange = onVideoCallingEnabledChange,
                                    onStartVideoWithCameraOnChange = onStartVideoWithCameraOnChange,
                                    onPreferFrontCameraChange = onPreferFrontCameraChange,
                                    onDefaultSpeakerForVideoChange = onDefaultSpeakerForVideoChange,
                                    onHdVideoQualityChange = onHdVideoQualityChange,
                                    onDeleteCallLog = chatViewModel::deleteCallLog,
                                    onAddContactClick = { showAddContactSheet = true },
                                    onShowError = chatViewModel::showMessage,
                                )
                            }
                            MainTab.CONTACTS -> {
                                ContactsScreen(
                                    contactsState = contactsState,
                                    peerProfiles = peerProfiles,
                                    searchQuery = contactSearchQuery,
                                    onSearchQueryChange = chatViewModel::updateContactSearch,
                                    onOpenAddContactSheet = { showAddContactSheet = true },
                                    onMessageContact = { contact ->
                                        currentTab = MainTab.CHATS
                                        chatViewModel.openOrStartChatWithPeer(contact.contactUid, contact.savedName)
                                    },
                                    onCallContact = { contact ->
                                        val avatar = peerProfiles[contact.contactUid]?.avatarData?.ifBlank { contact.avatarData }
                                            ?: contact.avatarData
                                        startCallWithPermission(contact.contactUid, contact.savedName, avatar, "", isVideo = false)
                                    },
                                    onDeleteContact = { contact -> chatViewModel.deleteContact(contact.contactUid) },
                                )
                            }
                            MainTab.LINK_PC -> {
                                LinkToPcScreen(
                                    pcLinksState = pcLinksState,
                                    userDisplayName = currentUserAccount?.displayName.orEmpty().ifBlank { "MJ Chatty User" },
                                    userHandle = currentUserAccount?.username.orEmpty(),
                                    onEnsureDefaultLink = chatViewModel::ensureDefaultPcLink,
                                    onCreateLink = chatViewModel::createPcLink,
                                    onUpdateLinkStatus = chatViewModel::updatePcLinkStatus,
                                    onRenameLink = chatViewModel::renamePcLink,
                                    onDeleteLink = chatViewModel::deletePcLink,
                                    onNotify = chatViewModel::showMessage,
                                )
                            }
                            MainTab.SETTINGS -> {
                                SettingsProfileScreen(
                                    userState = currentUserState,
                                    themeMode = themeMode,
                                    typingStatusConfig = typingStatusConfig,
                                    callSettingsConfig = callSettingsConfig,
                                    notificationSettingsConfig = notificationSettingsConfig,
                                    hasNotificationPermission = hasNotificationPermission,
                                    chatLockConfig = chatLockConfig,
                                    lockedChatsCount = lockedChats.size,
                                    onThemeModeChange = onThemeModeChange,
                                    onSendTypingIndicatorChange = onSendTypingIndicatorChange,
                                    onShowPeerTypingIndicatorChange = onShowPeerTypingIndicatorChange,
                                    onTypingTimeoutChange = onTypingTimeoutChange,
                                    onTypingDisplayStyleChange = onTypingDisplayStyleChange,
                                    onVideoCallingEnabledChange = onVideoCallingEnabledChange,
                                    onStartVideoWithCameraOnChange = onStartVideoWithCameraOnChange,
                                    onPreferFrontCameraChange = onPreferFrontCameraChange,
                                    onDefaultSpeakerForVideoChange = onDefaultSpeakerForVideoChange,
                                    onHdVideoQualityChange = onHdVideoQualityChange,
                                    onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded,
                                    onPushNotificationsEnabledChange = onPushNotificationsEnabledChange,
                                    onIncomingCallNotificationsChange = onIncomingCallNotificationsChange,
                                    onOutgoingCallNotificationsChange = onOutgoingCallNotificationsChange,
                                    onReceivedActivityNotificationsChange = onReceivedActivityNotificationsChange,
                                    onSetOrChangeChatLockCode = {
                                        pendingLockChat = null
                                        showSetPasscodeDialog = true
                                    },
                                    onOpenLockedChatsVault = { handleOpenLockedVault(bypassPrompt = false) },
                                    onHideLockedChatsChange = onHideLockedChatsChange,
                                    onUnlockAllAndClearCode = {
                                        chatViewModel.unlockAllLockedChats()
                                        onClearChatLockCode()
                                    },
                                    onSaveProfile = { name, handle, phone, bio, avatar ->
                                        chatViewModel.saveProfile(name, handle, phone, bio, avatar)
                                    },
                                    onCopyHandleNotice = chatViewModel::showMessage,
                                    onSignOut = {
                                        onPerformSignOut {
                                            chatViewModel.setOnlinePresence(false)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Global Direct WebRTC Audio & Video Call Overlay
        if (activeCall != null || incomingRingingCall != null) {
            WebRtcCallOverlay(
                currentUserId = currentUserId,
                activeCall = activeCall,
                incomingCall = incomingRingingCall,
                callStage = callStage,
                elapsedSeconds = callElapsedSeconds,
                isMuted = isCallMuted,
                isSpeakerOn = isSpeakerOn,
                isCameraEnabled = isCameraEnabled,
                isFrontCamera = isFrontCamera,
                eglBaseContext = chatViewModel.eglBaseContext,
                localVideoTrack = localVideoTrack,
                remoteVideoTrack = remoteVideoTrack,
                peerProfiles = peerProfiles,
                onAcceptIncoming = chatViewModel::acceptIncomingCall,
                onDeclineIncoming = chatViewModel::declineIncomingCall,
                onHangUp = chatViewModel::hangUpActiveCall,
                onToggleMute = chatViewModel::toggleCallMute,
                onToggleSpeaker = chatViewModel::toggleCallSpeaker,
                onToggleCamera = chatViewModel::toggleCallCamera,
                onSwitchCamera = chatViewModel::switchCallCamera,
                onShowError = chatViewModel::showMessage,
            )
        }
    }

    // Chat Lock Modals (Set Secret Code / Unlock Vault / Locked Chats Vault Sheet)
    if (showSetPasscodeDialog) {
        SetChatLockPasscodeDialog(
            isUpdatingExisting = chatLockConfig.hasPasscode,
            subtitle = if (pendingLockChat != null) {
                "Set a secret code first to move this chat into your private Locked Chats."
            } else {
                "Create a secret code (4–16 characters) to protect your locked chats."
            },
            onSaveCode = { newCode ->
                onSaveChatLockCode(newCode)
                showSetPasscodeDialog = false
                val targetToLock = pendingLockChat
                pendingLockChat = null
                if (targetToLock != null) {
                    chatViewModel.toggleLockChat(targetToLock, explicitLock = true)
                } else {
                    chatViewModel.showMessage("Chat Lock secret code saved")
                }
            },
            onDismiss = {
                showSetPasscodeDialog = false
                pendingLockChat = null
            },
        )
    }

    if (showUnlockVaultDialog) {
        UnlockChatLockDialog(
            chatLockConfig = chatLockConfig,
            onUnlocked = {
                showUnlockVaultDialog = false
                showLockedChatsVaultSheet = true
            },
            onDismiss = { showUnlockVaultDialog = false },
        )
    }

    if (showLockedChatsVaultSheet) {
        LockedChatsVaultSheet(
            currentUserId = currentUserId,
            lockedChats = lockedChats,
            contactsByUid = contactsByUid,
            peerProfiles = peerProfiles,
            chatLockConfig = chatLockConfig,
            onOpenChat = { chat ->
                currentTab = MainTab.CHATS
                chatViewModel.openChatThread(chat)
            },
            onUnlockChat = { chat ->
                chatViewModel.toggleLockChat(chat, explicitLock = false)
            },
            onHideLockedChatsChange = onHideLockedChatsChange,
            onChangePasscodeClick = {
                showLockedChatsVaultSheet = false
                pendingLockChat = null
                showSetPasscodeDialog = true
            },
            onDismiss = { showLockedChatsVaultSheet = false },
        )
    }

    if (showCallSettingsSheet) {
        CallSettingsSheet(
            config = callSettingsConfig,
            onVideoCallingEnabledChange = onVideoCallingEnabledChange,
            onStartVideoWithCameraOnChange = onStartVideoWithCameraOnChange,
            onPreferFrontCameraChange = onPreferFrontCameraChange,
            onDefaultSpeakerForVideoChange = onDefaultSpeakerForVideoChange,
            onHdVideoQualityChange = onHdVideoQualityChange,
            onDismiss = { showCallSettingsSheet = false },
        )
    }

    if (showNotificationSettingsSheet) {
        NotificationSettingsSheet(
            config = notificationSettingsConfig,
            hasOsPermission = hasNotificationPermission,
            onRequestOsPermission = ::requestNotificationPermissionIfNeeded,
            onPushNotificationsEnabledChange = onPushNotificationsEnabledChange,
            onIncomingCallNotificationsChange = onIncomingCallNotificationsChange,
            onOutgoingCallNotificationsChange = onOutgoingCallNotificationsChange,
            onReceivedActivityNotificationsChange = onReceivedActivityNotificationsChange,
            onDismiss = { showNotificationSettingsSheet = false },
        )
    }

    if (showAddContactSheet) {
        AddRegisteredContactSheet(
            lookupInput = lookupInput,
            verificationState = verificationState,
            onLookupInputChange = chatViewModel::updateContactLookupInput,
            onSaveAndChat = { entry, savedName, typedPhone ->
                chatViewModel.saveVerifiedContact(entry, savedName, typedPhone) { saved ->
                    showAddContactSheet = false
                    currentTab = MainTab.CHATS
                    chatViewModel.openOrStartChatWithPeer(saved.contactUid, saved.savedName)
                }
            },
            onDismiss = {
                showAddContactSheet = false
                chatViewModel.resetContactLookup()
            },
        )
    }
}
