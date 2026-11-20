package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.CallSession
import com.example.data.CallSettingsConfig
import com.example.data.PublicProfile
import com.example.data.SavedContact
import com.example.data.UiState
import com.example.data.WebRtcConnectionStage
import com.example.ui.components.CallSettingsSheet
import com.example.ui.components.MjAvatar
import com.example.ui.components.formatChatTimestamp
import com.example.ui.theme.LocalMjColors
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private data class PendingCallRequest(
    val peerUid: String,
    val peerName: String,
    val peerAvatar: String,
    val chatId: String,
    val isVideo: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(
    currentUserId: String,
    callsState: UiState<List<CallSession>>,
    contacts: List<SavedContact>,
    peerProfiles: Map<String, PublicProfile>,
    callSettingsConfig: CallSettingsConfig,
    onStartAudioCall: (peerUid: String, peerName: String, peerAvatar: String, chatId: String) -> Unit,
    onStartVideoCall: (peerUid: String, peerName: String, peerAvatar: String, chatId: String) -> Unit,
    onVideoCallingEnabledChange: (Boolean) -> Unit,
    onStartVideoWithCameraOnChange: (Boolean) -> Unit,
    onPreferFrontCameraChange: (Boolean) -> Unit,
    onDefaultSpeakerForVideoChange: (Boolean) -> Unit,
    onHdVideoQualityChange: (Boolean) -> Unit,
    onDeleteCallLog: (String) -> Unit,
    onAddContactClick: () -> Unit,
    onShowError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mjColors = LocalMjColors.current
    val contactsByUid = remember(contacts) { contacts.associateBy { it.contactUid } }

    var pendingRequest by remember { mutableStateOf<PendingCallRequest?>(null) }
    var pendingDeleteCall by remember { mutableStateOf<CallSession?>(null) }
    var showCallSettingsSheet by remember { mutableStateOf(false) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val req = pendingRequest
        pendingRequest = null
        if (req == null) return@rememberLauncherForActivityResult

        val audioOk = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraOk = !req.isVideo ||
            results[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (audioOk && cameraOk) {
            if (req.isVideo) {
                onStartVideoCall(req.peerUid, req.peerName, req.peerAvatar, req.chatId)
            } else {
                onStartAudioCall(req.peerUid, req.peerName, req.peerAvatar, req.chatId)
            }
        } else {
            onShowError(
                context.getString(
                    if (req.isVideo) R.string.permission_video_call_denied else R.string.permission_audio_denied
                )
            )
        }
    }

    fun requestAndCall(
        peerUid: String,
        peerName: String,
        peerAvatar: String,
        chatId: String = "",
        isVideo: Boolean = false,
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
                onStartVideoCall(peerUid, peerName, peerAvatar, chatId)
            } else {
                onStartAudioCall(peerUid, peerName, peerAvatar, chatId)
            }
        } else {
            pendingRequest = PendingCallRequest(peerUid, peerName, peerAvatar, chatId, isVideo)
            val perms = if (isVideo) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            permissionsLauncher.launch(perms)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(mjColors.neonBlue, mjColors.neonCyan)
                                        )
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhoneInTalk,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.nav_calls),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (callSettingsConfig.videoCallingEnabled) {
                                        "Direct WebRTC Audio & Video"
                                    } else {
                                        "Direct WebRTC HD Audio"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mjColors.neonCyan,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showCallSettingsSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("calls_settings_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "Call settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(
                            onClick = onAddContactClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("calls_add_contact_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = "Add contact to call",
                                tint = mjColors.neonCyan,
                            )
                        }
                    },
                )
                HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.35f))
            }
        },
    ) { innerPadding ->
        when (callsState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = mjColors.neonCyan)
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = callsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            is UiState.Success -> {
                val calls = callsState.data
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp, start = 14.dp, end = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (contacts.isNotEmpty()) {
                        item(key = "quick_call_contacts") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (callSettingsConfig.videoCallingEnabled) "Quick Audio & Video Call" else "Quick Audio Call",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                ) {
                                    items(contacts, key = { "qc_${it.contactUid}" }) { contact ->
                                        val livePeer = peerProfiles[contact.contactUid]
                                        val avatar = livePeer?.avatarData?.ifBlank { contact.avatarData }
                                            ?: contact.avatarData
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(
                                                1.dp,
                                                if (livePeer?.online == true) {
                                                    mjColors.neonEmerald.copy(alpha = 0.45f)
                                                } else {
                                                    mjColors.subtleBorder.copy(alpha = 0.45f)
                                                },
                                            ),
                                            modifier = Modifier
                                                .width(if (callSettingsConfig.videoCallingEnabled) 132.dp else 108.dp)
                                                .testTag("quick_call_${contact.contactUid}"),
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                            ) {
                                                MjAvatar(
                                                    name = contact.savedName,
                                                    avatarData = avatar,
                                                    size = 48.dp,
                                                    online = livePeer?.online == true,
                                                    showOnlineIndicator = true,
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = contact.savedName.substringBefore(" "),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Surface(
                                                        onClick = {
                                                            requestAndCall(
                                                                peerUid = contact.contactUid,
                                                                peerName = contact.savedName,
                                                                peerAvatar = avatar,
                                                                isVideo = false,
                                                            )
                                                        },
                                                        shape = CircleShape,
                                                        color = mjColors.neonEmerald.copy(alpha = 0.16f),
                                                        modifier = Modifier
                                                            .size(38.dp)
                                                            .testTag("quick_audio_call_${contact.contactUid}"),
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Call,
                                                                contentDescription = "Audio call ${contact.savedName}",
                                                                tint = mjColors.neonEmerald,
                                                                modifier = Modifier.size(18.dp),
                                                            )
                                                        }
                                                    }
                                                    if (callSettingsConfig.videoCallingEnabled) {
                                                        Surface(
                                                            onClick = {
                                                                requestAndCall(
                                                                    peerUid = contact.contactUid,
                                                                    peerName = contact.savedName,
                                                                    peerAvatar = avatar,
                                                                    isVideo = true,
                                                                )
                                                            },
                                                            shape = CircleShape,
                                                            color = mjColors.neonCyan.copy(alpha = 0.16f),
                                                            modifier = Modifier
                                                                .size(38.dp)
                                                                .testTag("quick_video_call_${contact.contactUid}"),
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.Videocam,
                                                                    contentDescription = "Video call ${contact.savedName}",
                                                                    tint = mjColors.neonCyan,
                                                                    modifier = Modifier.size(18.dp),
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "recent_calls_header") {
                        Text(
                            text = "Recent Calls",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }

                    if (calls.isEmpty()) {
                        item(key = "empty_calls_card") {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(mjColors.neonBlue.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = if (callSettingsConfig.videoCallingEnabled) Icons.Filled.Videocam else Icons.Filled.Call,
                                            contentDescription = null,
                                            tint = mjColors.neonCyan,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                    Text(
                                        text = "No calls yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "Place direct peer-to-peer WebRTC audio and video calls from any chat header or tap a contact above.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                    if (contacts.isEmpty()) {
                                        Button(
                                            onClick = onAddContactClick,
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = mjColors.neonCyan,
                                                contentColor = Color(0xFF001C2B),
                                            ),
                                            modifier = Modifier.testTag("calls_empty_add_contact"),
                                        ) {
                                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Add a Contact to Call", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(calls, key = { it.id }) { call ->
                            val peerUid = call.peerUid(currentUserId)
                            val livePeer = peerProfiles[peerUid]
                            val savedContact = contactsByUid[peerUid]
                            val displayName = savedContact?.savedName
                                ?: livePeer?.displayName?.takeIf { it.isNotBlank() }
                                ?: call.peerName(currentUserId).ifBlank { "MJ Contact" }
                            val displayAvatar = livePeer?.avatarData?.ifBlank {
                                savedContact?.avatarData?.ifBlank { call.peerAvatar(currentUserId) }.orEmpty()
                            } ?: savedContact?.avatarData?.ifBlank { call.peerAvatar(currentUserId) }
                                ?: call.peerAvatar(currentUserId)

                            val isVideo = call.isVideoCall()
                            val mediaLabel = if (isVideo) "video" else "audio"
                            val isOutgoing = call.isOutgoing(currentUserId)
                            val isMissedOrDeclined = call.status == "missed" || call.status == "declined"
                            val directionIcon = when {
                                isMissedOrDeclined && !isOutgoing -> Icons.AutoMirrored.Filled.CallMissed
                                isOutgoing -> Icons.AutoMirrored.Filled.CallMade
                                else -> Icons.AutoMirrored.Filled.CallReceived
                            }
                            val statusTint = when {
                                isMissedOrDeclined -> MaterialTheme.colorScheme.error
                                isOutgoing -> mjColors.neonCyan
                                else -> mjColors.neonEmerald
                            }
                            val statusText = buildString {
                                append(
                                    when {
                                        call.status == "ringing" -> if (isOutgoing) "Calling ($mediaLabel)…" else "Incoming $mediaLabel…"
                                        call.status == "declined" -> "Declined"
                                        call.status == "missed" -> if (isOutgoing) "No answer" else "Missed $mediaLabel call"
                                        isOutgoing -> "Outgoing $mediaLabel"
                                        else -> "Incoming $mediaLabel"
                                    }
                                )
                                if (call.durationSeconds > 0L) {
                                    append(" • ")
                                    append(formatCallTimer(call.durationSeconds))
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.45f)),
                                shadowElevation = if (mjColors.isDark) 2.dp else 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("call_history_row_${call.id}"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MjAvatar(
                                        name = displayName,
                                        avatarData = displayAvatar,
                                        size = 50.dp,
                                        online = livePeer?.online == true,
                                        showOnlineIndicator = true,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        ) {
                                            Icon(
                                                imageVector = directionIcon,
                                                contentDescription = null,
                                                tint = statusTint,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                text = statusText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isMissedOrDeclined) statusTint else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        val timeLabel = formatChatTimestamp(call.createdAt)
                                        if (timeLabel.isNotBlank()) {
                                            Text(
                                                text = timeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                            )
                                        }
                                    }
                                    if (callSettingsConfig.videoCallingEnabled) {
                                        IconButton(
                                            onClick = {
                                                requestAndCall(
                                                    peerUid = peerUid,
                                                    peerName = displayName,
                                                    peerAvatar = displayAvatar,
                                                    chatId = call.chatId,
                                                    isVideo = true,
                                                )
                                            },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .testTag("video_call_back_${call.id}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Videocam,
                                                contentDescription = "Video call $displayName",
                                                tint = mjColors.neonCyan,
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            requestAndCall(
                                                peerUid = peerUid,
                                                peerName = displayName,
                                                peerAvatar = displayAvatar,
                                                chatId = call.chatId,
                                                isVideo = false,
                                            )
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .testTag("call_back_${call.id}"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Call,
                                            contentDescription = "Audio call $displayName",
                                            tint = mjColors.neonEmerald,
                                        )
                                    }
                                    IconButton(
                                        onClick = { pendingDeleteCall = call },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Remove call log",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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

    pendingDeleteCall?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteCall = null },
            title = { Text("Delete Call Log") },
            text = { Text("Remove this call entry from your history?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCallLog(target.id)
                        pendingDeleteCall = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCall = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun WebRtcVideoSurface(
    videoTrack: VideoTrack,
    eglBaseContext: EglBase.Context,
    mirror: Boolean,
    isOverlay: Boolean,
    modifier: Modifier = Modifier,
) {
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    DisposableEffect(videoTrack, eglBaseContext, rendererRef) {
        val view = rendererRef
        if (view != null) {
            runCatching { videoTrack.addSink(view) }
        }
        onDispose {
            val currentView = rendererRef
            if (currentView != null) {
                runCatching { videoTrack.removeSink(currentView) }
            }
        }
    }

    DisposableEffect(rendererRef) {
        onDispose {
            runCatching { rendererRef?.release() }
            rendererRef = null
        }
    }

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                if (isOverlay) {
                    setZOrderMediaOverlay(true)
                }
                rendererRef = this
            }
        },
        update = { view ->
            view.setMirror(mirror)
        },
        modifier = modifier,
    )
}

@Composable
fun WebRtcCallOverlay(
    currentUserId: String,
    activeCall: CallSession?,
    incomingCall: CallSession?,
    callStage: WebRtcConnectionStage,
    elapsedSeconds: Long,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isCameraEnabled: Boolean,
    isFrontCamera: Boolean,
    eglBaseContext: EglBase.Context?,
    localVideoTrack: VideoTrack?,
    remoteVideoTrack: VideoTrack?,
    peerProfiles: Map<String, PublicProfile>,
    onAcceptIncoming: (CallSession) -> Unit,
    onDeclineIncoming: (CallSession) -> Unit,
    onHangUp: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onShowError: (String) -> Unit,
) {
    val call = activeCall ?: incomingCall ?: return
    val isIncomingPrompt = activeCall == null && incomingCall != null
    val isVideoCall = call.isVideoCall()
    val context = LocalContext.current
    val mjColors = LocalMjColors.current

    val acceptPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val audioOk = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraOk = !isVideoCall ||
            results[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (audioOk && cameraOk) {
            onAcceptIncoming(call)
        } else {
            onShowError(
                context.getString(
                    if (isVideoCall) R.string.permission_video_call_denied else R.string.permission_audio_denied
                )
            )
            onDeclineIncoming(call)
        }
    }

    val peerUid = call.peerUid(currentUserId)
    val peerName = peerProfiles[peerUid]?.displayName?.takeIf { it.isNotBlank() }
        ?: call.peerName(currentUserId).ifBlank { "MJ Contact" }
    val peerAvatar = peerProfiles[peerUid]?.avatarData?.ifBlank { call.peerAvatar(currentUserId) }
        ?: call.peerAvatar(currentUserId)

    val pulseTransition = rememberInfiniteTransition(label = "webrtc_call_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    val hasLiveRemoteVideo = isVideoCall && !isIncomingPrompt && remoteVideoTrack != null && eglBaseContext != null
    val hasLiveLocalVideo = isVideoCall && !isIncomingPrompt && isCameraEnabled && localVideoTrack != null && eglBaseContext != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF031538),
                        Color(0xFF072A6E),
                        Color(0xFF041436),
                    )
                )
            )
            .testTag("webrtc_call_overlay"),
    ) {
        // Full-screen Remote WebRTC Video Renderer when connected on a video call
        if (hasLiveRemoteVideo && remoteVideoTrack != null && eglBaseContext != null) {
            WebRtcVideoSurface(
                videoTrack = remoteVideoTrack,
                eglBaseContext = eglBaseContext,
                mirror = false,
                isOverlay = false,
                modifier = Modifier.fillMaxSize(),
            )
            // Subtle top & bottom gradient scrims for crisp readability over video
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.55f),
                                0.25f to Color.Transparent,
                                0.68f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.72f),
                            )
                        )
                    ),
            )
        }

        // Floating PiP Local Camera Preview in top-right corner
        if (hasLiveLocalVideo && localVideoTrack != null && eglBaseContext != null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, mjColors.neonCyan.copy(alpha = 0.75f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 74.dp, end = 16.dp)
                    .size(width = 112.dp, height = 160.dp)
                    .testTag("webrtc_local_video_pip"),
            ) {
                WebRtcVideoSurface(
                    videoTrack = localVideoTrack,
                    eglBaseContext = eglBaseContext,
                    mirror = isFrontCamera,
                    isOverlay = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top WebRTC Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = mjColors.neonCyan.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, mjColors.neonCyan.copy(alpha = 0.55f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (isVideoCall) Icons.Filled.Videocam else Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = mjColors.neonCyan,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = when {
                            isIncomingPrompt && isVideoCall -> "Incoming Direct WebRTC Video Call"
                            isIncomingPrompt -> "Incoming Direct WebRTC Audio Call"
                            isVideoCall -> "Direct WebRTC Video Call"
                            else -> "Direct WebRTC Audio Call"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Center Caller Avatar + Status + Timer (compact when live remote video is visible)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!hasLiveRemoteVideo) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = 0.28f
                                }
                                .clip(CircleShape)
                                .background(mjColors.neonCyan),
                        )
                        MjAvatar(
                            name = peerName,
                            avatarData = peerAvatar,
                            size = 110.dp,
                            online = true,
                            showOnlineIndicator = false,
                        )
                    }
                }

                Text(
                    text = peerName,
                    style = if (hasLiveRemoteVideo) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )

                val statusLine = when {
                    isIncomingPrompt -> if (isVideoCall) "Incoming video call…" else "Ringing…"
                    callStage == WebRtcConnectionStage.CONNECTED || elapsedSeconds > 0L ->
                        "Connected • ${formatCallTimer(elapsedSeconds)}"
                    else -> callStage.label
                }
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (callStage == WebRtcConnectionStage.CONNECTED || elapsedSeconds > 0L) {
                        mjColors.neonEmerald
                    } else {
                        Color(0xFFB7D8FF)
                    },
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("webrtc_call_status_text"),
                )
            }

            // Bottom Call Controls
            if (isIncomingPrompt) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = { onDeclineIncoming(call) },
                            shape = CircleShape,
                            color = Color(0xFFE53935),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("webrtc_decline_call_button"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.CallEnd,
                                    contentDescription = "Decline call",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Decline", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = {
                                val audioOk = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                                val cameraOk = !isVideoCall || ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (audioOk && cameraOk) {
                                    onAcceptIncoming(call)
                                } else {
                                    val perms = if (isVideoCall) {
                                        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                                    } else {
                                        arrayOf(Manifest.permission.RECORD_AUDIO)
                                    }
                                    acceptPermissionsLauncher.launch(perms)
                                }
                            },
                            shape = CircleShape,
                            color = mjColors.neonEmerald,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("webrtc_accept_call_button"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isVideoCall) Icons.Filled.Videocam else Icons.Filled.Call,
                                    contentDescription = if (isVideoCall) "Accept video call" else "Accept audio call",
                                    tint = Color(0xFF002410),
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Accept", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    if (isVideoCall) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CallRoundControl(
                                icon = if (isCameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                label = if (isCameraEnabled) "Camera On" else "Camera Off",
                                active = !isCameraEnabled,
                                activeBg = Color.White,
                                activeTint = Color(0xFF031538),
                                testTag = "webrtc_camera_toggle_button",
                                onClick = onToggleCamera,
                            )
                            CallRoundControl(
                                icon = Icons.Filled.Cameraswitch,
                                label = "Flip",
                                active = false,
                                activeBg = mjColors.neonCyan,
                                activeTint = Color(0xFF001C2B),
                                testTag = "webrtc_switch_camera_button",
                                onClick = onSwitchCamera,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CallRoundControl(
                            icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            label = if (isMuted) "Muted" else "Mute",
                            active = isMuted,
                            activeBg = Color.White,
                            activeTint = Color(0xFF031538),
                            testTag = "webrtc_mute_button",
                            onClick = onToggleMute,
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                onClick = onHangUp,
                                shape = CircleShape,
                                color = Color(0xFFE53935),
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .size(68.dp)
                                    .testTag("webrtc_hangup_button"),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.CallEnd,
                                        contentDescription = "End call",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("End", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }

                        CallRoundControl(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = if (isSpeakerOn) "Speaker On" else "Speaker",
                            active = isSpeakerOn,
                            activeBg = mjColors.neonCyan,
                            activeTint = Color(0xFF001C2B),
                            testTag = "webrtc_speaker_button",
                            onClick = onToggleSpeaker,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallRoundControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    activeBg: Color,
    activeTint: Color,
    testTag: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (active) activeBg else Color.White.copy(alpha = 0.18f),
            modifier = Modifier
                .size(56.dp)
                .testTag(testTag),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) activeTint else Color.White,
                    modifier = Modifier.size(25.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun formatCallTimer(totalSeconds: Long): String {
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    return "%02d:%02d".format(mins, secs)
}
