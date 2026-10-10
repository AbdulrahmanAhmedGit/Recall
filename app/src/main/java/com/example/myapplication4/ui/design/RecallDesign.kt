package com.example.myapplication4.ui.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import com.example.myapplication4.R

object RecallSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val ml = 20.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 40.dp
}

object RecallRadii {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(14.dp)
    val large = RoundedCornerShape(20.dp)
    val extraLarge = RoundedCornerShape(28.dp)
}

object RecallMotion {
    const val press = 120
    const val quick = 160
    const val standard = 240
    const val page = 280
    const val number = 600
    val softSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

object RecallSizes {
    val pagePadding = 20.dp
    val compactPagePadding = 16.dp
    val icon = 22.dp
    val touch = 48.dp
    val buttonHeight = 52.dp
    val dockHeight = 68.dp
    val contentMaxWidth = 720.dp
}

internal val InterFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
private val RecallFont = InterFont
// Static weights also render correctly on API 24/25. All assets are bundled offline.
internal val CairoFont = FontFamily(
    Font(R.font.cairo_regular, FontWeight.Normal),
    Font(R.font.cairo_medium, FontWeight.Medium),
    Font(R.font.cairo_semibold, FontWeight.SemiBold),
    Font(R.font.cairo_bold, FontWeight.Bold),
)

val RecallTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontFamily = RecallFont, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 48.sp, letterSpacing = (-0.8).sp),
    displayMedium = TextStyle(fontFamily = RecallFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 39.sp, letterSpacing = (-0.45).sp),
    displaySmall = TextStyle(fontFamily = RecallFont, fontWeight = FontWeight.SemiBold, fontSize = 27.sp, lineHeight = 34.sp, letterSpacing = (-0.25).sp),
    headlineLarge = androidx.compose.material3.Typography().headlineLarge.copy(fontFamily = RecallFont),
    headlineMedium = TextStyle(fontFamily = RecallFont, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 34.sp),
    headlineSmall = androidx.compose.material3.Typography().headlineSmall.copy(fontFamily = RecallFont),
    titleLarge = TextStyle(fontFamily = RecallFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = RecallFont, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 25.sp),
    titleSmall = TextStyle(fontFamily = RecallFont, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = RecallFont, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontFamily = RecallFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontFamily = RecallFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = RecallFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.05.sp),
    labelMedium = TextStyle(fontFamily = RecallFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.12.sp),
    labelSmall = TextStyle(fontFamily = RecallFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.18.sp),
)

private fun TextStyle.arabic() = copy(fontFamily = CairoFont, letterSpacing = 0.sp)
private val ArabicTypography = RecallTypography.copy(
    displayLarge = RecallTypography.displayLarge.arabic(),
    displayMedium = RecallTypography.displayMedium.arabic(),
    displaySmall = RecallTypography.displaySmall.arabic(),
    headlineLarge = RecallTypography.headlineLarge.arabic(),
    headlineMedium = RecallTypography.headlineMedium.arabic(),
    headlineSmall = RecallTypography.headlineSmall.arabic(),
    titleLarge = RecallTypography.titleLarge.arabic(),
    titleMedium = RecallTypography.titleMedium.arabic(),
    titleSmall = RecallTypography.titleSmall.arabic(),
    bodyLarge = RecallTypography.bodyLarge.arabic(),
    bodyMedium = RecallTypography.bodyMedium.arabic(),
    bodySmall = RecallTypography.bodySmall.arabic(),
    labelLarge = RecallTypography.labelLarge.arabic(),
    labelMedium = RecallTypography.labelMedium.arabic(),
    labelSmall = RecallTypography.labelSmall.arabic(),
)

internal fun recallTypography(language: String) = if (language.substringBefore('-') == "ar") ArabicTypography else RecallTypography

private val LightColors = lightColorScheme(
    primary = Color(0xFF315F87), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBFA), onPrimaryContainer = Color(0xFF123652),
    secondary = Color(0xFF59636F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8EDF2), onSecondaryContainer = Color(0xFF303942),
    tertiary = Color(0xFF5E5A89), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8E5F7), onTertiaryContainer = Color(0xFF35315F),
    background = Color(0xFFF7F7F5), onBackground = Color(0xFF1B1D20),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1B1D20),
    surfaceContainerLow = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFF0F2F4),
    surfaceVariant = Color(0xFFEEF0F2), onSurfaceVariant = Color(0xFF5B626A),
    outline = Color(0xFF858C94), outlineVariant = Color(0xFFD9DDE1),
    error = Color(0xFFB3262D), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD9), onErrorContainer = Color(0xFF6E1017),
    inverseSurface = Color(0xFF303236), inverseOnSurface = Color(0xFFF2F2F0),
    inversePrimary = Color(0xFFA5CDF3), scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6CDF2), onPrimary = Color(0xFF073451),
    primaryContainer = Color(0xFF214A68), onPrimaryContainer = Color(0xFFD7EAFC),
    secondary = Color(0xFFBEC8D2), onSecondary = Color(0xFF29323B),
    secondaryContainer = Color(0xFF39434D), onSecondaryContainer = Color(0xFFDCE3EA),
    tertiary = Color(0xFFC8C2F0), onTertiary = Color(0xFF302B5A),
    tertiaryContainer = Color(0xFF47426F), onTertiaryContainer = Color(0xFFE7E2FF),
    background = Color(0xFF111315), onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF191C1F), onSurface = Color(0xFFE8EAED),
    surfaceContainerLow = Color(0xFF202428), surfaceContainerHigh = Color(0xFF252A2F),
    surfaceVariant = Color(0xFF292D31), onSurfaceVariant = Color(0xFFB8C0C8),
    outline = Color(0xFF8A929A), outlineVariant = Color(0xFF3A4046),
    error = Color(0xFFFFB3B5), onError = Color(0xFF68000A),
    errorContainer = Color(0xFF8C1D25), onErrorContainer = Color(0xFFFFDAD9),
    inverseSurface = Color(0xFFE8EAED), inverseOnSurface = Color(0xFF2E3134),
    inversePrimary = Color(0xFF315F87), scrim = Color(0xFF000000),
)

val ColorScheme.surfaceElevated get() = surfaceContainerLow
val ColorScheme.surfaceInteractive get() = surfaceContainerHigh
val ColorScheme.surfaceSelected get() = primary.copy(alpha = if (isRecallLight) .12f else .16f).compositeOver(surface)
val ColorScheme.success get() = if (isRecallLight) Color(0xFF2F7156) else Color(0xFF82D3AA)
val ColorScheme.warning get() = if (isRecallLight) Color(0xFF8A5A13) else Color(0xFFF2C174)
val ColorScheme.muted get() = onSurfaceVariant
val ColorScheme.isRecallLight get() = background.luminance() > .5f

@Composable
fun RecallTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, language: String? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= 31 && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && android.os.Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    RecallMotionProvider { MaterialTheme(colorScheme = colors, typography = recallTypography(language ?: LocalConfiguration.current.locales[0].language)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    } }
}

private fun Color.luminance(): Float = (red * .299f) + (green * .587f) + (blue * .114f)
