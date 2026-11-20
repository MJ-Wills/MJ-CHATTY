package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.PcLinkSession
import com.example.data.UiState
import com.example.ui.theme.LocalMjColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkToPcScreen(
    pcLinksState: UiState<List<PcLinkSession>>,
    userDisplayName: String,
    userHandle: String,
    onEnsureDefaultLink: (baseUrl: String) -> Unit,
    onCreateLink: (baseUrl: String, deviceLabel: String) -> Unit,
    onUpdateLinkStatus: (linkId: String, status: String) -> Unit,
    onRenameLink: (linkId: String, newLabel: String) -> Unit,
    onDeleteLink: (linkId: String) -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val mjColors = LocalMjColors.current
    val baseUrl = stringResource(R.string.pc_link_base_url)

    var autoInitialized by remember { mutableStateOf(false) }
    var copiedSessionId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingSession by remember { mutableStateOf<PcLinkSession?>(null) }

    LaunchedEffect(pcLinksState) {
        if (!autoInitialized && pcLinksState is UiState.Success) {
            autoInitialized = true
            onEnsureDefaultLink(baseUrl)
        }
    }

    LaunchedEffect(copiedSessionId) {
        if (copiedSessionId != null) {
            delay(2400L)
            copiedSessionId = null
        }
    }

    val sessions = (pcLinksState as? UiState.Success)?.data.orEmpty()
    val primarySession = remember(sessions) {
        sessions.firstOrNull { it.status == "pending" }
            ?: sessions.firstOrNull { it.status == "linked" }
            ?: sessions.firstOrNull()
    }

    fun copyUrlToClipboard(session: PcLinkSession) {
        clipboard.setText(AnnotatedString(session.linkUrl))
        copiedSessionId = session.id
        onNotify("Linking URL copied — paste it into your PC web browser")
    }

    fun shareUrlToPc(session: PcLinkSession) {
        val shareText = buildString {
            append("Open this MJ Chatty Link to PC URL in your web browser:\n")
            append(session.linkUrl)
            if (session.pairingCode.isNotBlank()) {
                append("\nVerification code: ${session.pairingCode}")
            }
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Link MJ Chatty to PC Browser")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Send link to PC browser"))
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(mjColors.neonBlue, mjColors.neonCyan),
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Devices,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.link_pc_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            if (userHandle.isNotBlank()) {
                                Text(
                                    text = "$userDisplayName • @$userHandle",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mjColors.neonCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("link_pc_top_add_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddLink,
                            contentDescription = "Generate new PC link",
                            tint = mjColors.neonCyan,
                        )
                    }
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. HERO BANNER + PRIMARY COPYABLE BROWSER LINK
                item(key = "pc_link_hero") {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, mjColors.neonCyan.copy(alpha = 0.35f)),
                        shadowElevation = if (mjColors.isDark) 6.dp else 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            mjColors.neonBlue.copy(alpha = if (mjColors.isDark) 0.20f else 0.08f),
                                            Color.Transparent,
                                        )
                                    )
                                )
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            // Phone <-> PC Neon Visual Bridge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(mjColors.neonCyan.copy(alpha = 0.16f))
                                        .border(1.dp, mjColors.neonCyan.copy(alpha = 0.45f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PhoneAndroid,
                                        contentDescription = "This Android app",
                                        tint = mjColors.neonCyan,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }

                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .height(2.dp)
                                            .background(mjColors.neonCyan.copy(alpha = 0.65f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(mjColors.neonBlue.copy(alpha = 0.22f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Link,
                                            contentDescription = null,
                                            tint = mjColors.neonCyan,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .height(2.dp)
                                            .background(mjColors.neonEmerald.copy(alpha = 0.65f))
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(mjColors.neonEmerald.copy(alpha = 0.16f))
                                        .border(1.dp, mjColors.neonEmerald.copy(alpha = 0.45f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Computer,
                                        contentDescription = "PC web browser",
                                        tint = mjColors.neonEmerald,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "Connect MJ Chatty on Your PC",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.link_pc_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            when (pcLinksState) {
                                is UiState.Loading -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 18.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            color = mjColors.neonCyan,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                                is UiState.Error -> {
                                    Text(
                                        text = pcLinksState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Button(
                                        onClick = { onCreateLink(baseUrl, "PC Web Browser") },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Generate Linking URL")
                                    }
                                }
                                is UiState.Success -> {
                                    if (primarySession == null) {
                                        Button(
                                            onClick = { onCreateLink(baseUrl, "PC Web Browser") },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = mjColors.neonCyan,
                                                contentColor = Color(0xFF001C2B),
                                            ),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("link_pc_generate_first_button"),
                                        ) {
                                            Icon(Icons.Filled.AddLink, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Generate PC Linking URL", fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        val isCopied = copiedSessionId == primarySession.id

                                        // Selectable URL Box
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isCopied) mjColors.neonEmerald else mjColors.subtleBorder.copy(alpha = 0.8f),
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(
                                                        text = "BROWSER LINKING URL",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = mjColors.neonCyan,
                                                        letterSpacing = 0.8.sp,
                                                    )
                                                    PcLinkStatusPill(status = primarySession.status)
                                                }

                                                SelectionContainer {
                                                    Text(
                                                        text = primarySession.linkUrl,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontFamily = FontFamily.Monospace,
                                                            lineHeight = 19.sp,
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.testTag("link_pc_active_url_text"),
                                                    )
                                                }

                                                // Pairing Verification Code Chip
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                                                        .clickable {
                                                            clipboard.setText(AnnotatedString(primarySession.pairingCode))
                                                            onNotify("Verification code ${primarySession.pairingCode} copied")
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Filled.VerifiedUser,
                                                            contentDescription = null,
                                                            tint = mjColors.neonEmerald,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "Pairing Code:",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                    Text(
                                                        text = primarySession.pairingCode,
                                                        style = MaterialTheme.typography.labelLarge.copy(
                                                            fontFamily = FontFamily.Monospace,
                                                            letterSpacing = 1.2.sp,
                                                        ),
                                                        fontWeight = FontWeight.Bold,
                                                        color = mjColors.neonCyan,
                                                        modifier = Modifier.testTag("link_pc_pairing_code_text"),
                                                    )
                                                }
                                            }
                                        }

                                        // Primary Copy Button + Share + New Link
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Button(
                                                onClick = { copyUrlToClipboard(primarySession) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isCopied) mjColors.neonEmerald else mjColors.neonCyan,
                                                    contentColor = Color(0xFF001C2B),
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .weight(1.35f)
                                                    .height(48.dp)
                                                    .testTag("link_pc_copy_button"),
                                            ) {
                                                AnimatedContent(targetState = isCopied, label = "copy_btn") { copied ->
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = if (copied) {
                                                                stringResource(R.string.link_pc_copied)
                                                            } else {
                                                                stringResource(R.string.link_pc_copy_button)
                                                            },
                                                            fontWeight = FontWeight.Bold,
                                                        )
                                                    }
                                                }
                                            }

                                            OutlinedButton(
                                                onClick = { shareUrlToPc(primarySession) },
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(1.dp, mjColors.neonCyan.copy(alpha = 0.5f)),
                                                modifier = Modifier
                                                    .weight(0.95f)
                                                    .height(48.dp)
                                                    .testTag("link_pc_share_button"),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Share,
                                                    contentDescription = null,
                                                    tint = mjColors.neonCyan,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = stringResource(R.string.link_pc_share_button),
                                                    color = mjColors.neonCyan,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            OutlinedButton(
                                                onClick = { showCreateDialog = true },
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("link_pc_new_link_button"),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Refresh,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(17.dp),
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.link_pc_new_link_button))
                                            }

                                            if (primarySession.status == "pending") {
                                                OutlinedButton(
                                                    onClick = { onUpdateLinkStatus(primarySession.id, "linked") },
                                                    shape = RoundedCornerShape(14.dp),
                                                    border = BorderStroke(1.dp, mjColors.neonEmerald.copy(alpha = 0.55f)),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(48.dp)
                                                        .testTag("link_pc_confirm_linked_button"),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.CheckCircle,
                                                        contentDescription = null,
                                                        tint = mjColors.neonEmerald,
                                                        modifier = Modifier.size(17.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Mark Linked",
                                                        color = mjColors.neonEmerald,
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

                // 2. STEP-BY-STEP PC BROWSER GUIDE
                item(key = "pc_link_steps") {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "How to link in your PC browser",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            LinkStepRow(
                                step = "1",
                                text = "Tap Copy Link above (or use Share to send the link to your computer).",
                                accent = mjColors.neonCyan,
                            )
                            LinkStepRow(
                                step = "2",
                                text = "Open Chrome, Edge, Firefox, or Safari on your PC and paste the URL into the address bar.",
                                accent = mjColors.neonBlue,
                            )
                            LinkStepRow(
                                step = "3",
                                text = "Confirm the pairing code matches, then manage or unlink browsers anytime below.",
                                accent = mjColors.neonEmerald,
                            )
                        }
                    }
                }

                // 3. LINKED & GENERATED PC BROWSER SESSIONS
                if (sessions.isNotEmpty()) {
                    item(key = "pc_sessions_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "PC Linking Sessions (${sessions.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Synced to your account",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    items(sessions, key = { it.id }) { session ->
                        PcLinkSessionCard(
                            session = session,
                            isCopied = copiedSessionId == session.id,
                            onCopy = { copyUrlToClipboard(session) },
                            onToggleStatus = {
                                val nextStatus = when (session.status) {
                                    "linked" -> "revoked"
                                    "revoked" -> "pending"
                                    else -> "linked"
                                }
                                onUpdateLinkStatus(session.id, nextStatus)
                            },
                            onRename = { renamingSession = session },
                            onDelete = { onDeleteLink(session.id) },
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    if (showCreateDialog) {
        CreatePcLinkDialog(
            onCreate = { label ->
                showCreateDialog = false
                onCreateLink(baseUrl, label)
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    renamingSession?.let { target ->
        RenamePcLinkDialog(
            currentLabel = target.deviceLabel,
            onSave = { newLabel ->
                renamingSession = null
                onRenameLink(target.id, newLabel)
            },
            onDismiss = { renamingSession = null },
        )
    }
}

@Composable
private fun LinkStepRow(
    step: String,
    text: String,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PcLinkStatusPill(status: String) {
    val mjColors = LocalMjColors.current
    val (label, color) = when (status) {
        "linked" -> "Linked" to mjColors.neonEmerald
        "revoked" -> "Revoked" to MaterialTheme.colorScheme.error
        else -> "Ready to Link" to mjColors.neonCyan
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.16f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PcLinkSessionCard(
    session: PcLinkSession,
    isCopied: Boolean,
    onCopy: () -> Unit,
    onToggleStatus: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val mjColors = LocalMjColors.current
    val formattedDate = remember(session.createdAt) {
        val date = session.createdAt?.toDate()
        if (date != null) {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
        } else {
            "Just now"
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pc_link_session_card_${session.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(mjColors.neonBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Computer,
                        contentDescription = null,
                        tint = mjColors.neonCyan,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = session.deviceLabel.ifBlank { "PC Web Browser" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        PcLinkStatusPill(status = session.status)
                    }
                    Text(
                        text = "Code ${session.pairingCode} • $formattedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = session.linkUrl,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onCopy,
                    enabled = session.status != "revoked",
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "Copy link",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isCopied) "Copied" else "Copy")
                }

                OutlinedButton(
                    onClick = onToggleStatus,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(
                        imageVector = if (session.status == "linked") Icons.Filled.LinkOff else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        when (session.status) {
                            "linked" -> "Unlink"
                            "revoked" -> "Restore"
                            else -> "Mark Linked"
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = onRename) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Rename PC device",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete PC link",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatePcLinkDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mjColors = LocalMjColors.current
    var label by remember { mutableStateOf("PC Web Browser") }
    val presets = listOf("Work Laptop Chrome", "Home PC Edge", "MacBook Browser")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New PC Linking URL", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Name this PC browser session so you can recognize and manage it later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text("PC / Browser Label") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = mjColors.neonCyan,
                        unfocusedBorderColor = mjColors.subtleBorder,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("link_pc_label_input"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presets.forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { label = preset },
                        ) {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(label.trim().ifBlank { "PC Web Browser" }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = mjColors.neonCyan,
                    contentColor = Color(0xFF001C2B),
                ),
            ) {
                Text("Generate Link", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RenamePcLinkDialog(
    currentLabel: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mjColors = LocalMjColors.current
    var label by remember(currentLabel) { mutableStateOf(currentLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename PC Session", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                label = { Text("Device Label") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = mjColors.neonCyan,
                    unfocusedBorderColor = mjColors.subtleBorder,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(label.trim().ifBlank { "PC Web Browser" }) },
                enabled = label.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = mjColors.neonCyan,
                    contentColor = Color(0xFF001C2B),
                ),
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
