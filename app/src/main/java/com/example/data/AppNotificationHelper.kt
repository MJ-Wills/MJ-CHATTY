package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class AppNotificationHelper(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager by lazy(LazyThreadSafetyMode.NONE) {
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Volatile
    private var channelsInitialized = false

    fun warmUpChannels() {
        ensureChannels()
    }

    fun hasNotificationPermission(): Boolean {
        val osEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            osEnabled && ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            osEnabled
        }
    }

    @Synchronized
    private fun ensureChannels() {
        if (channelsInitialized || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            channelsInitialized = true
            return
        }

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ringtoneAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val incomingCallChannel = NotificationChannel(
            CHANNEL_INCOMING_CALLS,
            "Incoming Calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when someone calls you on MJ Chatty"
            enableLights(true)
            lightColor = Color.CYAN
            enableVibration(true)
            setSound(ringtoneUri, ringtoneAttrs)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val outgoingCallChannel = NotificationChannel(
            CHANNEL_OUTGOING_CALLS,
            "Outgoing & Active Calls",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Activity alerts for outgoing, connected, and completed calls"
            enableLights(true)
            lightColor = Color.BLUE
            setSound(null, null)
            setShowBadge(false)
        }

        val receivedActivityChannel = NotificationChannel(
            CHANNEL_RECEIVED_ACTIVITY,
            "Received Messages & Activity",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Push notifications for received messages, media, and missed calls"
            enableLights(true)
            lightColor = Color.GREEN
            enableVibration(true)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannels(
            listOf(incomingCallChannel, outgoingCallChannel, receivedActivityChannel)
        )
        channelsInitialized = true
    }

    private fun buildLaunchIntent(
        requestCode: Int,
        openTab: String,
        openChatId: String = "",
        callId: String = "",
    ): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TAB, openTab)
            if (openChatId.isNotBlank()) putExtra(EXTRA_OPEN_CHAT_ID, openChatId)
            if (callId.isNotBlank()) putExtra(EXTRA_CALL_ID, callId)
        }
        return PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    fun showIncomingCallNotification(call: CallSession, peerDisplayName: String) {
        if (!hasNotificationPermission()) return
        ensureChannels()

        val notifId = incomingCallNotificationId(call.id)
        val mediaLabel = if (call.isVideoCall()) "Video Call" else "Audio Call"
        val cleanName = peerDisplayName.ifBlank { call.callerName.ifBlank { "MJ Contact" } }
        val launchIntent = buildLaunchIntent(
            requestCode = notifId,
            openTab = "CALLS",
            callId = call.id,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_INCOMING_CALLS)
            .setSmallIcon(R.drawable.ic_notif_mj)
            .setContentTitle("Incoming $mediaLabel")
            .setContentText("$cleanName is calling you on MJ Chatty")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(launchIntent)
            .setColor(0xFF00E5FF.toInt())
            .build()

        NotificationManagerCompat.from(appContext).notify(notifId, notification)
    }

    fun cancelIncomingCallNotification(callId: String) {
        if (callId.isBlank()) return
        NotificationManagerCompat.from(appContext).cancel(incomingCallNotificationId(callId))
    }

    @SuppressLint("MissingPermission")
    fun showOutgoingCallNotification(
        call: CallSession,
        peerDisplayName: String,
        stage: WebRtcConnectionStage,
        connected: Boolean = false,
    ) {
        if (!hasNotificationPermission()) return
        ensureChannels()

        val notifId = outgoingCallNotificationId(call.id)
        val mediaLabel = if (call.isVideoCall()) "Video" else "Audio"
        val cleanName = peerDisplayName.ifBlank { call.calleeName.ifBlank { "MJ Contact" } }
        val title = when (stage) {
            WebRtcConnectionStage.CONNECTED -> "Ongoing $mediaLabel Call"
            WebRtcConnectionStage.CONNECTING -> "Connecting $mediaLabel Call"
            else -> "Outgoing $mediaLabel Call"
        }
        val body = when (stage) {
            WebRtcConnectionStage.CONNECTED -> "Connected with $cleanName"
            WebRtcConnectionStage.CONNECTING -> "Connecting with $cleanName…"
            WebRtcConnectionStage.RINGING -> "Ringing $cleanName…"
            else -> "Calling $cleanName…"
        }

        val launchIntent = buildLaunchIntent(
            requestCode = notifId,
            openTab = "CALLS",
            callId = call.id,
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_OUTGOING_CALLS)
            .setSmallIcon(R.drawable.ic_notif_mj)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(launchIntent)
            .setColor(0xFF1D6BFF.toInt())

        if (connected) {
            builder.setUsesChronometer(true)
        }

        NotificationManagerCompat.from(appContext).notify(notifId, builder.build())
    }

    @SuppressLint("MissingPermission")
    fun showOutgoingCallCompletedNotification(
        call: CallSession,
        peerDisplayName: String,
        outcomeLabel: String,
    ) {
        if (!hasNotificationPermission()) return
        ensureChannels()

        val notifId = outgoingCallNotificationId(call.id)
        val mediaLabel = if (call.isVideoCall()) "Video" else "Audio"
        val cleanName = peerDisplayName.ifBlank { call.calleeName.ifBlank { "MJ Contact" } }
        val launchIntent = buildLaunchIntent(
            requestCode = notifId,
            openTab = "CALLS",
            callId = call.id,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_OUTGOING_CALLS)
            .setSmallIcon(R.drawable.ic_notif_mj)
            .setContentTitle("Outgoing $mediaLabel Call • $cleanName")
            .setContentText(outcomeLabel)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(launchIntent)
            .setColor(0xFF1D6BFF.toInt())
            .build()

        NotificationManagerCompat.from(appContext).notify(notifId, notification)
    }

    fun cancelOutgoingCallNotification(callId: String) {
        if (callId.isBlank()) return
        NotificationManagerCompat.from(appContext).cancel(outgoingCallNotificationId(callId))
    }

    @SuppressLint("MissingPermission")
    fun showReceivedCallActivityNotification(
        call: CallSession,
        peerDisplayName: String,
        title: String,
        body: String,
    ) {
        if (!hasNotificationPermission()) return
        ensureChannels()

        val notifId = receivedCallNotificationId(call.id)
        val launchIntent = buildLaunchIntent(
            requestCode = notifId,
            openTab = "CALLS",
            callId = call.id,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_RECEIVED_ACTIVITY)
            .setSmallIcon(R.drawable.ic_notif_mj)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(launchIntent)
            .setColor(0xFF00E676.toInt())
            .build()

        NotificationManagerCompat.from(appContext).notify(notifId, notification)
    }

    @SuppressLint("MissingPermission")
    fun showReceivedMessageNotification(
        chatId: String,
        senderName: String,
        previewText: String,
        isLockedChat: Boolean,
    ) {
        if (!hasNotificationPermission()) return
        ensureChannels()

        val notifId = chatNotificationId(chatId)
        val launchIntent = buildLaunchIntent(
            requestCode = notifId,
            openTab = "CHATS",
            openChatId = if (isLockedChat) "" else chatId,
        )

        val title = if (isLockedChat) "Locked Chat" else senderName.ifBlank { "MJ Chatty" }
        val body = if (isLockedChat) {
            "New message received • Open Locked Chats to view"
        } else {
            previewText.ifBlank { "New message received" }
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_RECEIVED_ACTIVITY)
            .setSmallIcon(R.drawable.ic_notif_mj)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(launchIntent)
            .setColor(0xFF00E5FF.toInt())
            .build()

        NotificationManagerCompat.from(appContext).notify(notifId, notification)
    }

    fun cancelChatNotification(chatId: String) {
        if (chatId.isBlank()) return
        NotificationManagerCompat.from(appContext).cancel(chatNotificationId(chatId))
    }

    private fun incomingCallNotificationId(callId: String): Int =
        10_000 + (callId.hashCode() and 0x7FFF)

    private fun outgoingCallNotificationId(callId: String): Int =
        30_000 + (callId.hashCode() and 0x7FFF)

    private fun receivedCallNotificationId(callId: String): Int =
        50_000 + (callId.hashCode() and 0x7FFF)

    private fun chatNotificationId(chatId: String): Int =
        70_000 + (chatId.hashCode() and 0x7FFF)

    companion object {
        const val CHANNEL_INCOMING_CALLS = "mj_calls_incoming"
        const val CHANNEL_OUTGOING_CALLS = "mj_calls_outgoing"
        const val CHANNEL_RECEIVED_ACTIVITY = "mj_received_activity"

        const val EXTRA_OPEN_TAB = "extra_open_tab"
        const val EXTRA_OPEN_CHAT_ID = "extra_open_chat_id"
        const val EXTRA_CALL_ID = "extra_call_id"
    }
}
