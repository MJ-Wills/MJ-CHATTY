package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CallSettingsConfig
import com.example.data.ChatLockConfig
import com.example.data.ChatThread
import com.example.data.NotificationSettingsConfig
import com.example.data.PublicProfile
import com.example.data.SavedContact
import com.example.ui.screens.resolveChatTitle
import com.example.ui.theme.LocalMjColors

@Composable
fun SetChatLockPasscodeDialog(
    isUpdatingExisting: Boolean,
    subtitle: String = "Create a secret code (4–16 characters) to protect your locked chats.",
    onSaveCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mjColors = LocalMjColors.current
    var code by remember { mutableStateOf("") }
    var confirmCode by remember { mutableStateOf("") }
    var showCode by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(mjColors.neonCyan.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    tint = mjColors.neonCyan,
                )
            }
        },
        title = {
            Text(
                text = if (isUpdatingExisting) "Change Secret Code" else "Set Chat Lock Secret Code",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.take(16)
                        errorText = null
                    },
                    label = { Text("Secret Code") },
                    placeholder = { Text("e.g. 2468 or secretword") },
                    singleLine = true,
                    visualTransformation = if (showCode) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showCode = !showCode }) {
                            Icon(
                                imageVector = if (showCode) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showCode) "Hide secret code" else "Show secret code",
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = mjColors.neonCyan,
                        unfocusedBorderColor = mjColors.subtleBorder,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_lock_new_code_input"),
                )
                OutlinedTextField(
                    value = confirmCode,
                    onValueChange = {
                        confirmCode = it.take(16)
                        errorText = null
                    },
                    label = { Text("Confirm Secret Code") },
                    singleLine = true,
                    visualTransformation = if (showCode) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = mjColors.neonCyan,
                        unfocusedBorderColor = mjColors.subtleBorder,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_lock_confirm_code_input"),
                )
                if (errorText != null) {
                    Text(
                        text = errorText.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = code.trim()
                    when {
                        trimmed.length < 4 -> errorText = "Use at least 4 characters or digits."
                        trimmed != confirmCode.trim() -> errorText = "Secret codes do not match."
                        else -> onSaveCode(trimmed)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = mjColors.neonCyan,
                    contentColor = Color(0xFF001C2B),
                ),
                modifier = Modifier.testTag("chat_lock_save_code_button"),
            ) {
                Text("Save Code", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun UnlockChatLockDialog(
    chatLockConfig: ChatLockConfig,
    title: String = "Unlock Locked Chats",
    subtitle: String = "Enter your secret code to access your locked chats.",
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mjColors = LocalMjColors.current
    var inputCode by remember { mutableStateOf("") }
    var showCode by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(mjColors.neonEmerald.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = mjColors.neonEmerald,
                )
            }
        },
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = inputCode,
                    onValueChange = {
                        inputCode = it.take(16)
                        errorText = null
                    },
                    label = { Text("Secret Code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showCode) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showCode = !showCode }) {
                            Icon(
                                imageVector = if (showCode) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showCode) "Hide secret code" else "Show secret code",
                            )
                        }
                    },
                    isError = errorText != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = mjColors.neonEmerald,
                        unfocusedBorderColor = mjColors.subtleBorder,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_lock_unlock_input"),
                )
                if (errorText != null) {
                    Text(
                        text = errorText.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (chatLockConfig.matches(inputCode)) {
                        onUnlocked()
                    } else {
                        errorText = "Incorrect secret code. Try again."
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = mjColors.neonEmerald,
                    contentColor = Color(0xFF002410),
                ),
                modifier = Modifier.testTag("chat_lock_unlock_confirm"),
            ) {
                Text("Unlock", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedChatsVaultSheet(
    currentUserId: String,
    lockedChats: List<ChatThread>,
    contactsByUid: Map<String, SavedContact>,
    peerProfiles: Map<String, PublicProfile>,
    chatLockConfig: ChatLockConfig,
    onOpenChat: (ChatThread) -> Unit,
    onUnlockChat: (ChatThread) -> Unit,
    onHideLockedChatsChange: (Boolean) -> Unit,
    onChangePasscodeClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mjColors = LocalMjColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .testTag("locked_chats_vault_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(mjColors.neonEmerald.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = mjColors.neonEmerald,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.chat_lock_locked_folder),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${lockedChats.size} private ${if (lockedChats.size == 1) "chat" else "chats"} protected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close locked chats")
                }
            }

            // Quick Vault Settings strip (Hide locked chats + Secret Code)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = stringResource(R.string.chat_lock_hide_label),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Access via secret code in Search or Settings",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = chatLockConfig.hideLockedChats,
                            onCheckedChange = onHideLockedChatsChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF002410),
                                checkedTrackColor = mjColors.neonEmerald,
                            ),
                            modifier = Modifier.testTag("vault_hide_locked_switch"),
                        )
                    }
                    OutlinedButton(
                        onClick = onChangePasscodeClick,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, mjColors.neonCyan.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_change_code_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = mjColors.neonCyan,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Secret Code", color = mjColors.neonCyan, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (lockedChats.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LockOpen,
                            contentDescription = null,
                            tint = mjColors.neonCyan,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = "No locked chats yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Open any chat's 3-dots menu (⋮) or long-press a chat in your list and choose Lock Chat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(lockedChats, key = { it.id }) { chat ->
                        val peerUid = chat.peerUid(currentUserId)
                        val livePeer = peerProfiles[peerUid]
                        val savedContact = contactsByUid[peerUid]
                        val title = resolveChatTitle(chat, currentUserId, contactsByUid, peerProfiles)
                        val avatar = livePeer?.avatarData?.ifBlank { savedContact?.avatarData.orEmpty() }
                            ?: savedContact?.avatarData.orEmpty()

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, mjColors.neonEmerald.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    onDismiss()
                                    onOpenChat(chat)
                                }
                                .testTag("locked_chat_row_${chat.id}"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MjAvatar(
                                    name = title,
                                    avatarData = avatar,
                                    size = 46.dp,
                                    online = livePeer?.online == true,
                                    showOnlineIndicator = true,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = chat.lastMessagePreview.ifBlank { "Tap to open private chat" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = { onUnlockChat(chat) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("unlock_chat_btn_${chat.id}"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LockOpen,
                                        contentDescription = "Unlock $title",
                                        tint = mjColors.neonCyan,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun CallSettingsCard(
    config: CallSettingsConfig,
    onVideoCallingEnabledChange: (Boolean) -> Unit,
    onStartVideoWithCameraOnChange: (Boolean) -> Unit,
    onPreferFrontCameraChange: (Boolean) -> Unit,
    onDefaultSpeakerForVideoChange: (Boolean) -> Unit,
    onHdVideoQualityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mjColors = LocalMjColors.current

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_call_config_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = null,
                    tint = mjColors.neonCyan,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.call_settings_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = mjColors.neonCyan,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Master Video Calling Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.call_settings_video_label),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.call_settings_video_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.videoCallingEnabled,
                    onCheckedChange = onVideoCallingEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF001C2B),
                        checkedTrackColor = mjColors.neonCyan,
                    ),
                    modifier = Modifier.testTag("call_settings_video_enabled_switch"),
                )
            }

            HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.4f))

            // Start video calls with camera on
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.call_settings_camera_start_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (config.videoCallingEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                    )
                    Text(
                        text = "Turn camera on immediately when placing or answering video calls",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.startVideoWithCameraOn,
                    onCheckedChange = onStartVideoWithCameraOnChange,
                    enabled = config.videoCallingEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF002410),
                        checkedTrackColor = mjColors.neonEmerald,
                    ),
                    modifier = Modifier.testTag("call_settings_camera_start_switch"),
                )
            }

            // Auto speakerphone for video calls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.call_settings_speaker_video_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (config.videoCallingEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                    )
                    Text(
                        text = "Route video call audio to speakerphone automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.defaultSpeakerForVideo,
                    onCheckedChange = onDefaultSpeakerForVideoChange,
                    enabled = config.videoCallingEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF001C2B),
                        checkedTrackColor = mjColors.neonCyan,
                    ),
                    modifier = Modifier.testTag("call_settings_speaker_video_switch"),
                )
            }

            // HD Video Quality (720p vs Data Saver 480p)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.HighQuality,
                            contentDescription = null,
                            tint = mjColors.neonAmber,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.call_settings_hd_video_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (config.videoCallingEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            },
                        )
                    }
                    Text(
                        text = if (config.hdVideoQuality) "720p • 30fps crisp WebRTC video" else "480p • 24fps data saver mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.hdVideoQuality,
                    onCheckedChange = onHdVideoQualityChange,
                    enabled = config.videoCallingEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF1F1300),
                        checkedTrackColor = mjColors.neonAmber,
                    ),
                    modifier = Modifier.testTag("call_settings_hd_video_switch"),
                )
            }

            HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.4f))

            // Default Camera Lens Selector (Front / Back)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cameraswitch,
                        contentDescription = null,
                        tint = mjColors.neonCyan,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.call_settings_camera_lens_label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val lensOptions = listOf(true to "Front Camera", false to "Back Camera")
                    lensOptions.forEach { (isFront, label) ->
                        val selected = config.preferFrontCamera == isFront
                        Surface(
                            onClick = { onPreferFrontCameraChange(isFront) },
                            enabled = config.videoCallingEnabled,
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected && config.videoCallingEnabled) {
                                mjColors.neonCyan.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            border = BorderStroke(
                                width = if (selected && config.videoCallingEnabled) 1.5.dp else 1.dp,
                                color = if (selected && config.videoCallingEnabled) {
                                    mjColors.neonCyan
                                } else {
                                    mjColors.subtleBorder.copy(alpha = 0.45f)
                                },
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag(if (isFront) "call_lens_front" else "call_lens_back"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (!config.videoCallingEnabled) {
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSettingsSheet(
    config: CallSettingsConfig,
    onVideoCallingEnabledChange: (Boolean) -> Unit,
    onStartVideoWithCameraOnChange: (Boolean) -> Unit,
    onPreferFrontCameraChange: (Boolean) -> Unit,
    onDefaultSpeakerForVideoChange: (Boolean) -> Unit,
    onHdVideoQualityChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CallSettingsCard(
                config = config,
                onVideoCallingEnabledChange = onVideoCallingEnabledChange,
                onStartVideoWithCameraOnChange = onStartVideoWithCameraOnChange,
                onPreferFrontCameraChange = onPreferFrontCameraChange,
                onDefaultSpeakerForVideoChange = onDefaultSpeakerForVideoChange,
                onHdVideoQualityChange = onHdVideoQualityChange,
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
fun NotificationSettingsCard(
    config: NotificationSettingsConfig,
    hasOsPermission: Boolean,
    onRequestOsPermission: () -> Unit,
    onPushNotificationsEnabledChange: (Boolean) -> Unit,
    onIncomingCallNotificationsChange: (Boolean) -> Unit,
    onOutgoingCallNotificationsChange: (Boolean) -> Unit,
    onReceivedActivityNotificationsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mjColors = LocalMjColors.current
    val subControlsEnabled = config.pushNotificationsEnabled

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_notifications_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (hasOsPermission && config.pushNotificationsEnabled) {
                        Icons.Filled.NotificationsActive
                    } else {
                        Icons.Filled.NotificationsOff
                    },
                    contentDescription = null,
                    tint = if (hasOsPermission && config.pushNotificationsEnabled) mjColors.neonEmerald else mjColors.neonAmber,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.notif_settings_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = mjColors.neonCyan,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (!hasOsPermission) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = mjColors.neonAmber.copy(alpha = if (mjColors.isDark) 0.16f else 0.14f),
                    border = BorderStroke(1.dp, mjColors.neonAmber.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System permission needed",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Grant Android notification access so calls and messages can alert you.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = onRequestOsPermission,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = mjColors.neonCyan,
                                contentColor = Color(0xFF001C2B),
                            ),
                            modifier = Modifier.testTag("notif_grant_permission_button"),
                        ) {
                            Text("Allow", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Master Push Notification Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.notif_master_label),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.notif_master_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.pushNotificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasOsPermission) {
                            onRequestOsPermission()
                        }
                        onPushNotificationsEnabledChange(enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF002410),
                        checkedTrackColor = mjColors.neonEmerald,
                    ),
                    modifier = Modifier.testTag("notif_master_switch"),
                )
            }

            HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.4f))

            // Incoming Call Alerts
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallReceived,
                        contentDescription = null,
                        tint = mjColors.neonEmerald,
                        modifier = Modifier.size(18.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.notif_incoming_calls_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (subControlsEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            },
                        )
                        Text(
                            text = stringResource(R.string.notif_incoming_calls_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = config.incomingCallNotifications,
                    onCheckedChange = onIncomingCallNotificationsChange,
                    enabled = subControlsEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF002410),
                        checkedTrackColor = mjColors.neonEmerald,
                    ),
                    modifier = Modifier.testTag("notif_incoming_calls_switch"),
                )
            }

            // Outgoing Call Activity Alerts
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallMade,
                        contentDescription = null,
                        tint = mjColors.neonCyan,
                        modifier = Modifier.size(18.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.notif_outgoing_calls_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (subControlsEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            },
                        )
                        Text(
                            text = stringResource(R.string.notif_outgoing_calls_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = config.outgoingCallNotifications,
                    onCheckedChange = onOutgoingCallNotificationsChange,
                    enabled = subControlsEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF001C2B),
                        checkedTrackColor = mjColors.neonCyan,
                    ),
                    modifier = Modifier.testTag("notif_outgoing_calls_switch"),
                )
            }

            // Received Messages & Missed Calls Alerts
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = mjColors.neonBlue,
                        modifier = Modifier.size(18.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.notif_received_activity_label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (subControlsEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            },
                        )
                        Text(
                            text = stringResource(R.string.notif_received_activity_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = config.receivedActivityNotifications,
                    onCheckedChange = onReceivedActivityNotificationsChange,
                    enabled = subControlsEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF001C2B),
                        checkedTrackColor = mjColors.neonBlue,
                    ),
                    modifier = Modifier.testTag("notif_received_activity_switch"),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsSheet(
    config: NotificationSettingsConfig,
    hasOsPermission: Boolean,
    onRequestOsPermission: () -> Unit,
    onPushNotificationsEnabledChange: (Boolean) -> Unit,
    onIncomingCallNotificationsChange: (Boolean) -> Unit,
    onOutgoingCallNotificationsChange: (Boolean) -> Unit,
    onReceivedActivityNotificationsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NotificationSettingsCard(
                config = config,
                hasOsPermission = hasOsPermission,
                onRequestOsPermission = onRequestOsPermission,
                onPushNotificationsEnabledChange = onPushNotificationsEnabledChange,
                onIncomingCallNotificationsChange = onIncomingCallNotificationsChange,
                onOutgoingCallNotificationsChange = onOutgoingCallNotificationsChange,
                onReceivedActivityNotificationsChange = onReceivedActivityNotificationsChange,
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
