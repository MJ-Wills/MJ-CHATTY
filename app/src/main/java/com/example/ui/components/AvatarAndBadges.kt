package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.ImageMediaUtils
import com.example.data.TypingDisplayStyle
import com.example.ui.theme.LocalMjColors
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val AvatarGradientPalettes = listOf(
    listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
    listOf(Color(0xFF1CE876), Color(0xFF009E60)),
    listOf(Color(0xFFFFB800), Color(0xFFFF6B00)),
    listOf(Color(0xFF00E5FF), Color(0xFF1CE876)),
    listOf(Color(0xFF3A7BD5), Color(0xFF00D2FF)),
    listOf(Color(0xFFFF9800), Color(0xFFE65100)),
)

@Composable
fun MjAvatar(
    name: String,
    avatarData: String,
    size: Dp = 52.dp,
    online: Boolean = false,
    showOnlineIndicator: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val mjColors = LocalMjColors.current
    val initials = remember(name) {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "MJ"
        }
    }
    val gradientColors = remember(name) {
        AvatarGradientPalettes[abs(name.hashCode()) % AvatarGradientPalettes.size]
    }
    val decodedBitmap by produceState(
        initialValue = ImageMediaUtils.getCachedBitmap(avatarData),
        key1 = avatarData,
    ) {
        if (value == null && avatarData.startsWith("data:image/")) {
            value = ImageMediaUtils.decodeDataUriToBitmapAsync(avatarData)
        }
    }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = if (mjColors.isDark) 6.dp else 3.dp,
                    shape = CircleShape,
                    ambientColor = mjColors.neonCyan.copy(alpha = 0.25f),
                    spotColor = mjColors.neonCyan.copy(alpha = 0.25f),
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(gradientColors))
                .border(
                    width = if (online && showOnlineIndicator) 2.dp else 1.dp,
                    color = if (online && showOnlineIndicator) {
                        mjColors.neonEmerald.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val avatarBmp = decodedBitmap
            when {
                avatarBmp != null -> {
                    Image(
                        bitmap = avatarBmp.asImageBitmap(),
                        contentDescription = "$name profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                avatarData.startsWith("http://") || avatarData.startsWith("https://") -> {
                    AsyncImage(
                        model = avatarData,
                        contentDescription = "$name profile picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = (size.value * 0.36f).sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                    )
                }
            }
        }

        if (showOnlineIndicator && online) {
            val dotSize = (size * 0.27f).coerceIn(11.dp, 16.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(mjColors.neonEmerald),
            )
        }
    }
}

@Composable
fun MessageStatusTicks(
    status: String,
    onOutgoingBubble: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val mjColors = LocalMjColors.current
    val neutralTint = if (onOutgoingBubble) {
        mjColors.outgoingBubbleMeta
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    }
    val (icon, tint, desc) = when (status) {
        "read" -> Triple(
            Icons.Filled.DoneAll,
            mjColors.readTick,
            "Read",
        )
        "delivered" -> Triple(
            Icons.Filled.DoneAll,
            neutralTint,
            "Delivered",
        )
        "sending" -> Triple(
            Icons.Filled.Schedule,
            neutralTint,
            "Sending",
        )
        else -> Triple(
            Icons.Filled.Done,
            neutralTint,
            "Sent",
        )
    }
    Icon(
        imageVector = icon,
        contentDescription = desc,
        tint = tint,
        modifier = modifier.size(16.dp),
    )
}

@Composable
fun TypingDotsIndicator(
    dotColor: Color = LocalMjColors.current.neonEmerald,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "typing_dots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0..2) {
            val offsetY by transition.animateFloat(
                initialValue = 0f,
                targetValue = -4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 420,
                        delayMillis = index * 130,
                        easing = LinearEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot_$index",
            )
            Box(
                modifier = Modifier
                    .offset(y = offsetY.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}

@Composable
fun ConfiguredTypingIndicator(
    style: TypingDisplayStyle,
    label: String,
    accentColor: Color = LocalMjColors.current.neonEmerald,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelSmall,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (style == TypingDisplayStyle.DOTS_AND_TEXT || style == TypingDisplayStyle.DOTS_ONLY) {
            TypingDotsIndicator(dotColor = accentColor)
        }
        if (style == TypingDisplayStyle.DOTS_AND_TEXT || style == TypingDisplayStyle.TEXT_ONLY) {
            Text(
                text = label,
                style = textStyle,
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun VerifiedRegisteredPill(modifier: Modifier = Modifier) {
    val mjColors = LocalMjColors.current
    Surface(
        modifier = modifier,
        color = mjColors.neonEmerald.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = "Verified on MJ Chatty",
                tint = mjColors.neonEmerald,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = "MJ Verified",
                style = MaterialTheme.typography.labelSmall,
                color = mjColors.neonEmerald,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun FullScreenImageDialog(
    imageData: String,
    caption: String,
    onDismiss: () -> Unit,
) {
    val bitmap by produceState(
        initialValue = ImageMediaUtils.getCachedBitmap(imageData),
        key1 = imageData,
    ) {
        if (value == null && imageData.startsWith("data:image/")) {
            value = ImageMediaUtils.decodeDataUriToBitmapAsync(imageData)
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
        ) {
            val previewBmp = bitmap
            if (previewBmp != null) {
                Image(
                    bitmap = previewBmp.asImageBitmap(),
                    contentDescription = caption.ifBlank { "Shared photo" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                )
            } else {
                AsyncImage(
                    model = imageData,
                    contentDescription = caption.ifBlank { "Shared photo" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close photo preview",
                    tint = Color.White,
                )
            }
            if (caption.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                ) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

fun formatChatTimestamp(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return ""
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { time = date }
    return when {
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR) == 1 ->
            "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}

fun formatMessageTime(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
}

fun formatDateSeparator(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return "Today"
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { time = date }
    return when {
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) -> "Today"
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) ->
            SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
    }
}

fun formatLastSeen(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return "Recently active"
    val diffMinutes = ((System.currentTimeMillis() - date.time) / 60_000L).coerceAtLeast(0L)
    return when {
        diffMinutes < 1 -> "Last seen just now"
        diffMinutes < 60 -> "Last seen ${diffMinutes}m ago"
        diffMinutes < 1440 -> "Last seen today at ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)}"
        else -> "Last seen ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)}"
    }
}

fun formatDurationSeconds(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%d:%02d", mins, secs)
}
