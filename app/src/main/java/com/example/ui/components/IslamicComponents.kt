package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun IslamicBannerCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    arabicGreeting: String = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
    icon: ImageVector? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val themeMode = LocalAppThemeMode.current
    val gradientColors = when (themeMode) {
        AppThemeMode.LIQUID_GLASS_DARK -> listOf(
            Color(0xFF0C3B36).copy(alpha = 0.84f),
            Color(0xFF0B253A).copy(alpha = 0.86f)
        )
        AppThemeMode.LIQUID_GLASS_LIGHT -> listOf(
            Color(0xFF0B745D).copy(alpha = 0.88f),
            Color(0xFF09527A).copy(alpha = 0.86f)
        )
        AppThemeMode.AURORA_PRISM_GLASS -> listOf(
            Color(0xFF281F5A).copy(alpha = 0.88f),
            Color(0xFF0F3554).copy(alpha = 0.88f)
        )
        AppThemeMode.ISLAMIC_EMERALD -> listOf(
            IslamicGreenLight.copy(alpha = 0.94f),
            Color(0xFF1B644C).copy(alpha = 0.92f)
        )
        AppThemeMode.ISLAMIC_NIGHT -> listOf(
            Color(0xFF103A2D).copy(alpha = 0.90f),
            Color(0xFF1D3224).copy(alpha = 0.90f)
        )
        AppThemeMode.MEDINA_PEARL -> listOf(
            Color(0xFF8C3F33).copy(alpha = 0.90f),
            Color(0xFF2B634B).copy(alpha = 0.88f)
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.52f),
                    GoldAccentDark.copy(alpha = 0.58f),
                    Color.White.copy(alpha = 0.16f)
                )
            )
        ),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors = gradientColors))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.08f)
                        )
                    )
                )
                .padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = arabicGreeting,
                    fontFamily = AmiriArabicFontFamily,
                    color = GoldAccentDark,
                    fontSize = 21.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    softWrap = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (icon != null) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(end = 6.dp)
                                )
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                softWrap = true
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.90f),
                            softWrap = true
                        )
                    }

                    if (trailingContent != null) {
                        trailingContent()
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                softWrap = true
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = true
                )
            }
        }

        if (actionText != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Multi-layered iOS 26 Liquid Glass Card with adaptive translucent fill,
 * optical top sheen, diagonal crystal reflection, specular rim border,
 * and guaranteed safe internal padding so text and Arabic diacritics never clip.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val palette = rememberLiquidGlassPalette()
    val isDark = LocalAppThemeMode.current.isDark

    val rimBrush = remember(palette, borderColor, isDark) {
        Brush.linearGradient(
            colors = listOf(
                palette.rimTopLeft,
                borderColor,
                palette.rimBottomRight,
                palette.rimTopLeft.copy(alpha = if (isDark) 0.22f else 0.65f)
            )
        )
    }

    val sheenBrush = remember(palette, backgroundColor, isDark) {
        Brush.verticalGradient(
            colors = listOf(
                palette.topSheen,
                backgroundColor.copy(alpha = if (isDark) 0.04f else 0.02f),
                palette.bottomSheen
            )
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = palette.surfaceFill,
        border = BorderStroke(width = 1.dp, brush = rimBrush),
        shadowElevation = palette.shadowElevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(sheenBrush)
                .padding(contentPadding)
        ) {
            content()
        }
    }
}

@Composable
fun RealisticIslamicNavVisual(
    route: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val iconColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val glowBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(glowBg),
        contentAlignment = Alignment.Center
    ) {
        when (route) {
            "home" -> Icon(
                imageVector = if (isSelected) Icons.Filled.Mosque else Icons.Outlined.Mosque,
                contentDescription = "Home",
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            "quran" -> Icon(
                imageVector = if (isSelected) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = "Holy Quran",
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            "prayer" -> Icon(
                imageVector = if (isSelected) Icons.Filled.AccessTimeFilled else Icons.Outlined.AccessTime,
                contentDescription = "Prayer Times",
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            "ai_assistant" -> Icon(
                imageVector = if (isSelected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                contentDescription = "Hidayah AI",
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            "discover" -> Icon(
                imageVector = if (isSelected) Icons.Filled.Explore else Icons.Outlined.Explore,
                contentDescription = "Discover",
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            "tracker" -> Icon(
                imageVector = if (isSelected) Icons.Filled.TaskAlt else Icons.Outlined.CheckCircleOutline,
                contentDescription = "Deen Tracker",
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            "settings" -> Icon(
                imageVector = if (isSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            else -> Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun CategoryChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalAppThemeMode.current.isDark
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            if (isDark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.65f)
        },
        label = "chip_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "chip_fg"
    )

    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                Color.White.copy(alpha = 0.45f)
            } else {
                Color.White.copy(alpha = if (isDark) 0.20f else 0.85f)
            }
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                softWrap = false
            )
        }
    }
}
