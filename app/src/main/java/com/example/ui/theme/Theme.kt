package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(
    val id: String,
    val displayName: String,
    val shortName: String,
    val subtitle: String,
    val isDark: Boolean,
    val isIslamicStyle: Boolean,
    val swatchPrimary: Color,
    val swatchSecondary: Color
) {
    LIQUID_GLASS_DARK(
        id = "LIQUID_GLASS_DARK",
        displayName = "Liquid Glass Dark",
        shortName = "Obsidian Glass",
        subtitle = "iOS 26 obsidian translucent crystal with luminous aqua rims",
        isDark = true,
        isIslamicStyle = false,
        swatchPrimary = LiquidDarkPrimary,
        swatchSecondary = LiquidDarkSecondary
    ),
    LIQUID_GLASS_LIGHT(
        id = "LIQUID_GLASS_LIGHT",
        displayName = "Liquid Glass Light",
        shortName = "Glacier Glass",
        subtitle = "Frosted daylight crystal glass over airy sky mist",
        isDark = false,
        isIslamicStyle = false,
        swatchPrimary = LiquidLightSecondary,
        swatchSecondary = LiquidLightPrimary
    ),
    AURORA_PRISM_GLASS(
        id = "AURORA_PRISM_GLASS",
        displayName = "Aurora Prism Glass",
        shortName = "Aurora Prism",
        subtitle = "Sapphire & violet refractive glass with luminous depth",
        isDark = true,
        isIslamicStyle = false,
        swatchPrimary = AuroraOrbPrimary,
        swatchSecondary = AuroraOrbSecondary
    ),
    ISLAMIC_EMERALD(
        id = "ISLAMIC_EMERALD",
        displayName = "Islamic Emerald & Gold",
        shortName = "Emerald Gold",
        subtitle = "Sacred emerald, antique gold & warm parchment glass",
        isDark = false,
        isIslamicStyle = true,
        swatchPrimary = IslamicGreenLight,
        swatchSecondary = GoldAccentLight
    ),
    ISLAMIC_NIGHT(
        id = "ISLAMIC_NIGHT",
        displayName = "Islamic Night Masjid",
        shortName = "Masjid Night",
        subtitle = "Deep cypress night with lantern gold frosted glass",
        isDark = true,
        isIslamicStyle = true,
        swatchPrimary = IslamicGreenDark,
        swatchSecondary = GoldAccentDark
    ),
    MEDINA_PEARL(
        id = "MEDINA_PEARL",
        displayName = "Medina Rose & Pearl",
        shortName = "Medina Pearl",
        subtitle = "Warm translucent desert pearl & rose-gold glass",
        isDark = false,
        isIslamicStyle = true,
        swatchPrimary = MedinaPrimary,
        swatchSecondary = MedinaSecondary
    );

    companion object {
        fun fromId(id: String, systemDark: Boolean): AppThemeMode {
            return when (id.uppercase()) {
                "LIQUID_GLASS_DARK" -> LIQUID_GLASS_DARK
                "LIQUID_GLASS_LIGHT", "LIGHT" -> LIQUID_GLASS_LIGHT
                "AURORA_PRISM_GLASS", "AURORA" -> AURORA_PRISM_GLASS
                "ISLAMIC_EMERALD", "ISLAMIC" -> ISLAMIC_EMERALD
                "ISLAMIC_NIGHT", "DARK" -> ISLAMIC_NIGHT
                "MEDINA_PEARL", "MEDINA" -> MEDINA_PEARL
                "SYSTEM" -> if (systemDark) LIQUID_GLASS_DARK else LIQUID_GLASS_LIGHT
                else -> LIQUID_GLASS_DARK
            }
        }
    }
}

val LocalAppThemeMode = staticCompositionLocalOf { AppThemeMode.LIQUID_GLASS_DARK }

private val LiquidGlassDarkColorScheme = darkColorScheme(
    primary = LiquidDarkPrimary,
    onPrimary = LiquidDarkOnPrimary,
    primaryContainer = LiquidDarkPrimaryContainer,
    onPrimaryContainer = LiquidDarkOnPrimaryContainer,
    secondary = LiquidDarkSecondary,
    onSecondary = LiquidDarkOnPrimary,
    secondaryContainer = LiquidDarkSecondaryContainer,
    onSecondaryContainer = LiquidDarkOnSecondaryContainer,
    tertiary = LiquidDarkTertiary,
    onTertiary = LiquidDarkOnPrimary,
    background = LiquidDarkBgMid,
    surface = LiquidDarkSurface,
    surfaceVariant = LiquidDarkSurfaceVariant,
    onBackground = LiquidDarkOnSurface,
    onSurface = LiquidDarkOnSurface,
    onSurfaceVariant = LiquidDarkOnSurfaceVariant,
    outline = LiquidDarkOutline
)

