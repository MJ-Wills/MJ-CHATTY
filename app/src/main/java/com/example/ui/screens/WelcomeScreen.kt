package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.ChatIdentityUtils
import com.example.data.onGoogleSignInClicked
import com.example.data.onQuickGoogleSignInClicked
import com.example.ui.theme.LocalMjColors
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    onPreparePhoneForAuth: suspend (String) -> Unit,
    onAuthSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val mjColors = LocalMjColors.current

    var phoneInput by remember { mutableStateOf("") }
    var isLoadingGoogle by remember { mutableStateOf(false) }
    var isLoadingPhone by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isBusy = isLoadingGoogle || isLoadingPhone

    val pulseTransition = rememberInfiniteTransition(label = "logo_glow")
    val glowScale by pulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            mjColors.neonCyan.copy(alpha = if (mjColors.isDark) 0.22f else 0.14f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.2f, size.height * 0.16f),
                        radius = size.width * 0.72f,
                    ),
                    radius = size.width * 0.72f,
                    center = Offset(size.width * 0.2f, size.height * 0.16f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            mjColors.neonAmber.copy(alpha = if (mjColors.isDark) 0.16f else 0.11f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.30f),
                        radius = size.width * 0.65f,
                    ),
                    radius = size.width * 0.65f,
                    center = Offset(size.width * 0.85f, size.height * 0.30f),
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Glowing MJ Chatty Logo
            Box(
                modifier = Modifier.size(136.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .graphicsLayer {
                            scaleX = glowScale
                            scaleY = glowScale
                        }
                        .clip(RoundedCornerShape(34.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    mjColors.neonCyan.copy(alpha = 0.38f),
                                    mjColors.neonEmerald.copy(alpha = 0.28f),
                                    mjColors.neonAmber.copy(alpha = 0.35f),
                                )
                            )
                        ),
                )
                AsyncImage(
                    model = remember(context) {
                        ImageRequest.Builder(context)
                            .data(R.drawable.mj_chatty_logo_brand)
                            .size(384)
                            .crossfade(false)
                            .build()
                    },
                    contentDescription = "MJ Chatty Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(116.dp)
                        .shadow(18.dp, RoundedCornerShape(30.dp), spotColor = mjColors.neonCyan)
                        .clip(RoundedCornerShape(30.dp)),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.brand_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Auth Card with Instant Quick Sign-In with Google + Phone Number option
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = if (mjColors.isDark) 0.94f else 0.98f),
                tonalElevation = 4.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, mjColors.subtleBorder.copy(alpha = 0.7f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Instant Quick Sign-In with Google Hero Button
                    Surface(
                        onClick = {
                            if (isBusy) return@Surface
                            isLoadingGoogle = true
                            errorMessage = null
                            scope.launch {
                                onPreparePhoneForAuth("")
                                onQuickGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isLoadingGoogle = false
                                        onAuthSuccess()
                                    },
                                    onAuthError = { msg ->
                                        isLoadingGoogle = false
                                        errorMessage = msg
                                    },
                                    scope = scope,
                                    onAuthCancelled = {
                                        isLoadingGoogle = false
                                    },
                                )
                            }
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(20.dp),
                        color = if (mjColors.isDark) Color(0xFFFFFFFF) else Color(0xFF0E172A),
                        border = BorderStroke(1.5.dp, mjColors.neonCyan.copy(alpha = 0.75f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("welcome_quick_google_signin_button"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                shadowElevation = 2.dp,
                                modifier = Modifier.size(38.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isLoadingGoogle) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color(0xFF4285F4),
                                            strokeWidth = 2.5.dp,
                                        )
                                    } else {
                                        GoogleGEmblem(modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isLoadingGoogle) "Signing in with Google…" else stringResource(R.string.welcome_quick_google),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (mjColors.isDark) Color(0xFF0B1221) else Color.White,
                                )
                                Text(
                                    text = stringResource(R.string.welcome_quick_google_sub),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (mjColors.isDark) Color(0xFF475569) else Color(0xFFCBD5E1),
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = mjColors.neonCyan.copy(alpha = 0.18f),
                                modifier = Modifier.size(32.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Bolt,
                                        contentDescription = "Quick Sign-In",
                                        tint = if (mjColors.isDark) Color(0xFF0077B6) else mjColors.neonCyan,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Divider between Quick Google Sign-In and Phone Number Sign-In
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = mjColors.subtleBorder.copy(alpha = 0.65f),
                        )
                        Text(
                            text = stringResource(R.string.welcome_or_phone),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = mjColors.subtleBorder.copy(alpha = 0.65f),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Phone Number Sign-In Option
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { value ->
                            phoneInput = value.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' }
                                .take(20)
                            errorMessage = null
                        },
                        label = { Text("Phone Number") },
                        placeholder = { Text(stringResource(R.string.welcome_phone_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Phone,
                                contentDescription = "Phone number",
                                tint = mjColors.neonCyan,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = mjColors.neonCyan,
                            unfocusedBorderColor = mjColors.subtleBorder,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("welcome_phone_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.welcome_phone_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val normalized = ChatIdentityUtils.normalizePhoneNumber(phoneInput)
                            if (normalized.isBlank()) {
                                errorMessage = "Please enter a valid phone number with country code (e.g. +1 555 019 2834), or use Quick Sign-In with Google above."
                                return@Button
                            }
                            isLoadingPhone = true
                            errorMessage = null
                            scope.launch {
                                onPreparePhoneForAuth(normalized)
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isLoadingPhone = false
                                        onAuthSuccess()
                                    },
                                    onAuthError = { msg ->
                                        isLoadingPhone = false
                                        errorMessage = msg
                                    },
                                    scope = scope,
                                    onAuthCancelled = {
                                        isLoadingPhone = false
                                    },
                                )
                            }
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = mjColors.neonCyan,
                            contentColor = Color(0xFF001A2B),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("welcome_continue_button"),
                    ) {
                        if (isLoadingPhone) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFF001A2B),
                                strokeWidth = 2.5.dp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Linking phone & signing in…",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            Text(
                                text = "Continue with Phone",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WelcomeFeatureRow(
                    icon = Icons.Filled.VerifiedUser,
                    text = "Verified Google identity & automatic cloud sync",
                    tint = mjColors.neonEmerald,
                )
                WelcomeFeatureRow(
                    icon = Icons.Filled.Lock,
                    text = "Live contact verification, typing status & read receipts",
                    tint = mjColors.neonCyan,
                )
            }
        }
    }
}

@Composable
private fun GoogleGEmblem(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.21f
        val inset = strokeWidth / 2f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)

        // Red arc (top)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = -140f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
        )
        // Yellow arc (left)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 140f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
        )
        // Green arc (bottom)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 95f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
        )
        // Blue arc (right)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -10f,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
        )
        // Blue horizontal bar
        val midY = size.height / 2f
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.50f, midY),
            end = Offset(size.width - inset * 0.6f, midY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt,
        )
    }
}

@Composable
private fun WelcomeFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
