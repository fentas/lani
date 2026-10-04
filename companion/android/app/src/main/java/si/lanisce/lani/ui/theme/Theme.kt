package si.lanisce.lani.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palette from the Slovenian flag and the Alps.
val SloBlue = Color(0xFF0B4EA2)
val SloBlueDeep = Color(0xFF062C63)
val TriglavRed = Color(0xFFE4002B)
val AlpineGreen = Color(0xFF16A06E)
val XpGold = Color(0xFFFFB300)
val FlameOrange = Color(0xFFFF6B1A)
val SnowWhite = Color(0xFFF7F9FD)

private val light = lightColorScheme(
    primary = SloBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E6FA),
    onPrimaryContainer = SloBlueDeep,
    secondary = TriglavRed,
    onSecondary = Color.White,
    tertiary = AlpineGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3F4E6),
    // Set explicitly: the Material default is a pink that clashes on the green containers.
    onTertiaryContainer = Color(0xFF073B27),
    background = SnowWhite,
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF1F8),
)

private val dark = darkColorScheme(
    primary = Color(0xFF8EB6F5),
    onPrimary = SloBlueDeep,
    primaryContainer = Color(0xFF123F7F),
    onPrimaryContainer = Color(0xFFD9E6FA),
    secondary = Color(0xFFFF8A9A),
    tertiary = Color(0xFF5FD6A5),
    tertiaryContainer = Color(0xFF0E4D37),
    onTertiaryContainer = Color(0xFFD3F4E6),
    background = Color(0xFF0B1220),
    surface = Color(0xFF131C2E),
    surfaceVariant = Color(0xFF1C273D),
)

private val base = Typography()
private val typography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun LaniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) dark else light,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}
