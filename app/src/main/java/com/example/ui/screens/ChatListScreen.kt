package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ChatLockConfig
import com.example.data.ChatThread
import com.example.data.PublicProfile
import com.example.data.SavedContact
import com.example.data.TypingStatusConfig
import com.example.data.UiState
import com.example.ui.components.ConfiguredTypingIndicator
import com.example.ui.components.ForwardTargetDialog
import com.example.ui.components.MessageStatusTicks
import com.example.ui.components.MjAvatar
import com.example.ui.components.formatChatTimestamp
import com.example.ui.theme.LocalMjColors
import com.google.firebase.Timestamp

private enum class ChatFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    ONLINE("Online"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    currentUserId: String,
    chatsState: UiState<List<ChatThread>>,
    contacts: List<SavedContact>,
    peerProfiles: Map<String, PublicProfile>,
    typingStatusConfig: TypingStatusConfig,
    chatLockConfig: ChatLockConfig,
    videoCallingEnabled: Boolean,
    mutedChatIds: Set<String>,
    searchQuery: String,
    selectedChatId: String?,
    onSearchQueryChange: (String) -> Unit,
    onChatSelected: (ChatThread) -> Unit,
    onStartChatWithContact: (SavedContact) -> Unit,
    onAddContactClick: () -> Unit,
    onTogglePinChat: (ChatThread) -> Unit,
    onToggleLockChat: (ChatThread) -> Unit,
    onToggleMuteChat: (ChatThread) -> Unit,
    onRequestOpenLockedVault: (bypassPrompt: Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenCallSettings: () -> Unit,
    onRenameChat: (ChatThread, String) -> Unit,
    onForwardChatToChat: (ChatThread, ChatThread, String) -> Unit,
    onForwardChatToContact: (ChatThread, SavedContact) -> Unit,
    onDeleteChat: (ChatThread) -> Unit,
    onCallPeer: (peerUid: String, peerName: String, peerAvatar: String, chatId: String) -> Unit,
    onVideoCallPeer: (peerUid: String, peerName: String, peerAvatar: String, chatId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mjColors = LocalMjColors.current
    var searchExpanded by remember { mutableStateOf(false) }
    var showTopMoreMenu by remember { mutableStateOf(false) }
    var selectedFilter by rememberSaveable { mutableStateOf(ChatFilter.ALL) }

    // Chat option modals (Pin / Lock / Mute / Edit / Forward / Delete)
    var actionTargetChat by remember { mutableStateOf<ChatThread?>(null) }
    var renameTargetChat by remember { mutableStateOf<ChatThread?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var forwardSourceChat by remember { mutableStateOf<ChatThread?>(null) }
    var confirmDeleteChat by remember { mutableStateOf<ChatThread?>(null) }

    val contactsByUid = remember(contacts) { contacts.associateBy { it.contactUid } }
    val allNonHiddenChats = remember(chatsState, currentUserId) {
        (chatsState as? UiState.Success)?.data.orEmpty()
            .filterNot { it.isSelfChat(currentUserId) || it.isHiddenBy(currentUserId) }
    }
    val lockedChats = remember(allNonHiddenChats, currentUserId) {
        allNonHiddenChats.filter { it.isLockedBy(currentUserId) }
    }
    val visibleChats = remember(allNonHiddenChats, currentUserId) {
        allNonHiddenChats.filterNot { it.isLockedBy(currentUserId) }
    }
    val secretCodeMatchedInSearch = remember(searchQuery, chatLockConfig) {
        chatLockConfig.hasPasscode && chatLockConfig.matches(searchQuery)
    }
    val unreadChatsCount = remember(visibleChats, currentUserId) {
        visibleChats.count { it.unreadByUid == currentUserId && it.unreadCount > 0L }
    }

    val activeChatPeerUids = remember(allNonHiddenChats, currentUserId) {
        allNonHiddenChats.map { it.peerUid(currentUserId) }.toSet()
    }

    val onlineChatsCount = remember(visibleChats, peerProfiles, currentUserId) {
        visibleChats.count { chat ->
            val peerUid = chat.peerUid(currentUserId)
            peerProfiles[peerUid]?.online == true
        }
    }

    val onlineContactsWithoutChat = remember(contacts, activeChatPeerUids, peerProfiles, searchQuery) {
        val q = searchQuery.trim().lowercase()
        contacts.filter { contact ->
            contact.contactUid !in activeChatPeerUids &&
                peerProfiles[contact.contactUid]?.online == true &&
                (q.isEmpty() ||
                    contact.savedName.lowercase().contains(q) ||
                    contact.contactUsername.lowercase().contains(q))
        }
    }

    val totalOnlineCount = onlineChatsCount + onlineContactsWithoutChat.size

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
                            val headerContext = LocalContext.current
                            AsyncImage(
                                model = remember(headerContext) {
                                    ImageRequest.Builder(headerContext)
                                        .data(R.drawable.mj_chatty_logo_brand)
                                        .size(128)
                                        .crossfade(false)
                                        .build()
                                },
                                contentDescription = "MJ Chatty logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                searchExpanded = !searchExpanded
                                if (!searchExpanded) onSearchQueryChange("")
                            },
                            modifier = Modifier.testTag("chats_search_toggle"),
                        ) {
                            Icon(
                                imageVector = if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = if (searchExpanded) "Close search" else "Search chats",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showTopMoreMenu = true },
                                modifier = Modifier.testTag("chats_top_more_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More chat options",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            DropdownMenu(
                                expanded = showTopMoreMenu,
                                onDismissRequest = { showTopMoreMenu = false },
                                modifier = Modifier.testTag("chats_top_more_dropdown"),
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Chat / Add Contact") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = mjColors.neonCyan)
                                    },
                                    onClick = {
                                        showTopMoreMenu = false
                                        onAddContactClick()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_lock_locked_folder)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = mjColors.neonEmerald)
                                    },
                                    onClick = {
                                        showTopMoreMenu = false
                                        onRequestOpenLockedVault(false)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Notification Settings") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = mjColors.neonBlue)
                                    },
                                    onClick = {
                                        showTopMoreMenu = false
                                        onOpenNotificationSettings()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Call Settings") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Tune, contentDescription = null, tint = mjColors.neonAmber)
                                    },
                                    onClick = {
                                        showTopMoreMenu = false
                                        onOpenCallSettings()
                                    },
                                )
                            }
                        }
                    },
                )

                AnimatedVisibility(
                    visible = searchExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text(stringResource(R.string.search_chats_placeholder)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = mjColors.neonCyan,
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = mjColors.neonCyan,
                            unfocusedBorderColor = mjColors.subtleBorder,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                            .testTag("chats_search_input"),
                    )
                }

                // Modern Chat List Filter Chips (All / Unread / Online)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatFilter.entries.forEach { filter ->
                        val isActive = selectedFilter == filter
                        val chipLabel = when {
                            filter == ChatFilter.UNREAD && unreadChatsCount > 0 ->
                                "${filter.label} ($unreadChatsCount)"
                            filter == ChatFilter.ONLINE && totalOnlineCount > 0 ->
                                "${filter.label} ($totalOnlineCount)"
                            else -> filter.label
                        }
                        Surface(
                            onClick = { selectedFilter = filter },
                            shape = RoundedCornerShape(50),
                            color = if (isActive) {
                                if (filter == ChatFilter.ONLINE) {
                                    mjColors.neonEmerald.copy(alpha = if (mjColors.isDark) 0.22f else 0.16f)
                                } else {
                                    mjColors.neonCyan.copy(alpha = if (mjColors.isDark) 0.22f else 0.16f)
                                }
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isActive) {
                                    if (filter == ChatFilter.ONLINE) {
                                        mjColors.neonEmerald.copy(alpha = 0.75f)
                                    } else {
                                        mjColors.neonCyan.copy(alpha = 0.7f)
                                    }
                                } else {
                                    Color.Transparent
                                },
                            ),
                            modifier = Modifier.testTag("chat_filter_${filter.name.lowercase()}"),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            ) {
                                if (filter == ChatFilter.ONLINE) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (totalOnlineCount > 0 || isActive) {
                                                    mjColors.neonEmerald
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                }
                                            ),
                                    )
                                }
                                Text(
                                    text = chipLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isActive) {
                                        if (filter == ChatFilter.ONLINE) mjColors.neonEmerald else mjColors.neonCyan
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = mjColors.subtleBorder.copy(alpha = 0.35f))
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddContactClick,
                containerColor = mjColors.neonCyan,
                contentColor = Color(0xFF001C2B),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("chats_new_fab"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "New chat or add contact",
                )
            }
        },
    ) { innerPadding ->
        when (chatsState) {
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
                        text = chatsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            is UiState.Success -> {
                val q = searchQuery.trim().lowercase()

                val filteredPeerChats = remember(visibleChats, q, selectedFilter, contactsByUid, peerProfiles, currentUserId) {
                    visibleChats.filter { chat ->
                        val peerUid = chat.peerUid(currentUserId)
                        val peerName = resolveChatTitle(chat, currentUserId, contactsByUid, peerProfiles)
                        val peerHandle = peerProfiles[peerUid]?.username
                            ?: contactsByUid[peerUid]?.contactUsername.orEmpty()
                        val matchesQuery = q.isEmpty() ||
                            peerName.lowercase().contains(q) ||
                            peerHandle.lowercase().contains(q) ||
                            chat.lastMessagePreview.lowercase().contains(q)
                        val matchesFilter = when (selectedFilter) {
                            ChatFilter.ALL -> true
                            ChatFilter.UNREAD -> chat.unreadByUid == currentUserId && chat.unreadCount > 0L
                            ChatFilter.ONLINE -> peerProfiles[peerUid]?.online == true
                        }
                        matchesQuery && matchesFilter
                    }.sortedWith(
                        compareByDescending<ChatThread> { it.isPinnedBy(currentUserId) }
                            .thenByDescending { (it.lastMessageAt ?: it.updatedAt ?: Timestamp(0, 0)).seconds }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp, start = 14.dp, end = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Quick Connect horizontal strip when contacts exist
                    if (contacts.isNotEmpty() && searchQuery.isBlank() && selectedFilter == ChatFilter.ALL) {
                        item(key = "active_contacts_rail") {
                            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                                Text(
                                    text = "Quick Connect",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                ) {
                                    items(contacts, key = { it.contactUid }) { contact ->
                                        val livePeer = peerProfiles[contact.contactUid]
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .width(64.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { onStartChatWithContact(contact) }
                                                .padding(vertical = 4.dp),
                                        ) {
                                            MjAvatar(
                                                name = contact.savedName,
                                                avatarData = livePeer?.avatarData?.ifBlank { contact.avatarData }
                                                    ?: contact.avatarData,
                                                size = 50.dp,
                                                online = livePeer?.online == true,
                                                showOnlineIndicator = true,
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = contact.savedName.substringBefore(" "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Secret Code Match Banner (WhatsApp-style hidden locked chats unlock via search bar)
                    if (secretCodeMatchedInSearch) {
                        item(key = "secret_code_vault_entry") {
                            Surface(
                                onClick = {
                                    onSearchQueryChange("")
                                    searchExpanded = false
                                    onRequestOpenLockedVault(true)
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = mjColors.neonEmerald.copy(alpha = if (mjColors.isDark) 0.18f else 0.14f),
                                border = BorderStroke(1.2.dp, mjColors.neonEmerald.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("secret_code_locked_chats_row"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(mjColors.neonEmerald.copy(alpha = 0.22f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.LockOpen,
                                            contentDescription = null,
                                            tint = mjColors.neonEmerald,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.chat_lock_locked_folder),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "Secret code recognized • Tap to open (${lockedChats.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = mjColors.neonEmerald,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // WhatsApp-style Locked Chats folder row (hidden when hideLockedChats == true)
                    if (!chatLockConfig.hideLockedChats && lockedChats.isNotEmpty() && q.isEmpty() && selectedFilter == ChatFilter.ALL) {
                        item(key = "locked_chats_folder_row") {
                            Surface(
                                onClick = { onRequestOpenLockedVault(false) },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, mjColors.neonEmerald.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("locked_chats_folder_row"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(mjColors.neonEmerald.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = mjColors.neonEmerald,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.chat_lock_locked_folder),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = mjColors.neonEmerald.copy(alpha = 0.2f),
                                    ) {
                                        Text(
                                            text = lockedChats.size.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = mjColors.neonEmerald,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Active chats (pinned chats automatically sorted to top)
                    items(filteredPeerChats, key = { it.id }) { chat ->
                        val peerUid = chat.peerUid(currentUserId)
                        val livePeer = peerProfiles[peerUid]
                        val savedContact = contactsByUid[peerUid]
                        val title = resolveChatTitle(chat, currentUserId, contactsByUid, peerProfiles)
                        val avatar = livePeer?.avatarData?.ifBlank { savedContact?.avatarData.orEmpty() }
                            ?: savedContact?.avatarData.orEmpty()
                        val isPeerTyping = typingStatusConfig.showPeerTypingIndicator &&
                            chat.typingUid.isNotBlank() &&
                            chat.typingUid != currentUserId
                        val unreadCount = if (chat.unreadByUid == currentUserId) chat.unreadCount else 0L
                        val isSelected = chat.id == selectedChatId
                        val isPinned = chat.isPinnedBy(currentUserId)
                        val isMuted = chat.id in mutedChatIds

                        ChatThreadRowCard(
                            chat = chat,
                            currentUserId = currentUserId,
                            title = title,
                            avatarData = avatar,
                            online = livePeer?.online == true,
                            isPinned = isPinned,
                            isMuted = isMuted,
                            isPeerTyping = isPeerTyping,
                            typingStatusConfig = typingStatusConfig,
                            unreadCount = unreadCount,
                            isSelected = isSelected,
                            onClick = { onChatSelected(chat) },
                            onLongPress = { actionTargetChat = chat },
                        )
                    }

                    // In the Online tab, also surface online saved contacts who don't have an active chat thread yet
                    if (selectedFilter == ChatFilter.ONLINE && onlineContactsWithoutChat.isNotEmpty()) {
                        item(key = "online_contacts_header") {
                            Text(
                                text = "Online Contacts Ready to Chat",
                                style = MaterialTheme.typography.labelMedium,
                                color = mjColors.neonEmerald,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
                            )
                        }
                        items(onlineContactsWithoutChat, key = { "online_contact_${it.contactUid}" }) { contact ->
                            val livePeer = peerProfiles[contact.contactUid]
                            val avatar = livePeer?.avatarData?.ifBlank { contact.avatarData } ?: contact.avatarData
                            Surface(
                                onClick = { onStartChatWithContact(contact) },
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, mjColors.neonEmerald.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("online_contact_row_${contact.contactUid}"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MjAvatar(
                                        name = contact.savedName,
                                        avatarData = avatar,
                                        size = 50.dp,
                                        online = true,
                                        showOnlineIndicator = true,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.savedName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(mjColors.neonEmerald),
                                            )
                                            Text(
                                                text = "Online now • Tap to message",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = mjColors.neonEmerald.copy(alpha = 0.85f),
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            onCallPeer(contact.contactUid, contact.savedName, avatar, "")
                                        },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Call,
                                            contentDescription = "Audio call ${contact.savedName}",
                                            tint = mjColors.neonEmerald,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Inline footer hint when no chats (or online contacts) match
                    val isOnlineEmpty = selectedFilter == ChatFilter.ONLINE &&
                        filteredPeerChats.isEmpty() &&
                        onlineContactsWithoutChat.isEmpty()
                    if ((selectedFilter != ChatFilter.ONLINE && filteredPeerChats.isEmpty()) || isOnlineEmpty) {
                        item(key = "chat_list_footer_prompt") {
                            ChatListPromptCard(
                                filter = selectedFilter,
                                isSearching = q.isNotBlank(),
                                onAddContactClick = onAddContactClick,
                            )
                        }
                    }
                }
            }
        }
    }

    // Chat Options Dialog: Pin / Edit Name / Forward / Call / Delete
    actionTargetChat?.let { target ->
        val peerUid = target.peerUid(currentUserId)
        val livePeer = peerProfiles[peerUid]
        val savedContact = contactsByUid[peerUid]
        val title = resolveChatTitle(target, currentUserId, contactsByUid, peerProfiles)
        val avatar = livePeer?.avatarData?.ifBlank { savedContact?.avatarData.orEmpty() }
            ?: savedContact?.avatarData.orEmpty()
        val isPinned = target.isPinnedBy(currentUserId)

        AlertDialog(
            onDismissRequest = { actionTargetChat = null },
            title = {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChatOptionRow(
                        icon = Icons.Filled.PushPin,
                        label = if (isPinned) "Unpin Chat" else "Pin Chat to Top",
                        tint = mjColors.neonCyan,
                        testTag = "chat_option_pin",
                        onClick = {
                            onTogglePinChat(target)
                            actionTargetChat = null
                        },
                    )
                    ChatOptionRow(
                        icon = if (target.isLockedBy(currentUserId)) Icons.Filled.LockOpen else Icons.Filled.Lock,
                        label = if (target.isLockedBy(currentUserId)) "Unlock Chat" else "Lock Chat",
                        tint = mjColors.neonEmerald,
                        testTag = "chat_option_lock",
                        onClick = {
                            onToggleLockChat(target)
                            actionTargetChat = null
                        },
                    )
                    val isMutedTarget = target.id in mutedChatIds
                    ChatOptionRow(
                        icon = if (isMutedTarget) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                        label = if (isMutedTarget) "Unmute Notifications" else "Mute Notifications",
                        tint = if (isMutedTarget) mjColors.neonEmerald else mjColors.neonAmber,
                        testTag = "chat_option_mute",
                        onClick = {
                            onToggleMuteChat(target)
                            actionTargetChat = null
                        },
                    )
                    ChatOptionRow(
                        icon = Icons.Filled.Edit,
                        label = "Edit Chat Name",
                        tint = mjColors.neonAmber,
                        testTag = "chat_option_edit",
                        onClick = {
                            renameDraft = target.title.ifBlank { title }
                            renameTargetChat = target
                            actionTargetChat = null
                        },
                    )
                    ChatOptionRow(
                        icon = Icons.AutoMirrored.Filled.Forward,
                        label = "Forward Latest Message",
                        tint = mjColors.neonBlue,
                        testTag = "chat_option_forward",
                        onClick = {
                            forwardSourceChat = target
                            actionTargetChat = null
                        },
                    )
                    if (!target.isSelfChat(currentUserId)) {
                        ChatOptionRow(
                            icon = Icons.Filled.Call,
                            label = "Start WebRTC Audio Call",
                            tint = mjColors.neonEmerald,
                            testTag = "chat_option_call",
                            onClick = {
                                onCallPeer(peerUid, title, avatar, target.id)
                                actionTargetChat = null
                            },
                        )
                        if (videoCallingEnabled) {
                            ChatOptionRow(
                                icon = Icons.Filled.Videocam,
                                label = "Start WebRTC Video Call",
                                tint = mjColors.neonCyan,
                                testTag = "chat_option_video_call",
                                onClick = {
                                    onVideoCallPeer(peerUid, title, avatar, target.id)
                                    actionTargetChat = null
                                },
                            )
                        }
                    }
                    ChatOptionRow(
                        icon = Icons.Filled.Delete,
                        label = "Delete Chat",
                        tint = MaterialTheme.colorScheme.error,
                        testTag = "chat_option_delete",
                        onClick = {
                            confirmDeleteChat = target
                            actionTargetChat = null
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTargetChat = null }) {
                    Text("Close")
                }
            },
        )
    }

    // Rename / Edit Chat Name Dialog
    renameTargetChat?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTargetChat = null },
            title = { Text("Edit Chat Name") },
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
                        .testTag("rename_chat_input"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameChat(target, renameDraft)
                        renameTargetChat = null
                    },
                    modifier = Modifier.testTag("rename_chat_confirm"),
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetChat = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Forward Chat's Latest Message Picker
    forwardSourceChat?.let { source ->
        ForwardTargetDialog(
            title = "Forward Chat Message",
            previewText = source.lastMessagePreview,
            currentUserId = currentUserId,
            chats = visibleChats,
            contacts = contacts,
            peerProfiles = peerProfiles,
            excludeChatId = source.id,
            onSelectChat = { targetChat, label ->
                onForwardChatToChat(source, targetChat, label)
            },
            onSelectContact = { contact ->
                onForwardChatToContact(source, contact)
            },
            onDismiss = { forwardSourceChat = null },
        )
    }

    // Confirm Delete Chat Dialog
    confirmDeleteChat?.let { target ->
        val title = resolveChatTitle(target, currentUserId, contactsByUid, peerProfiles)
        AlertDialog(
            onDismissRequest = { confirmDeleteChat = null },
            title = { Text("Delete Chat") },
            text = { Text("Remove \"$title\" from your chat list? New messages will restore it later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteChat(target)
                        confirmDeleteChat = null
                    },
                    modifier = Modifier.testTag("confirm_delete_chat_button"),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteChat = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ChatOptionRow(
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
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

fun resolveChatTitle(
    chat: ChatThread,
    currentUserId: String,
    contactsByUid: Map<String, SavedContact>,
    peerProfiles: Map<String, PublicProfile>,
): String {
    val peerUid = chat.peerUid(currentUserId)
    val customTitle = chat.title.takeIf { it.isNotBlank() && !it.equals("Saved Messages", ignoreCase = true) }
    return customTitle
        ?: contactsByUid[peerUid]?.savedName
        ?: peerProfiles[peerUid]?.displayName?.takeIf { it.isNotBlank() }
        ?: "MJ Chatty User"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatThreadRowCard(
    chat: ChatThread,
    currentUserId: String,
    title: String,
    avatarData: String,
    online: Boolean,
    isPinned: Boolean,
    isMuted: Boolean,
    isPeerTyping: Boolean,
    typingStatusConfig: TypingStatusConfig,
    unreadCount: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val mjColors = LocalMjColors.current

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isSelected) {
            mjColors.neonCyan.copy(alpha = if (mjColors.isDark) 0.16f else 0.12f)
        } else if (isPinned) {
            mjColors.neonBlue.copy(alpha = if (mjColors.isDark) 0.11f else 0.06f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isSelected || isPinned) 1.25.dp else 1.dp,
            color = when {
                isSelected -> mjColors.neonCyan.copy(alpha = 0.65f)
                isPinned -> mjColors.neonCyan.copy(alpha = 0.35f)
                else -> mjColors.subtleBorder.copy(alpha = 0.45f)
            },
        ),
        shadowElevation = if (mjColors.isDark) 2.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .testTag("chat_row_${chat.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MjAvatar(
                name = title,
                avatarData = avatarData,
                size = 54.dp,
                online = online,
                showOnlineIndicator = true,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "Pinned chat",
                                tint = mjColors.neonCyan,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        if (isMuted) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsOff,
                                contentDescription = "Muted notifications",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    val timeText = formatChatTimestamp(chat.lastMessageAt ?: chat.updatedAt)
                    if (timeText.isNotEmpty()) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unreadCount > 0) mjColors.neonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }

                if (online) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .testTag("online_badge_${chat.id}"),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(mjColors.neonEmerald.copy(alpha = 0.55f)),
                        )
                        Text(
                            text = stringResource(R.string.status_online),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = mjColors.neonEmerald.copy(alpha = 0.65f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (isPeerTyping) {
                        ConfiguredTypingIndicator(
                            style = typingStatusConfig.displayStyle,
                            label = stringResource(R.string.status_typing),
                            accentColor = mjColors.neonEmerald,
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (chat.lastSenderId == currentUserId && chat.lastMessagePreview.isNotBlank()) {
                                MessageStatusTicks(
                                    status = chat.lastMessageStatus,
                                    onOutgoingBubble = false,
                                )
                            }
                            Text(
                                text = chat.lastMessagePreview.ifBlank { "Tap to start chatting" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (unreadCount > 0) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(mjColors.neonEmerald)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF002410),
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = onLongPress,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat_more_${chat.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Chat options for $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatListPromptCard(
    filter: ChatFilter,
    isSearching: Boolean,
    onAddContactClick: () -> Unit,
) {
    val mjColors = LocalMjColors.current
    Surface(
        onClick = if (!isSearching && filter == ChatFilter.ALL) onAddContactClick else ({}),
        enabled = !isSearching && filter == ChatFilter.ALL,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .testTag("chat_list_add_contact_prompt"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(mjColors.neonCyan.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSearching || filter != ChatFilter.ALL) {
                        Icons.Filled.ChatBubbleOutline
                    } else {
                        Icons.Filled.PersonAdd
                    },
                    contentDescription = null,
                    tint = mjColors.neonCyan,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isSearching -> "No matching conversations"
                        filter == ChatFilter.UNREAD -> "All caught up"
                        filter == ChatFilter.ONLINE -> "No contacts online right now"
                        else -> "Start a new conversation"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        isSearching -> "Try searching another name, @username, or message."
                        filter == ChatFilter.UNREAD -> "You have no unread messages."
                        filter == ChatFilter.ONLINE -> "Online chats & contacts will appear here automatically."
                        else -> "Tap to verify and add a registered MJ Chatty contact."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
