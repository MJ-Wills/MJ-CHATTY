package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest
import java.security.SecureRandom

enum class ThemeMode(val key: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    companion object {
        fun fromKey(raw: String?): ThemeMode =
            entries.firstOrNull { it.key == raw } ?: DARK
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface ContactVerificationState {
    data object Idle : ContactVerificationState
    data object Checking : ContactVerificationState
    data class Verified(val entry: DirectoryEntry, val matchedQuery: String) : ContactVerificationState
    data class NotRegistered(val query: String) : ContactVerificationState
    data class InvalidFormat(val hint: String) : ContactVerificationState
}

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val phoneNumber: String = "",
    val about: String = "",
    val avatarData: String = "",
    val online: Boolean = false,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val displayName: String = "",
    val username: String = "",
    val about: String = "",
    val avatarData: String = "",
    val online: Boolean = false,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class DirectoryEntry(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val lookupType: String = "username",
    val displayName: String = "",
    val username: String = "",
    val avatarData: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class SavedContact(
    @DocumentId val id: String = "",
    val contactUid: String = "",
    val savedName: String = "",
    val contactUsername: String = "",
    val contactPhone: String = "",
    val avatarData: String = "",
    val registered: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val memberUids: List<String> = emptyList(),
    val chatType: String = "direct",
    val title: String = "",
    val pinnedByUids: List<String> = emptyList(),
    val hiddenByUids: List<String> = emptyList(),
    val lockedByUids: List<String> = emptyList(),
    val pinnedMessageId: String = "",
    val pinnedMessagePreview: String = "",
    val lastSenderId: String = "",
    val lastMessagePreview: String = "",
    val lastMessageType: String = "text",
    val lastMessageStatus: String = "sent",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val typingUid: String = "",
    @ServerTimestamp val typingUpdatedAt: Timestamp? = null,
    val unreadByUid: String = "",
    val unreadCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun peerUid(currentUid: String): String =
        memberUids.firstOrNull { it != currentUid } ?: currentUid

    fun isSelfChat(currentUid: String): Boolean =
        memberUids.size == 1 && memberUids.firstOrNull() == currentUid

    fun isPinnedBy(uid: String): Boolean = uid in pinnedByUids

    fun isHiddenBy(uid: String): Boolean = uid in hiddenByUids

    fun isLockedBy(uid: String): Boolean = uid in lockedByUids
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val memberUids: List<String> = emptyList(),
    val messageType: String = "text",
    val text: String = "",
    val imageData: String = "",
    val voiceData: String = "",
    val voiceDurationMs: Long = 0L,
    val status: String = "sent",
    val reaction: String = "",
    val edited: Boolean = false,
    @ServerTimestamp val editedAt: Timestamp? = null,
    val pinned: Boolean = false,
    val forwarded: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val callerUid: String = "",
    val calleeUid: String = "",
    val callerName: String = "",
    val calleeName: String = "",
    val callerAvatar: String = "",
    val calleeAvatar: String = "",
    val memberUids: List<String> = emptyList(),
    val callType: String = "audio",
    val status: String = "ringing",
    val offerSdp: String = "",
    val answerSdp: String = "",
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun peerUid(currentUid: String): String =
        if (callerUid == currentUid) calleeUid else callerUid

    fun peerName(currentUid: String): String =
        if (callerUid == currentUid) calleeName else callerName

    fun peerAvatar(currentUid: String): String =
        if (callerUid == currentUid) calleeAvatar else callerAvatar

    fun isOutgoing(currentUid: String): Boolean = callerUid == currentUid

    fun isVideoCall(): Boolean = callType.equals("video", ignoreCase = true)
}

@Keep
@IgnoreExtraProperties
data class IceCandidateRecord(
    @DocumentId val id: String = "",
    val senderUid: String = "",
    val memberUids: List<String> = emptyList(),
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val sdp: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

object ChatIdentityUtils {
    private val usernameRegex = Regex("^[a-z0-9_]{3,24}$")

    fun buildDirectChatId(uidA: String, uidB: String): String {
        return if (uidA == uidB) {
            "dm_${uidA}_${uidA}"
        } else if (uidA < uidB) {
            "dm_${uidA}_${uidB}"
        } else {
            "dm_${uidB}_${uidA}"
        }
    }

    fun sanitizeUsername(raw: String): String =
        raw.trim()
            .removePrefix("@")
            .lowercase()
            .replace(Regex("[^a-z0-9_]"), "")
            .take(24)

    fun isValidUsername(username: String): Boolean =
        usernameRegex.matches(username)

    fun defaultUsernameFor(displayName: String, uid: String): String {
        val base = displayName
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(14)
            .ifBlank { "mjuser" }
        val suffix = uid.lowercase().replace(Regex("[^a-z0-9]"), "").take(4).ifBlank { "0001" }
        val combined = "${base}_$suffix".take(24)
        return if (combined.length >= 3) combined else "mj_$suffix"
    }

    fun normalizePhoneNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < 7) return ""
        return (if (hasPlus) "+$digits" else "+$digits").take(20)
    }

    fun isLikelyPhoneQuery(raw: String): Boolean {
        val stripped = raw.trim()
        if (stripped.startsWith("@")) return false
        val digitsCount = stripped.count { it.isDigit() }
        val lettersCount = stripped.count { it.isLetter() }
        return digitsCount >= 7 && lettersCount == 0
    }

    fun usernameLookupKey(username: String): String = "u_${sanitizeUsername(username)}"

    fun phoneLookupKey(normalizedPhone: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalizedPhone.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(32)
        return "p_$hex"
    }
}

@Keep
@IgnoreExtraProperties
data class PcLinkSession(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val deviceLabel: String = "PC Web Browser",
    val pairingCode: String = "",
    val linkToken: String = "",
    val linkUrl: String = "",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

object PcLinkUtils {
    private val secureRandom = SecureRandom()
    private val tokenChars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generateLinkToken(): String {
        val sb = StringBuilder("mjc_")
        repeat(24) {
            sb.append(tokenChars[secureRandom.nextInt(tokenChars.length)])
        }
        return sb.toString()
    }

    fun generatePairingCode(): String {
        val partA = buildString {
            append("MJ")
            repeat(2) { append(codeChars[secureRandom.nextInt(codeChars.length)]) }
        }
        val partB = buildString {
            repeat(4) { append(codeChars[secureRandom.nextInt(codeChars.length)]) }
        }
        return "$partA-$partB"
    }

    fun buildPcLinkUrl(
        baseUrl: String,
        token: String,
        pairingCode: String,
        uid: String,
    ): String {
        val cleanBase = baseUrl.trim().trimEnd('/').ifBlank {
            "https://ais-pre-5b67unt45s6jbqi2xakzh5-402680087625.europe-west1.run.app"
        }.let { url ->
            if (url.startsWith("https://")) url else "https://${url.removePrefix("http://")}"
        }
        val shortUid = uid.filter { it.isLetterOrDigit() }.take(12).ifBlank { "user" }
        return "$cleanBase/link?token=$token&code=$pairingCode&uid=$shortUid".take(500)
    }
}
