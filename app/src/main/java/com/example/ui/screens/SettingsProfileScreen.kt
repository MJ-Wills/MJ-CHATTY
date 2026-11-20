package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CallSettingsConfig
import com.example.data.ChatIdentityUtils
import com.example.data.ChatLockConfig
import com.example.data.ImageMediaUtils
import com.example.data.NotificationSettingsConfig
import com.example.data.ThemeMode
import com.example.data.TypingDisplayStyle
import com.example.data.TypingStatusConfig
import com.example.data.UiState
import com.example.data.UserAccount
import com.example.ui.components.CallSettingsCard
import com.example.ui.components.ConfiguredTypingIndicator
import com.example.ui.components.MjAvatar
import com.example.ui.components.NotificationSettingsCard
import com.example.ui.theme.LocalMjColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsProfileScreen(
    userState: UiState<UserAccount?>,
    themeMode: ThemeMode,
    typingStatusConfig: TypingStatusConfig,
    callSettingsConfig: CallSettingsConfig,
    notificationSettingsConfig: NotificationSettingsConfig,
    hasNotificationPermission: Boolean,
    chatLockConfig: ChatLockConfig,
    lockedChatsCount: Int,
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
    onRequestNotificationPermission: () -> Unit,
    onPushNotificationsEnabledChange: (Boolean) -> Unit,
    onIncomingCallNotificationsChange: (Boolean) -> Unit,
    onOutgoingCallNotificationsChange: (Boolean) -> Unit,
    onReceivedActivityNotificationsChange: (Boolean) -> Unit,
    onSetOrChangeChatLockCode: () -> Unit,
    onOpenLockedChatsVault: () -> Unit,
    onHideLockedChatsChange: (Boolean) -> Unit,
    onUnlockAllAndClearCode: () -> Unit,
    onSaveProfile: (displayName: String, username: String, phoneNumber: String, about: String, avatarData: String) -> Unit,
    onCopyHandleNotice: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val mjColors = LocalMjColors.current

    val account = (userState as? UiState.Success)?.data

    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var avatarData by remember { mutableStateOf("") }
    var initializedUid by remember { mutableStateOf<String?>(null) }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var privacyExpanded by remember { mutableStateOf(false) }
    var confirmClearLockDialog by remember { mutableStateOf(false) }

    LaunchedEffect(account) {
        if (account != null && initializedUid != account.userId) {
            displayName = account.displayName
            username = account.username
            phoneNumber = account.phoneNumber
            about = account.about
            avatarData = account.avatarData
            initializedUid = account.userId
        }
    }

    // Zero-permission Android Photo Picker for Profile Picture from device storage
    val profilePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            isProcessingPhoto = true
            scope.launch {
                val encoded = ImageMediaUtils.encodeImageUriToDataUri(
                    context = context,
                    uri = uri,
                    maxDimension = 300,
                    jpegQuality = 78,
                )
                isProcessingPhoto = false
                if (encoded != null) {
                    avatarData = encoded
                    onSaveProfile(
                        displayName.ifBlank { account?.displayName ?: "MJ Chatty User" },
                        username.ifBlank { account?.username ?: "mjuser" },
                        phoneNumber,
                        about,
                        encoded,
                    )
                } else {
                    onCopyHandleNotice("Could not load selected image. Try another photo.")
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    Text(
                        text = stringResource(R.string.nav_settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. PROFILE CARD & PHOTO PICKER
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.55f)),
                    shadowElevation = if (mjColors.isDark) 4.dp else 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clickable {
                                        profilePhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    .testTag("settings_pick_avatar_button"),
                            ) {
                                MjAvatar(
                                    name = displayName.ifBlank { "MJ" },
                                    avatarData = avatarData,
                                    size = 84.dp,
                                    online = true,
                                    showOnlineIndicator = false,
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = mjColors.neonCyan,
                                    shadowElevation = 4.dp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(30.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isProcessingPhoto) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = Color(0xFF001C2B),
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.CameraAlt,
                                                contentDescription = "Choose profile photo from device",
                                                tint = Color(0xFF001C2B),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName.ifBlank { "Loading profile…" },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (username.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "@$username",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = mjColors.neonCyan,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        IconButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString("@$username"))
                                                onCopyHandleNotice("Copied @$username to clipboard")
                                            },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ContentCopy,
                                                contentDescription = "Copy username",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Tap avatar to pick photo from gallery",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.settings_profile_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = mjColors.neonCyan,
                            fontWeight = FontWeight.Bold,
                        )

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(60) },
                            label = { Text("Profile Name") },
                            leadingIcon = {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = mjColors.neonCyan)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = mjColors.neonCyan,
                                unfocusedBorderColor = mjColors.subtleBorder,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_profile_name_input"),
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = ChatIdentityUtils.sanitizeUsername(it) },
                            label = { Text("Username") },
                            prefix = { Text("@", color = mjColors.neonCyan, fontWeight = FontWeight.Bold) },
                            supportingText = {
                                Text("3–24 chars (a-z, 0-9, _). Friends find & verify you with this handle.")
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.AlternateEmail, contentDescription = null, tint = mjColors.neonEmerald)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = mjColors.neonCyan,
                                unfocusedBorderColor = mjColors.subtleBorder,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_username_input"),
                        )

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { next ->
                                phoneNumber = next.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' }.take(20)
                            },
                            label = { Text("Phone Number (for contact verification)") },
                            placeholder = { Text("+1 555 019 2834") },
                            leadingIcon = {
                                Icon(Icons.Filled.Phone, contentDescription = null, tint = mjColors.neonAmber)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = mjColors.neonCyan,
                                unfocusedBorderColor = mjColors.subtleBorder,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_phone_input"),
                        )

                        OutlinedTextField(
                            value = about,
                            onValueChange = { about = it.take(160) },
                            label = { Text("About / Status") },
                            leadingIcon = {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            maxLines = 2,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = mjColors.neonCyan,
                                unfocusedBorderColor = mjColors.subtleBorder,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_about_input"),
                        )

                        Button(
                            onClick = {
                                onSaveProfile(displayName, username, phoneNumber, about, avatarData)
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = mjColors.neonCyan,
                                contentColor = Color(0xFF001C2B),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("settings_save_profile_button"),
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Profile", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 2. APPEARANCE / DARK MODE CARD
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_appearance_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = mjColors.neonCyan,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ThemeModeChip(
                                label = "Dark",
                                icon = Icons.Filled.DarkMode,
                                selected = themeMode == ThemeMode.DARK,
                                onClick = { onThemeModeChange(ThemeMode.DARK) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_mode_dark"),
                            )
                            ThemeModeChip(
                                label = "Light",
                                icon = Icons.Filled.LightMode,
                                selected = themeMode == ThemeMode.LIGHT,
                                onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_mode_light"),
                            )
                            ThemeModeChip(
                                label = "System",
                                icon = Icons.Filled.BrightnessAuto,
                                selected = themeMode == ThemeMode.SYSTEM,
                                onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_mode_system"),
                            )
                        }
                    }
                }

                // 3. TYPING STATUS CONFIGURATION CARD
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_typing_config_card"),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_typing_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = mjColors.neonCyan,
                            fontWeight = FontWeight.Bold,
                        )

                        // Share typing status switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_typing_send_label),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.settings_typing_send_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = typingStatusConfig.sendTypingIndicator,
                                onCheckedChange = onSendTypingIndicatorChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF001C2B),
                                    checkedTrackColor = mjColors.neonCyan,
                                ),
                                modifier = Modifier.testTag("settings_typing_send_switch"),
                            )
                        }

                        HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.4f))

                        // Show peer typing status switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_typing_receive_label),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.settings_typing_receive_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = typingStatusConfig.showPeerTypingIndicator,
                                onCheckedChange = onShowPeerTypingIndicatorChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF002410),
                                    checkedTrackColor = mjColors.neonEmerald,
                                ),
                                modifier = Modifier.testTag("settings_typing_receive_switch"),
                            )
                        }

                        HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.4f))

                        // Typing auto-hide timeout presets
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.settings_typing_timeout_label),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            val timeoutOptions = listOf(2, 3, 5, 8)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                timeoutOptions.forEach { seconds ->
                                    val selected = typingStatusConfig.typingTimeoutSeconds == seconds
                                    Surface(
                                        onClick = { onTypingTimeoutChange(seconds) },
                                        enabled = typingStatusConfig.sendTypingIndicator,
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected && typingStatusConfig.sendTypingIndicator) {
                                            mjColors.neonCyan.copy(alpha = 0.18f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        },
                                        border = BorderStroke(
                                            width = if (selected && typingStatusConfig.sendTypingIndicator) 1.5.dp else 1.dp,
                                            color = if (selected && typingStatusConfig.sendTypingIndicator) {
                                                mjColors.neonCyan
                                            } else {
                                                mjColors.subtleBorder.copy(alpha = 0.45f)
                                            },
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("typing_timeout_$seconds"),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${seconds}s",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (!typingStatusConfig.sendTypingIndicator) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                } else if (selected) {
                                                    mjColors.neonCyan
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Indicator Display Style selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.settings_typing_style_label),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TypingDisplayStyle.entries.forEach { style ->
                                    val selected = typingStatusConfig.displayStyle == style
                                    Surface(
                                        onClick = { onTypingDisplayStyleChange(style) },
                                        enabled = typingStatusConfig.showPeerTypingIndicator,
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected && typingStatusConfig.showPeerTypingIndicator) {
                                            mjColors.neonEmerald.copy(alpha = 0.18f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        },
                                        border = BorderStroke(
                                            width = if (selected && typingStatusConfig.showPeerTypingIndicator) 1.5.dp else 1.dp,
                                            color = if (selected && typingStatusConfig.showPeerTypingIndicator) {
                                                mjColors.neonEmerald
                                            } else {
                                                mjColors.subtleBorder.copy(alpha = 0.45f)
                                            },
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("typing_style_${style.key}"),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = style.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (!typingStatusConfig.showPeerTypingIndicator) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                } else if (selected) {
                                                    mjColors.neonEmerald
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Live preview pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.4f)),
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
                                    text = "Live preview",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (typingStatusConfig.showPeerTypingIndicator) {
                                    ConfiguredTypingIndicator(
                                        style = typingStatusConfig.displayStyle,
                                        label = stringResource(R.string.status_typing),
                                        accentColor = mjColors.neonEmerald,
                                        textStyle = MaterialTheme.typography.labelMedium,
                                    )
                                } else {
                                    Text(
                                        text = "Hidden",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. CALL SETTINGS (AUDIO & VIDEO WEBRTC)
                CallSettingsCard(
                    config = callSettingsConfig,
                    onVideoCallingEnabledChange = onVideoCallingEnabledChange,
                    onStartVideoWithCameraOnChange = onStartVideoWithCameraOnChange,
                    onPreferFrontCameraChange = onPreferFrontCameraChange,
                    onDefaultSpeakerForVideoChange = onDefaultSpeakerForVideoChange,
                    onHdVideoQualityChange = onHdVideoQualityChange,
                )

                // 5. PUSH NOTIFICATIONS & ACTIVITY ALERTS
                NotificationSettingsCard(
                    config = notificationSettingsConfig,
                    hasOsPermission = hasNotificationPermission,
                    onRequestOsPermission = onRequestNotificationPermission,
                    onPushNotificationsEnabledChange = onPushNotificationsEnabledChange,
                    onIncomingCallNotificationsChange = onIncomingCallNotificationsChange,
                    onOutgoingCallNotificationsChange = onOutgoingCallNotificationsChange,
                    onReceivedActivityNotificationsChange = onReceivedActivityNotificationsChange,
                )

                // 6. PRIVACY & SECURITY (HIDDEN CHAT LOCK SECTION — WHATSAPP STYLE)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_privacy_section_card"),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { privacyExpanded = !privacyExpanded }
                                .padding(vertical = 4.dp)
                                .testTag("settings_privacy_expand_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(mjColors.neonEmerald.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Security,
                                        contentDescription = null,
                                        tint = mjColors.neonEmerald,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Privacy & Security",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = if (chatLockConfig.hasPasscode) {
                                            "Chat Lock active • Tap to manage"
                                        } else {
                                            "Chat Lock, secret code & hidden chats"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (privacyExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (privacyExpanded) "Collapse privacy options" else "Expand privacy options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        AnimatedVisibility(
                            visible = privacyExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.4f))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = mjColors.neonEmerald,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.chat_lock_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = mjColors.neonEmerald,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                Text(
                                    text = "Keep private chats locked behind a secret code. Lock or unlock any chat from its options menu (⋮) or long-press.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                // Secret Code Setup / Change
                                OutlinedButton(
                                    onClick = onSetOrChangeChatLockCode,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, mjColors.neonCyan.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("settings_chat_lock_code_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Key,
                                        contentDescription = null,
                                        tint = mjColors.neonCyan,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (chatLockConfig.hasPasscode) "Change Secret Code" else "Set Secret Code",
                                        color = mjColors.neonCyan,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                // Hide Locked Chats toggle (WhatsApp-style secret search access)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            text = stringResource(R.string.chat_lock_hide_label),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = stringResource(R.string.chat_lock_hide_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = chatLockConfig.hideLockedChats,
                                        enabled = chatLockConfig.hasPasscode,
                                        onCheckedChange = onHideLockedChatsChange,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF002410),
                                            checkedTrackColor = mjColors.neonEmerald,
                                        ),
                                        modifier = Modifier.testTag("settings_hide_locked_chats_switch"),
                                    )
                                }

                                // Discreet Open Locked Chats Vault button inside Settings
                                Button(
                                    onClick = onOpenLockedChatsVault,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = mjColors.neonEmerald,
                                        contentColor = Color(0xFF002410),
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("settings_open_locked_chats_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LockOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (!chatLockConfig.hideLockedChats && lockedChatsCount > 0) {
                                            "Open Locked Chats ($lockedChatsCount)"
                                        } else {
                                            "Open Locked Chats"
                                        },
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                if (chatLockConfig.hasPasscode) {
                                    TextButton(
                                        onClick = { confirmClearLockDialog = true },
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .testTag("settings_clear_chat_lock_button"),
                                    ) {
                                        Text(
                                            text = "Unlock All & Remove Secret Code",
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. MJ CHATTY BRAND & PLATFORM ARCHITECTURE INFO CARD
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = remember(context) {
                                    ImageRequest.Builder(context)
                                        .data(R.drawable.mj_chatty_logo_brand)
                                        .size(160)
                                        .crossfade(false)
                                        .build()
                                },
                                contentDescription = "MJ Chatty logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Column {
                                Text(
                                    text = "MJ Chatty",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "Cloud-synced real-time messaging • v1.0",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RoadmapPill(icon = Icons.Filled.CloudDone, label = "Cloud Sync", tint = mjColors.neonEmerald, modifier = Modifier.weight(1f))
                            RoadmapPill(icon = Icons.Filled.Lock, label = "Chat Lock", tint = mjColors.neonCyan, modifier = Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RoadmapPill(icon = Icons.Filled.Videocam, label = "WebRTC Video", tint = mjColors.neonAmber, modifier = Modifier.weight(1f))
                            RoadmapPill(icon = Icons.Filled.Call, label = "HD Voice", tint = mjColors.neonBlue, modifier = Modifier.weight(1f))
                        }
                    }
                }

                // 7. SIGN OUT
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("settings_sign_out_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_sign_out),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (confirmClearLockDialog) {
        AlertDialog(
            onDismissRequest = { confirmClearLockDialog = false },
            title = { Text("Remove Secret Code?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will unlock all currently locked chats and remove your Chat Lock secret code.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearLockDialog = false
                        onUnlockAllAndClearCode()
                    },
                ) {
                    Text("Unlock All & Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearLockDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ThemeModeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mjColors = LocalMjColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) mjColors.neonCyan.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) mjColors.neonCyan else mjColors.subtleBorder.copy(alpha = 0.5f),
        ),
        modifier = modifier.height(52.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) mjColors.neonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RoadmapPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.11f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