private val LiquidGlassLightColorScheme = lightColorScheme(
    primary = LiquidLightPrimary,
    onPrimary = LiquidLightOnPrimary,
    primaryContainer = LiquidLightPrimaryContainer,
    onPrimaryContainer = LiquidLightOnPrimaryContainer,
    secondary = LiquidLightSecondary,
    onSecondary = LiquidLightOnPrimary,
    secondaryContainer = LiquidLightSecondaryContainer,
    onSecondaryContainer = LiquidLightOnSecondaryContainer,
    tertiary = LiquidLightTertiary,
    onTertiary = LiquidLightOnPrimary,
    background = LiquidLightBgMid,
    surface = LiquidLightSurface,
    surfaceVariant = LiquidLightSurfaceVariant,
    onBackground = LiquidLightOnSurface,
    onSurface = LiquidLightOnSurface,
    onSurfaceVariant = LiquidLightOnSurfaceVariant,
    outline = LiquidLightOutline
)

private val AuroraPrismColorScheme = darkColorScheme(
    primary = AuroraPrimary,
    onPrimary = AuroraOnPrimary,
    primaryContainer = AuroraPrimaryContainer,
    onPrimaryContainer = AuroraOnPrimaryContainer,
    secondary = AuroraSecondary,
    onSecondary = AuroraOnPrimary,
    secondaryContainer = AuroraSecondaryContainer,
    onSecondaryContainer = AuroraOnSecondaryContainer,
    tertiary = AuroraTertiary,
    onTertiary = AuroraOnPrimary,
    background = AuroraBgMid,
    surface = AuroraSurface,
    surfaceVariant = AuroraSurfaceVariant,
    onBackground = AuroraOnSurface,
    onSurface = AuroraOnSurface,
    onSurfaceVariant = AuroraOnSurfaceVariant,
    outline = AuroraOutline
)

private val IslamicNightColorScheme = darkColorScheme(
    primary = IslamicGreenDark,
    onPrimary = IslamicGreenOnDark,
    primaryContainer = IslamicContainerDark,
    onPrimaryContainer = IslamicOnContainerDark,
    secondary = GoldAccentDark,
    onSecondary = IslamicGreenOnDark,
    secondaryContainer = GoldContainerDark,
    onSecondaryContainer = GoldOnContainerDark,
    tertiary = EarthGreenDarkTertiary,
    onTertiary = IslamicGreenOnDark,
    background = IslamicBackgroundDark,
    surface = IslamicSurfaceDark,
    surfaceVariant = IslamicSurfaceVariantDark,
    onBackground = IslamicOnSurfaceDark,
    onSurface = IslamicOnSurfaceDark,
    onSurfaceVariant = IslamicOnSurfaceVariantDark,
    outline = IslamicOutlineDark
)

private val IslamicEmeraldColorScheme = lightColorScheme(
    primary = IslamicGreenLight,
    onPrimary = IslamicGreenOnLight,
    primaryContainer = IslamicContainerLight,
    onPrimaryContainer = IslamicOnContainerLight,
    secondary = GoldAccentLight,
    onSecondary = IslamicGreenOnLight,
    secondaryContainer = GoldContainerLight,
    onSecondaryContainer = GoldOnContainerLight,
    tertiary = EarthGreenTertiary,
    onTertiary = IslamicGreenOnLight,
    background = IslamicBackgroundLight,
    surface = IslamicSurfaceLight,
    surfaceVariant = IslamicSurfaceVariantLight,
    onBackground = IslamicOnSurfaceLight,
    onSurface = IslamicOnSurfaceLight,
    onSurfaceVariant = IslamicOnSurfaceVariantLight,
    outline = IslamicOutlineLight
)

private val MedinaPearlColorScheme = lightColorScheme(
    primary = MedinaPrimary,
    onPrimary = MedinaOnPrimary,
    primaryContainer = MedinaPrimaryContainer,
    onPrimaryContainer = MedinaOnPrimaryContainer,
    secondary = MedinaSecondary,
    onSecondary = MedinaOnPrimary,
    secondaryContainer = MedinaSecondaryContainer,
    onSecondaryContainer = MedinaOnSecondaryContainer,
    tertiary = MedinaTertiary,
    onTertiary = MedinaOnPrimary,
    background = MedinaBgMid,
    surface = MedinaSurface,
    surfaceVariant = MedinaSurfaceVariant,
    onBackground = MedinaOnSurface,
    onSurface = MedinaOnSurface,
    onSurfaceVariant = MedinaOnSurfaceVariant,
    outline = MedinaOutline
)

@Composable
fun HidayahTheme(
    themeMode: AppThemeMode = AppThemeMode.LIQUID_GLASS_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.LIQUID_GLASS_DARK -> LiquidGlassDarkColorScheme
        AppThemeMode.LIQUID_GLASS_LIGHT -> LiquidGlassLightColorScheme
        AppThemeMode.AURORA_PRISM_GLASS -> AuroraPrismColorScheme
        AppThemeMode.ISLAMIC_EMERALD -> IslamicEmeraldColorScheme
        AppThemeMode.ISLAMIC_NIGHT -> IslamicNightColorScheme
        AppThemeMode.MEDINA_PEARL -> MedinaPearlColorScheme
    }

    CompositionLocalProvider(LocalAppThemeMode provides themeMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Backwards-compatible aliases
@Composable
fun DeenMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    HidayahTheme(
        themeMode = if (darkTheme) AppThemeMode.LIQUID_GLASS_DARK else AppThemeMode.LIQUID_GLASS_LIGHT,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    HidayahTheme(
        themeMode = if (darkTheme) AppThemeMode.LIQUID_GLASS_DARK else AppThemeMode.LIQUID_GLASS_LIGHT,
        content = content
    )
}
