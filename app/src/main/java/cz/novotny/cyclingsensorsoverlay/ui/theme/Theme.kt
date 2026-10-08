package cz.novotny.cyclingsensorsoverlay.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class OverlayColorScheme(
    val power: Color = PowerYellow,
    val speed: Color = SpeedBlue,
    val cadence: Color = CadenceGreen,
    val heartRate: Color = HeartRateRed,
    val threatSafeBg: Color = ThreatSafeBg,
    val threatApproachingBg: Color = ThreatApproachingBg,
    val threatHighSpeedBg: Color = ThreatHighSpeedBg,
    val threatSafeBorder: Color = ThreatSafeBorder,
    val threatApproachingBorder: Color = ThreatApproachingBorder,
    val threatHighSpeedBorder: Color = ThreatHighSpeedBorder,
    val threatSafeBanner: Color = ThreatSafeBanner,
    val threatApproachingBanner: Color = ThreatApproachingBanner,
    val threatHighSpeedBanner: Color = ThreatHighSpeedBanner,
    val earBackground: Color = EarBackground,
    val sensorCardBackground: Color = SensorCardBackground,
    val overlayDivider: Color = OverlayDividerColor,
    val radarBackground: Color = RadarBackground,
    val radarBorderClear: Color = RadarBorderClear,
    val riderNormal: Color = RiderNormalGreen,
    val threatDotApproaching: Color = ThreatDotApproachingYellow,
    val threatPillBackground: Color = ThreatPillBackground
)

val LocalOverlayColorScheme = staticCompositionLocalOf { OverlayColorScheme() }

val MaterialTheme.overlayColors: OverlayColorScheme
    @Composable
    @ReadOnlyComposable
    get() = LocalOverlayColorScheme.current

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun CyclingSensorsOverlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    overlayColors: OverlayColorScheme = OverlayColorScheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalOverlayColorScheme provides overlayColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
