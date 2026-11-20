package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ChatThread
import com.example.data.PublicProfile
import com.example.data.SavedContact
import com.example.ui.screens.resolveChatTitle
import com.example.ui.theme.LocalMjColors

@Composable
fun ForwardTargetDialog(
    title: String,
    previewText: String,
    currentUserId: String,
    chats: List<ChatThread>,
    contacts: List<SavedContact>,
    peerProfiles: Map<String, PublicProfile>,
    excludeChatId: String? = null,
    onSelectChat: (ChatThread, String) -> Unit,
    onSelectContact: (SavedContact) -> Unit,
    onDismiss: () -> Unit,
) {
    val mjColors = LocalMjColors.current
    var query by remember { mutableStateOf("") }
    val contactsByUid = remember(contacts) { contacts.associateBy { it.contactUid } }

    val availableChats = remember(chats, currentUserId, excludeChatId, query, contactsByUid, peerProfiles) {
        val q = query.trim().lowercase()
        chats.filter { chat ->
            !chat.isSelfChat(currentUserId) &&
                !chat.isHiddenBy(currentUserId) &&
                chat.id != excludeChatId
        }.filter { chat ->
            if (q.isEmpty()) true
            else resolveChatTitle(chat, currentUserId, contactsByUid, peerProfiles).lowercase().contains(q)
        }
    }

    val existingPeerUids = remember(availableChats, currentUserId) {
        availableChats.map { it.peerUid(currentUserId) }.toSet()
    }

    val remainingContacts = remember(contacts, existingPeerUids, query) {
        val q = query.trim().lowercase()
        contacts.filter { it.contactUid !in existingPeerUids }
            .filter { contact ->
                q.isEmpty() ||
                    contact.savedName.lowercase().contains(q) ||
                    contact.contactUsername.lowercase().contains(q)
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Forward,
                    contentDescription = null,
                    tint = mjColors.neonCyan,
                )
                Text(text = title, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (previewText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search chats or contacts…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = mjColors.neonCyan,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = mjColors.neonCyan,
                        unfocusedBorderColor = mjColors.subtleBorder,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("forward_target_search"),
                )

                if (availableChats.isEmpty() && remainingContacts.isEmpty()) {
                    Text(
                        text = "No other chats or contacts available to forward to yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(availableChats, key = { "fwd_chat_${it.id}" }) { chat ->
                            val peerUid = chat.peerUid(currentUserId)
                            val label = resolveChatTitle(chat, currentUserId, contactsByUid, peerProfiles)
                            val avatar = peerProfiles[peerUid]?.avatarData?.ifBlank {
                                contactsByUid[peerUid]?.avatarData.orEmpty()
                            } ?: contactsByUid[peerUid]?.avatarData.orEmpty()
                            val online = peerProfiles[peerUid]?.online == true

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onSelectChat(chat, label)
                                        onDismiss()
                                    }
                                    .testTag("forward_target_chat_${chat.id}"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MjAvatar(
                                        name = label,
                                        avatarData = avatar,
                                        size = 40.dp,
                                        online = online,
                                        showOnlineIndicator = true,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "Active chat",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Forward,
                                        contentDescription = "Forward to $label",
                                        tint = mjColors.neonCyan,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        items(remainingContacts, key = { "fwd_contact_${it.contactUid}" }) { contact ->
                            val livePeer = peerProfiles[contact.contactUid]
                            val avatar = livePeer?.avatarData?.ifBlank { contact.avatarData } ?: contact.avatarData
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onSelectContact(contact)
                                        onDismiss()
                                    }
                                    .testTag("forward_target_contact_${contact.contactUid}"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MjAvatar(
                                        name = contact.savedName,
                                        avatarData = avatar,
                                        size = 40.dp,
                                        online = livePeer?.online == true,
                                        showOnlineIndicator = true,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.savedName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (contact.contactUsername.isNotBlank()) {
                                                "@${contact.contactUsername}"
                                            } else {
                                                "Contact"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Forward,
                                        contentDescription = "Forward to ${contact.savedName}",
                                        tint = mjColors.neonCyan,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
