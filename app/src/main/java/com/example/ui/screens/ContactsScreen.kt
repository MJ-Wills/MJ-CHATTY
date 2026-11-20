package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ContactVerificationState
import com.example.data.DirectoryEntry
import com.example.data.PublicProfile
import com.example.data.SavedContact
import com.example.data.UiState
import com.example.ui.components.MjAvatar
import com.example.ui.components.VerifiedRegisteredPill
import com.example.ui.theme.LocalMjColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    contactsState: UiState<List<SavedContact>>,
    peerProfiles: Map<String, PublicProfile>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenAddContactSheet: () -> Unit,
    onMessageContact: (SavedContact) -> Unit,
    onCallContact: (SavedContact) -> Unit,
    onDeleteContact: (SavedContact) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mjColors = LocalMjColors.current
    var pendingDelete by remember { mutableStateOf<SavedContact?>(null) }

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
                        Text(
                            text = stringResource(R.string.nav_contacts),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = onOpenAddContactSheet,
                            modifier = Modifier.testTag("contacts_top_add_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = "Add registered contact",
                                tint = mjColors.neonCyan,
                            )
                        }
                    },
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(R.string.search_contacts_placeholder)) },
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
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        .testTag("contacts_search_input"),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAddContactSheet,
                containerColor = mjColors.neonEmerald,
                contentColor = Color(0xFF002410),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("contacts_add_fab"),
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = "Add registered contact",
                )
            }
        },
    ) { innerPadding ->
        when (contactsState) {
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
                        text = contactsState.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            is UiState.Success -> {
                val filtered = remember(contactsState.data, searchQuery) {
                    val q = searchQuery.trim().lowercase()
                    if (q.isEmpty()) {
                        contactsState.data
                    } else {
                        contactsState.data.filter { contact ->
                            contact.savedName.lowercase().contains(q) ||
                                contact.contactUsername.lowercase().contains(q) ||
                                contact.contactPhone.lowercase().contains(q)
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(mjColors.neonEmerald.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VerifiedUser,
                                        contentDescription = null,
                                        tint = mjColors.neonEmerald,
                                        modifier = Modifier.size(30.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No contacts match" else "No saved contacts yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Type any MJ Chatty @username or registered phone number to verify and save contacts automatically.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Button(
                                    onClick = onOpenAddContactSheet,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = mjColors.neonEmerald,
                                        contentColor = Color(0xFF002410),
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("contacts_empty_add_button"),
                                ) {
                                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add & Verify Contact", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp, start = 14.dp, end = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = { it.contactUid }) { contact ->
                            val livePeer = peerProfiles[contact.contactUid]
                            Surface(
                                onClick = { onMessageContact(contact) },
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.45f)),
                                shadowElevation = if (mjColors.isDark) 2.dp else 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("contact_row_${contact.contactUid}"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MjAvatar(
                                        name = contact.savedName,
                                        avatarData = livePeer?.avatarData?.ifBlank { contact.avatarData } ?: contact.avatarData,
                                        size = 52.dp,
                                        online = livePeer?.online == true,
                                        showOnlineIndicator = true,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = contact.savedName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (contact.registered) {
                                                VerifiedRegisteredPill()
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val handleLine = buildString {
                                            if (contact.contactUsername.isNotBlank()) append("@${contact.contactUsername}")
                                            if (contact.contactPhone.isNotBlank()) {
                                                if (isNotEmpty()) append(" • ")
                                                append(contact.contactPhone)
                                            }
                                        }
                                        Text(
                                            text = handleLine.ifBlank { "Registered on MJ Chatty" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    IconButton(
                                        onClick = { onCallContact(contact) },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Call,
                                            contentDescription = "Audio call ${contact.savedName}",
                                            tint = mjColors.neonEmerald,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMessageContact(contact) },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Chat,
                                            contentDescription = "Message ${contact.savedName}",
                                            tint = mjColors.neonCyan,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { pendingDelete = contact },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete ${contact.savedName}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
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

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove Contact") },
            text = { Text("Remove ${target.savedName} from your saved contacts list?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteContact(target)
                        pendingDelete = null
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRegisteredContactSheet(
    lookupInput: String,
    verificationState: ContactVerificationState,
    onLookupInputChange: (String) -> Unit,
    onSaveAndChat: (DirectoryEntry, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mjColors = LocalMjColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var savedNameInput by remember { mutableStateOf("") }
    var userEditedName by remember { mutableStateOf(false) }

    // Auto-fill contact name when automatic live verification finds a registered MJ Chatty user
    LaunchedEffect(verificationState) {
        if (verificationState is ContactVerificationState.Verified && !userEditedName) {
            savedNameInput = verificationState.entry.displayName
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(mjColors.neonEmerald.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = null,
                        tint = mjColors.neonEmerald,
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.add_contact_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Verifies MJ Chatty registration live as you type",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = lookupInput,
                onValueChange = { next ->
                    onLookupInputChange(next)
                },
                label = { Text("Phone Number or @username") },
                placeholder = { Text("e.g. @alex_mj or +15551234567") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.AlternateEmail,
                        contentDescription = null,
                        tint = mjColors.neonCyan,
                    )
                },
                trailingIcon = {
                    if (lookupInput.isNotEmpty()) {
                        IconButton(onClick = { onLookupInputChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
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
                    .testTag("add_contact_lookup_input"),
            )

            // Live Automatic Registration Verification Status Panel
            AnimatedContent(
                targetState = verificationState,
                label = "contact_verification_status",
            ) { state ->
                when (state) {
                    is ContactVerificationState.Idle -> {
                        Text(
                            text = "Start typing a registered @username or phone number to verify automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is ContactVerificationState.Checking -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = mjColors.neonCyan,
                                )
                                Text(
                                    text = stringResource(R.string.contact_checking_badge),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    is ContactVerificationState.Verified -> {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = mjColors.neonEmerald.copy(alpha = 0.13f),
                            border = BorderStroke(1.dp, mjColors.neonEmerald.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contact_verified_card"),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                MjAvatar(
                                    name = state.entry.displayName,
                                    avatarData = state.entry.avatarData,
                                    size = 46.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Verified",
                                            tint = mjColors.neonEmerald,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = stringResource(R.string.contact_verified_badge),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = mjColors.neonEmerald,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Text(
                                        text = state.entry.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "@${state.entry.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    is ContactVerificationState.NotRegistered -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = mjColors.neonAmber.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, mjColors.neonAmber.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = mjColors.neonAmber,
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.contact_not_found_badge),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = mjColors.neonAmber,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "No MJ Chatty account matches \"${state.query}\". Check the @username or phone number.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    is ContactVerificationState.InvalidFormat -> {
                        Text(
                            text = state.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = savedNameInput,
                onValueChange = {
                    savedNameInput = it.take(60)
                    userEditedName = true
                },
                label = { Text("Save Contact Name") },
                placeholder = { Text("How this contact appears in your list") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Person,
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
                    .testTag("add_contact_name_input"),
            )

            val verified = verificationState as? ContactVerificationState.Verified
            Button(
                onClick = {
                    if (verified != null) {
                        onSaveAndChat(verified.entry, savedNameInput, lookupInput)
                    }
                },
                enabled = verified != null,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = mjColors.neonCyan,
                    contentColor = Color(0xFF001C2B),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("add_contact_save_button"),
            ) {
                Text(
                    text = if (verified != null) "Save Contact & Start Chat" else "Verify a Registered Contact First",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
