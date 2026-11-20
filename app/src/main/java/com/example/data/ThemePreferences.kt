package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.mjChattyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mj_chatty_preferences"
)

enum class TypingDisplayStyle(val key: String, val label: String) {
    DOTS_AND_TEXT("dots_and_text", "Dots & Text"),
    DOTS_ONLY("dots_only", "Dots Only"),
    TEXT_ONLY("text_only", "Text Only");

    companion object {
        fun fromKey(key: String?): TypingDisplayStyle =
            entries.firstOrNull { it.key == key } ?: DOTS_AND_TEXT
    }
}

data class TypingStatusConfig(
    val sendTypingIndicator: Boolean = true,
    val showPeerTypingIndicator: Boolean = true,
    val typingTimeoutSeconds: Int = 3,
    val displayStyle: TypingDisplayStyle = TypingDisplayStyle.DOTS_AND_TEXT,
)

data class ChatLockConfig(
    val passcodeHash: String = "",
    val hideLockedChats: Boolean = false,
) {
    val hasPasscode: Boolean get() = passcodeHash.isNotBlank()

    fun matches(rawCode: String): Boolean {
        val trimmed = rawCode.trim()
        if (trimmed.isEmpty() || passcodeHash.isBlank()) return false
        return hashSecretCode(trimmed) == passcodeHash
    }

    companion object {
        fun hashSecretCode(rawCode: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("mj_chat_lock_v1:${rawCode.trim()}".toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

data class CallSettingsConfig(
    val videoCallingEnabled: Boolean = true,
    val startVideoWithCameraOn: Boolean = true,
    val preferFrontCamera: Boolean = true,
    val defaultSpeakerForVideo: Boolean = true,
    val hdVideoQuality: Boolean = true,
)

data class NotificationSettingsConfig(
    val pushNotificationsEnabled: Boolean = true,
    val incomingCallNotifications: Boolean = true,
    val outgoingCallNotifications: Boolean = true,
    val receivedActivityNotifications: Boolean = true,
    val mutedChatIds: Set<String> = emptySet(),
) {
    fun isChatMuted(chatId: String): Boolean = chatId in mutedChatIds
}

class ThemePreferences(private val context: Context) {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val pendingPhoneKey = stringPreferencesKey("pending_welcome_phone")
    private val sendTypingKey = booleanPreferencesKey("typing_send_indicator")
    private val showPeerTypingKey = booleanPreferencesKey("typing_show_peer_indicator")
    private val typingTimeoutSecondsKey = intPreferencesKey("typing_timeout_seconds")
    private val typingDisplayStyleKey = stringPreferencesKey("typing_display_style")

    // Hidden Chat Lock preferences (WhatsApp-style secret code & vault visibility)
    private val chatLockPasscodeHashKey = stringPreferencesKey("chat_lock_passcode_hash")
    private val chatLockHideFolderKey = booleanPreferencesKey("chat_lock_hide_folder")

    // Call Settings preferences (Audio & Video WebRTC configuration)
    private val videoCallingEnabledKey = booleanPreferencesKey("call_video_enabled")
    private val startVideoCameraOnKey = booleanPreferencesKey("call_start_video_camera_on")
    private val preferFrontCameraKey = booleanPreferencesKey("call_prefer_front_camera")
    private val defaultSpeakerVideoKey = booleanPreferencesKey("call_default_speaker_video")
    private val hdVideoQualityKey = booleanPreferencesKey("call_hd_video_quality")

    // Push Notifications & Activity Alerts preferences
    private val pushNotificationsEnabledKey = booleanPreferencesKey("notif_push_enabled")
    private val incomingCallNotifKey = booleanPreferencesKey("notif_incoming_calls")
    private val outgoingCallNotifKey = booleanPreferencesKey("notif_outgoing_calls")
    private val receivedActivityNotifKey = booleanPreferencesKey("notif_received_activity")
    private val mutedChatIdsKey = stringSetPreferencesKey("notif_muted_chat_ids")

    val themeModeFlow: Flow<ThemeMode> = context.applicationContext.mjChattyDataStore.data
        .map { prefs -> ThemeMode.fromKey(prefs[themeModeKey]) }

    val typingStatusConfigFlow: Flow<TypingStatusConfig> = context.applicationContext.mjChattyDataStore.data
        .map { prefs ->
            TypingStatusConfig(
                sendTypingIndicator = prefs[sendTypingKey] ?: true,
                showPeerTypingIndicator = prefs[showPeerTypingKey] ?: true,
                typingTimeoutSeconds = (prefs[typingTimeoutSecondsKey] ?: 3).coerceIn(2, 10),
                displayStyle = TypingDisplayStyle.fromKey(prefs[typingDisplayStyleKey]),
            )
        }

    val chatLockConfigFlow: Flow<ChatLockConfig> = context.applicationContext.mjChattyDataStore.data
        .map { prefs ->
            ChatLockConfig(
                passcodeHash = prefs[chatLockPasscodeHashKey].orEmpty(),
                hideLockedChats = prefs[chatLockHideFolderKey] ?: false,
            )
        }

    val callSettingsConfigFlow: Flow<CallSettingsConfig> = context.applicationContext.mjChattyDataStore.data
        .map { prefs ->
            CallSettingsConfig(
                videoCallingEnabled = prefs[videoCallingEnabledKey] ?: true,
                startVideoWithCameraOn = prefs[startVideoCameraOnKey] ?: true,
                preferFrontCamera = prefs[preferFrontCameraKey] ?: true,
                defaultSpeakerForVideo = prefs[defaultSpeakerVideoKey] ?: true,
                hdVideoQuality = prefs[hdVideoQualityKey] ?: true,
            )
        }

    val notificationSettingsFlow: Flow<NotificationSettingsConfig> = context.applicationContext.mjChattyDataStore.data
        .map { prefs ->
            NotificationSettingsConfig(
                pushNotificationsEnabled = prefs[pushNotificationsEnabledKey] ?: true,
                incomingCallNotifications = prefs[incomingCallNotifKey] ?: true,
                outgoingCallNotifications = prefs[outgoingCallNotifKey] ?: true,
                receivedActivityNotifications = prefs[receivedActivityNotifKey] ?: true,
                mutedChatIds = prefs[mutedChatIdsKey].orEmpty(),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[themeModeKey] = mode.key
        }
    }

    suspend fun setSendTypingIndicator(enabled: Boolean) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[sendTypingKey] = enabled
        }
    }

    suspend fun setShowPeerTypingIndicator(enabled: Boolean) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[showPeerTypingKey] = enabled
        }
    }

    suspend fun setTypingTimeoutSeconds(seconds: Int) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[typingTimeoutSecondsKey] = seconds.coerceIn(2, 10)
        }
    }

    suspend fun setTypingDisplayStyle(style: TypingDisplayStyle) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[typingDisplayStyleKey] = style.key
        }
    }

    suspend fun setChatLockPasscode(rawCode: String) {
        val trimmed = rawCode.trim()
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(chatLockPasscodeHashKey)
                prefs[chatLockHideFolderKey] = false
            } else {
                prefs[chatLockPasscodeHashKey] = ChatLockConfig.hashSecretCode(trimmed)
            }
        }
    }

    suspend fun clearChatLockPasscode() {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs.remove(chatLockPasscodeHashKey)
            prefs[chatLockHideFolderKey] = false
        }
    }

    suspend fun setHideLockedChats(hide: Boolean) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[chatLockHideFolderKey] = hide
        }
    }

    suspend fun setVideoCallingEnabled(enabled: Boolean) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[videoCallingEnabledKey] = enabled
        }
    }

    suspend fun setStartVideoWithCameraOn(enabled: Boolean) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[startVideoCameraOnKey] = enabled
        }
    }

    suspend fun setPreferFrontCamera(front: Boolean) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[preferFrontCameraKey] = front
        }
    }

    suspend fun setDefaultSpeakerForVideo(enabled: Boolean) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[defaultSpeakerVideoKey] = enabled
        }
    }

    suspend fun setHdVideoQuality(hd: Boolean) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[hdVideoQualityKey] = hd
        }
    }

    suspend fun setPushNotificationsEnabled(enabled: Boolean) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[pushNotificationsEnabledKey] = enabled
        }
    }

    suspend fun setIncomingCallNotifications(enabled: Boolean) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[incomingCallNotifKey] = enabled
        }
    }

    suspend fun setOutgoingCallNotifications(enabled: Boolean) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[outgoingCallNotifKey] = enabled
        }
    }

    suspend fun setReceivedActivityNotifications(enabled: Boolean) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            prefs[receivedActivityNotifKey] = enabled
        }
    }

    suspend fun setChatMuted(chatId: String, muted: Boolean) {
        if (chatId.isBlank()) return
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            val current = prefs[mutedChatIdsKey].orEmpty()
            prefs[mutedChatIdsKey] = if (muted) current + chatId else current - chatId
        }
    }

    suspend fun savePendingPhone(phone: String) {
        context.applicationContext.mjChattyDataStore.edit { prefs ->
            if (phone.isBlank()) {
                prefs.remove(pendingPhoneKey)
            } else {
                prefs[pendingPhoneKey] = phone
            }
        }
    }

    suspend fun consumePendingPhone(): String {
        val current = context.applicationContext.mjChattyDataStore.data.first()[pendingPhoneKey].orEmpty()
        if (current.isNotBlank()) {
            context.applicationContext.mjChattyDataStore.edit { prefs ->
                prefs.remove(pendingPhoneKey)
            }
        }
        return current
    }
}
