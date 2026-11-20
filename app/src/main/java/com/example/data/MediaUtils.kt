package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

private const val MEDIA_TAG = "MjMediaUtils"

object ImageMediaUtils {
    private val bitmapCache = object : LruCache<Int, Bitmap>(32) {}

    suspend fun encodeImageUriToDataUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = 640,
        jpegQuality: Int = 72,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.applicationContext.contentResolver
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return@withContext null

            var sampleSize = 1
            while (
                boundsOptions.outWidth / sampleSize > maxDimension * 2 ||
                boundsOptions.outHeight / sampleSize > maxDimension * 2
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            val scale = minOf(
                1f,
                maxDimension.toFloat() / maxOf(rawBitmap.width, rawBitmap.height).toFloat(),
            )
            val scaledBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    rawBitmap,
                    (rawBitmap.width * scale).toInt().coerceAtLeast(1),
                    (rawBitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                ).also {
                    if (it !== rawBitmap) rawBitmap.recycle()
                }
            } else {
                rawBitmap
            }

            val output = ByteArrayOutputStream()
            var quality = jpegQuality
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            while (output.size() > 210_000 && quality > 30) {
                output.reset()
                quality -= 12
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            }
            scaledBitmap.recycle()

            val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (e: Exception) {
            Log.e(MEDIA_TAG, "Failed to encode image URI", e)
            null
        }
    }

    fun getCachedBitmap(dataUri: String): Bitmap? {
        if (!dataUri.startsWith("data:image/")) return null
        val key = dataUri.hashCode()
        return synchronized(bitmapCache) {
            bitmapCache.get(key)?.takeIf { !it.isRecycled }
        }
    }

    suspend fun decodeDataUriToBitmapAsync(dataUri: String): Bitmap? {
        if (!dataUri.startsWith("data:image/")) return null
        getCachedBitmap(dataUri)?.let { return it }
        return withContext(Dispatchers.Default) {
            decodeDataUriToBitmap(dataUri)
        }
    }

    fun decodeDataUriToBitmap(dataUri: String): Bitmap? {
        if (!dataUri.startsWith("data:image/")) return null
        val key = dataUri.hashCode()
        getCachedBitmap(dataUri)?.let { return it }
        return try {
            val commaIndex = dataUri.indexOf(',')
            if (commaIndex < 0) return null
            val bytes = Base64.decode(dataUri.substring(commaIndex + 1), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { bmp ->
                synchronized(bitmapCache) {
                    bitmapCache.put(key, bmp)
                }
            }
        } catch (e: Exception) {
            Log.w(MEDIA_TAG, "Failed to decode Base64 image", e)
            null
        }
    }
}

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    fun startRecording(): Boolean {
        stopAndDiscard()
        return try {
            val file = File(context.cacheDir, "mj_voice_${System.currentTimeMillis()}.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context.applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(24000)
                setAudioSamplingRate(16000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            startedAtMs = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.e(MEDIA_TAG, "Failed to start voice recording", e)
            stopAndDiscard()
            false
        }
    }

    fun stopAndEncode(): Pair<String, Long>? {
        val file = outputFile
        val durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(500L)
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            if (file == null || !file.exists() || file.length() == 0L) {
                return null
            }
            val bytes = file.readBytes()
            file.delete()
            outputFile = null
            if (bytes.size > 340_000) {
                Log.w(MEDIA_TAG, "Voice clip exceeded 340KB limit: ${bytes.size}")
                return null
            }
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            encoded to durationMs
        } catch (e: Exception) {
            Log.e(MEDIA_TAG, "Failed to stop voice recording", e)
            stopAndDiscard()
            null
        }
    }

    fun stopAndDiscard() {
        try {
            recorder?.reset()
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

class VoiceNotePlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null

    fun playBase64Audio(
        base64Audio: String,
        onCompletion: () -> Unit,
        onError: () -> Unit,
    ) {
        stop()
        try {
            val bytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val file = File(context.cacheDir, "mj_play_${System.currentTimeMillis()}.m4a")
            FileOutputStream(file).use { it.write(bytes) }
            tempFile = file

            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    stop()
                    onCompletion()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onError()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(MEDIA_TAG, "Failed to play voice note", e)
            stop()
            onError()
        }
    }

    fun currentPositionMs(): Int = try {
        mediaPlayer?.currentPosition ?: 0
    } catch (_: Exception) {
        0
    }

    fun durationMs(): Int = try {
        mediaPlayer?.duration?.coerceAtLeast(1) ?: 1
    } catch (_: Exception) {
        1
    }

    fun isPlaying(): Boolean = try {
        mediaPlayer?.isPlaying == true
    } catch (_: Exception) {
        false
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        tempFile?.delete()
        tempFile = null
    }
}
