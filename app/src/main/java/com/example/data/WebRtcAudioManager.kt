package com.example.data

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

enum class WebRtcConnectionStage(val label: String) {
    IDLE("Idle"),
    CALLING("Calling…"),
    RINGING("Ringing…"),
    CONNECTING("Connecting…"),
    CONNECTED("Connected"),
    RECONNECTING("Reconnecting…"),
    FAILED("Connection failed"),
    ENDED("Call ended"),
}

/**
 * Direct peer-to-peer WebRTC Audio & Video Call engine built on Stream's Android WebRTC library
 * (org.webrtc.*). Uses Firestore for SDP Offer/Answer and Trickle-ICE candidate exchange,
 * routes live full-duplex audio through Android's AudioManager communication mode, and captures
 * and renders hardware-accelerated camera video via EglBase + Camera2/Camera1 capturer.
 */
class WebRtcAudioManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    // Live WebRTC Video capture & tracks
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var localVideoSource: VideoSource? = null
    private var activeLocalVideoTrack: VideoTrack? = null

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    val eglBaseContext: EglBase.Context?
        get() = ensureEglBase().eglBaseContext

    private val pendingRemoteCandidates = CopyOnWriteArrayList<IceCandidate>()
    private val remoteDescriptionReady = AtomicBoolean(false)
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL

    @Synchronized
    private fun ensureEglBase(): EglBase {
        eglBase?.let { return it }
        val created = EglBase.create()
        eglBase = created
        return created
    }

    @Synchronized
    private fun ensureFactory(): PeerConnectionFactory {
        peerConnectionFactory?.let { return it }

        if (factoryInitialized.compareAndSet(false, true)) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
        }

        val sharedEgl = ensureEglBase()
        val audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
            .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
            .createAudioDeviceModule()

        val encoderFactory = DefaultVideoEncoderFactory(
            sharedEgl.eglBaseContext,
            /* enableIntelVp8Encoder = */ true,
            /* enableH264HighProfile = */ true,
        )
        val decoderFactory = DefaultVideoDecoderFactory(sharedEgl.eglBaseContext)

        val options = PeerConnectionFactory.Options()
        val factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        audioDeviceModule.release()
        peerConnectionFactory = factory
        return factory
    }

    private fun configureAudioSession(speakerOn: Boolean) {
        runCatching {
            previousAudioMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = false
            applySpeakerRoute(speakerOn)
        }.onFailure { err ->
            Log.w(TAG, "AudioManager setup warning", err)
        }
    }

    @Suppress("DEPRECATION")
    private fun applySpeakerRoute(enabled: Boolean) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val targetType = if (enabled) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                val device = audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == targetType }
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                } else {
                    audioManager.isSpeakerphoneOn = enabled
                }
            } else {
                audioManager.isSpeakerphoneOn = enabled
            }
        }.onFailure { err ->
            Log.w(TAG, "Speaker route toggle warning", err)
        }
    }

    private fun createCameraCapturer(preferFront: Boolean): CameraVideoCapturer? {
        val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(appContext)) {
            Camera2Enumerator(appContext)
        } else {
            Camera1Enumerator(false)
        }
        val names = enumerator.deviceNames
        if (names.isEmpty()) return null

        val preferred = names.firstOrNull { name ->
            if (preferFront) enumerator.isFrontFacing(name) else enumerator.isBackFacing(name)
        }
        val fallback = names.firstOrNull { it != preferred }
        val ordered = listOfNotNull(preferred, fallback)
        for (deviceName in ordered) {
            val capturer = runCatching { enumerator.createCapturer(deviceName, null) }.getOrNull()
            if (capturer != null) return capturer
        }
        return null
    }

    private fun attachLocalVideoTrack(
        factory: PeerConnectionFactory,
        pc: PeerConnection,
        preferFrontCamera: Boolean,
        hdVideo: Boolean,
        startCameraOn: Boolean,
    ) {
        runCatching {
            val capturer = createCameraCapturer(preferFront = preferFrontCamera) ?: return
            val sharedEgl = ensureEglBase()
            val textureHelper = SurfaceTextureHelper.create("MjWebRtcCapture", sharedEgl.eglBaseContext)
            val source = factory.createVideoSource(capturer.isScreencast)
            capturer.initialize(textureHelper, appContext, source.capturerObserver)

            val width = if (hdVideo) 1280 else 640
            val height = if (hdVideo) 720 else 480
            val fps = if (hdVideo) 30 else 24
            capturer.startCapture(width, height, fps)

            val track = factory.createVideoTrack("mj_video_track_${System.currentTimeMillis()}", source)
            track.setEnabled(startCameraOn)
            pc.addTrack(track, listOf("mj_media_stream"))

            videoCapturer = capturer
            surfaceTextureHelper = textureHelper
            localVideoSource = source
            activeLocalVideoTrack = track
            _localVideoTrack.value = track
        }.onFailure { err ->
            Log.w(TAG, "Video capturer initialization warning (continuing with audio)", err)
        }
    }

    private fun createPeerConnection(
        isVideo: Boolean,
        preferFrontCamera: Boolean,
        hdVideo: Boolean,
        startCameraOn: Boolean,
        speakerOn: Boolean,
        onLocalIceCandidate: (sdpMid: String, sdpMLineIndex: Int, sdp: String) -> Unit,
        onStageChanged: (WebRtcConnectionStage) -> Unit,
    ): PeerConnection? {
        endCall()
        remoteDescriptionReady.set(false)
        pendingRemoteCandidates.clear()

        val factory = runCatching { ensureFactory() }
            .onFailure { Log.e(TAG, "Failed to initialize WebRTC factory", it) }
            .getOrNull() ?: return null

        configureAudioSession(speakerOn = speakerOn)

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }

        val pc = factory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    when (state) {
                        PeerConnection.IceConnectionState.CHECKING ->
                            onStageChanged(WebRtcConnectionStage.CONNECTING)
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED ->
                            onStageChanged(WebRtcConnectionStage.CONNECTED)
                        PeerConnection.IceConnectionState.DISCONNECTED ->
                            onStageChanged(WebRtcConnectionStage.RECONNECTING)
                        PeerConnection.IceConnectionState.FAILED ->
                            onStageChanged(WebRtcConnectionStage.FAILED)
                        PeerConnection.IceConnectionState.CLOSED ->
                            onStageChanged(WebRtcConnectionStage.ENDED)
                        else -> Unit
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit

                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate != null) {
                        onLocalIceCandidate(
                            candidate.sdpMid.orEmpty(),
                            candidate.sdpMLineIndex,
                            candidate.sdp.orEmpty(),
                        )
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
                override fun onAddStream(stream: MediaStream?) {
                    val video = stream?.videoTracks?.firstOrNull()
                    if (video != null) {
                        video.setEnabled(true)
                        _remoteVideoTrack.value = video
                    }
                }

                override fun onRemoveStream(stream: MediaStream?) = Unit
                override fun onDataChannel(channel: DataChannel?) = Unit
                override fun onRenegotiationNeeded() = Unit

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    track?.setEnabled(true)
                    if (track is VideoTrack) {
                        _remoteVideoTrack.value = track
                    }
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    val track = transceiver?.receiver?.track()
                    track?.setEnabled(true)
                    if (track is VideoTrack) {
                        _remoteVideoTrack.value = track
                    }
                }
            },
        ) ?: return null

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        val source = factory.createAudioSource(audioConstraints)
        val track = factory.createAudioTrack("mj_audio_track_${System.currentTimeMillis()}", source)
        track.setEnabled(true)
        pc.addTrack(track, listOf("mj_media_stream"))

        localAudioSource = source
        localAudioTrack = track

        if (isVideo) {
            attachLocalVideoTrack(
                factory = factory,
                pc = pc,
                preferFrontCamera = preferFrontCamera,
                hdVideo = hdVideo,
                startCameraOn = startCameraOn,
            )
        }

        peerConnection = pc
        return pc
    }

    fun startOutgoingCall(
        isVideo: Boolean = false,
        preferFrontCamera: Boolean = true,
        hdVideo: Boolean = true,
        startCameraOn: Boolean = true,
        speakerOn: Boolean = isVideo,
        onLocalOfferSdp: (String) -> Unit,
        onLocalIceCandidate: (sdpMid: String, sdpMLineIndex: Int, sdp: String) -> Unit,
        onStageChanged: (WebRtcConnectionStage) -> Unit,
        onError: (String) -> Unit,
    ) {
        val pc = createPeerConnection(
            isVideo = isVideo,
            preferFrontCamera = preferFrontCamera,
            hdVideo = hdVideo,
            startCameraOn = startCameraOn,
            speakerOn = speakerOn,
            onLocalIceCandidate = onLocalIceCandidate,
            onStageChanged = onStageChanged,
        )
        if (pc == null) {
            onError("Unable to initialize WebRTC call engine.")
            return
        }

        val offerConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideo) "true" else "false"))
        }

        pc.createOffer(
            object : SimpleSdpObserver("createOffer") {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc == null) {
                        onError("Failed to create WebRTC call offer.")
                        return
                    }
                    pc.setLocalDescription(
                        object : SimpleSdpObserver("setLocalOffer") {
                            override fun onSetSuccess() {
                                onLocalOfferSdp(desc.description)
                            }

                            override fun onSetFailure(error: String?) {
                                onError(error ?: "Failed to apply local offer.")
                            }
                        },
                        desc,
                    )
                }

                override fun onCreateFailure(error: String?) {
                    onError(error ?: "Could not create WebRTC offer.")
                }
            },
            offerConstraints,
        )
    }

    fun answerIncomingCall(
        remoteOfferSdp: String,
        isVideo: Boolean = false,
        preferFrontCamera: Boolean = true,
        hdVideo: Boolean = true,
        startCameraOn: Boolean = true,
        speakerOn: Boolean = isVideo,
        onLocalAnswerSdp: (String) -> Unit,
        onLocalIceCandidate: (sdpMid: String, sdpMLineIndex: Int, sdp: String) -> Unit,
        onStageChanged: (WebRtcConnectionStage) -> Unit,
        onError: (String) -> Unit,
    ) {
        val pc = createPeerConnection(
            isVideo = isVideo,
            preferFrontCamera = preferFrontCamera,
            hdVideo = hdVideo,
            startCameraOn = startCameraOn,
            speakerOn = speakerOn,
            onLocalIceCandidate = onLocalIceCandidate,
            onStageChanged = onStageChanged,
        )
        if (pc == null) {
            onError("Unable to initialize WebRTC call engine.")
            return
        }

        val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp)
        pc.setRemoteDescription(
            object : SimpleSdpObserver("setRemoteOffer") {
                override fun onSetSuccess() {
                    remoteDescriptionReady.set(true)
                    flushPendingCandidates(pc)

                    val answerConstraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideo) "true" else "false"))
                    }
                    pc.createAnswer(
                        object : SimpleSdpObserver("createAnswer") {
                            override fun onCreateSuccess(desc: SessionDescription?) {
                                if (desc == null) {
                                    onError("Failed to generate WebRTC answer.")
                                    return
                                }
                                pc.setLocalDescription(
                                    object : SimpleSdpObserver("setLocalAnswer") {
                                        override fun onSetSuccess() {
                                            onLocalAnswerSdp(desc.description)
                                        }

                                        override fun onSetFailure(error: String?) {
                                            onError(error ?: "Failed to set local answer.")
                                        }
                                    },
                                    desc,
                                )
                            }

                            override fun onCreateFailure(error: String?) {
                                onError(error ?: "Failed to create answer.")
                            }
                        },
                        answerConstraints,
                    )
                }

                override fun onSetFailure(error: String?) {
                    onError(error ?: "Invalid remote call offer.")
                }
            },
            remoteOffer,
        )
    }

    fun onRemoteAnswerReceived(answerSdp: String, onError: (String) -> Unit = {}) {
        val pc = peerConnection ?: return
        if (remoteDescriptionReady.get()) return
        val remoteAnswer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        pc.setRemoteDescription(
            object : SimpleSdpObserver("setRemoteAnswer") {
                override fun onSetSuccess() {
                    remoteDescriptionReady.set(true)
                    flushPendingCandidates(pc)
                }

                override fun onSetFailure(error: String?) {
                    onError(error ?: "Could not apply remote call answer.")
                }
            },
            remoteAnswer,
        )
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        if (sdp.isBlank()) return
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        val pc = peerConnection
        if (pc != null && remoteDescriptionReady.get()) {
            pc.addIceCandidate(candidate)
        } else {
            pendingRemoteCandidates.add(candidate)
        }
    }

    private fun flushPendingCandidates(pc: PeerConnection) {
        val snapshot = pendingRemoteCandidates.toList()
        pendingRemoteCandidates.clear()
        snapshot.forEach { candidate ->
            pc.addIceCandidate(candidate)
        }
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
        runCatching { audioManager.isMicrophoneMute = muted }
    }

    fun setSpeakerphone(enabled: Boolean) {
        applySpeakerRoute(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        runCatching { activeLocalVideoTrack?.setEnabled(enabled) }
    }

    fun switchCamera(onSwitched: (Boolean) -> Unit = {}) {
        val capturer = videoCapturer ?: return
        runCatching {
            capturer.switchCamera(
                object : CameraVideoCapturer.CameraSwitchHandler {
                    override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                        onSwitched(isFrontCamera)
                    }

                    override fun onCameraSwitchError(errorDescription: String?) {
                        Log.w(TAG, "switchCamera error: $errorDescription")
                    }
                }
            )
        }
    }

    @Suppress("DEPRECATION")
    fun endCall() {
        remoteDescriptionReady.set(false)
        pendingRemoteCandidates.clear()

        _localVideoTrack.value = null
        _remoteVideoTrack.value = null

        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null

        runCatching { activeLocalVideoTrack?.setEnabled(false) }
        runCatching { activeLocalVideoTrack?.dispose() }
        activeLocalVideoTrack = null

        runCatching { localVideoSource?.dispose() }
        localVideoSource = null

        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null

        runCatching { localAudioTrack?.setEnabled(false) }
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null

        runCatching { localAudioSource?.dispose() }
        localAudioSource = null

        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                audioManager.isSpeakerphoneOn = false
            }
            audioManager.isMicrophoneMute = false
            audioManager.mode = previousAudioMode
        }
    }

    fun release() {
        endCall()
        runCatching { peerConnectionFactory?.dispose() }
        peerConnectionFactory = null
        runCatching { eglBase?.release() }
        eglBase = null
    }

    private open class SimpleSdpObserver(private val action: String) : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) {
            Log.w(TAG, "$action onCreateFailure: $error")
        }

        override fun onSetFailure(error: String?) {
            Log.w(TAG, "$action onSetFailure: $error")
        }
    }

    private companion object {
        const val TAG = "WebRtcAudioManager"
        val factoryInitialized = AtomicBoolean(false)
    }
}
