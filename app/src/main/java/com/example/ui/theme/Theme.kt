package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.ThemeMode

@Immutable
data class MjExtendedColors(
    val neonCyan: Color,
    val neonBlue: Color,
    val neonEmerald: Color,
    val neonAmber: Color,
    val outgoingBubbleStart: Color,
    val outgoingBubbleEnd: Color,
    val outgoingBubbleText: Color,
    val outgoingBubbleMeta: Color,
    val incomingBubble: Color,
    val incomingBubbleBorder: Color,
    val chatWallpaperBg: Color,
    val chatWallpaperBgEnd: Color,
    val chatWallpaperPattern: Color,
    val datePillBg: Color,
    val readTick: Color,
    val subtleBorder: Color,
    val isDark: Boolean,
)

private val DarkExtendedColors = MjExtendedColors(
    neonCyan = NeonCyan,
    neonBlue = NeonElectricBlue,
    neonEmerald = NeonEmerald,
    neonAmber = NeonAmber,
    // Deep emerald-teal outgoing bubble contrasts cleanly over neon blue wallpaper & highlights blue read ticks
    outgoingBubbleStart = Color(0xFF005C4B),
    outgoingBubbleEnd = Color(0xFF004B3E),
    outgoingBubbleText = Color(0xFFE9EDEF),
    outgoingBubbleMeta = Color(0xB3E9EDEF),
    incomingBubble = Color(0xFF0C234E),
    incomingBubbleBorder = Color(0xFF1F4D99),
    // Neon Blue chat wallpaper matching the MJ Chatty app icon (#0075FF / #00D2FF glow)
    chatWallpaperBg = Color(0xFF04173D),
    chatWallpaperBgEnd = Color(0xFF082E7A),
    chatWallpaperPattern = Color(0xFF00D2FF),
    datePillBg = Color(0xFF092356),
    readTick = Color(0xFF53BDEB),
    subtleBorder = ObsidianBorder,
    isDark = true,
)

private val LightExtendedColors = MjExtendedColors(
    neonCyan = CyanLightPrimary,
    neonBlue = NeonElectricBlue,
    neonEmerald = EmeraldLightAccent,
    neonAmber = AmberLightAccent,
    // Crisp soft mint outgoing bubble with dark text & vivid blue read ticks
    outgoingBubbleStart = Color(0xFFD9FDD3),
    outgoingBubbleEnd = Color(0xFFCCF7C6),
    outgoingBubbleText = Color(0xFF111B21),
    outgoingBubbleMeta = Color(0xFF54656F),
    incomingBubble = Color(0xFFFFFFFF),
    incomingBubbleBorder = Color(0xFF9ACFFF),
    // Vibrant ice-neon blue chat wallpaper harmonized with MJ Chatty app icon
    chatWallpaperBg = Color(0xFFD6EEFF),
    chatWallpaperBgEnd = Color(0xFFB2DCFF),
    chatWallpaperPattern = Color(0xFF0075FF),
    datePillBg = Color(0xFFEBF6FF),
    readTick = Color(0xFF0075FF),
    subtleBorder = IceBorder,
    isDark = false,
)

val LocalMjColors = staticCompositionLocalOf { DarkExtendedColors }

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF001C2B),
    primaryContainer = Color(0xFF083254),
    onPrimaryContainer = Color(0xFFC2EEFF),
    secondary = NeonEmerald,
    onSecondary = Color(0xFF002410),
    secondaryContainer = Color(0xFF083A22),
    onSecondaryContainer = Color(0xFFB3FFD2),
    tertiary = NeonAmber,
    onTertiary = Color(0xFF2B1B00),
    tertiaryContainer = Color(0xFF473000),
    onTertiaryContainer = Color(0xFFFFE199),
    background = ObsidianBackground,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceVariant,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1A2844),
    error = ErrorRedDark,
    onError = Color(0xFF2B000D),
)

private val LightColorScheme = lightColorScheme(
    primary = CyanLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EEFF),
    onPrimaryContainer = Color(0xFF002447),
    secondary = EmeraldLightAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FADF),
    onSecondaryContainer = Color(0xFF00381A),
    tertiary = AmberLightAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEDC2),
    onTertiaryContainer = Color(0xFF3E2200),
    background = IceBackground,
    onBackground = TextPrimaryLight,
    surface = IceSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = IceSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = IceSurface,
    surfaceContainerLow = Color(0xFFF8FBFF),
    surfaceContainer = IceSurfaceVariant,
    surfaceContainerHigh = IceSurfaceHigh,
    outline = IceBorder,
    outlineVariant = Color(0xFFDDE8F4),
    error = ErrorRedLight,
    onError = Color.White,
)

private val MjChattyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MjChattyTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val extendedColors = if (isDark) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalMjColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MjChattyTypography,
            shapes = MjChattyShapes,
            content = content,
        )
    }
}
