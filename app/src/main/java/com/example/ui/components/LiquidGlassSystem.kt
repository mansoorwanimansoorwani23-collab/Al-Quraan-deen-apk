package com.example.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.navigation.Screen
import com.example.ui.theme.*

/**
 * Shared visual tokens computed dynamically for the active Liquid Glass / Islamic theme.
 */
@Composable
fun rememberLiquidGlassPalette(): LiquidGlassPalette {
    val mode = LocalAppThemeMode.current
    val cs = MaterialTheme.colorScheme
    return remember(mode, cs) {
        when (mode) {
            AppThemeMode.LIQUID_GLASS_DARK -> LiquidGlassPalette(
                surfaceFill = Color(0xFF0E1824).copy(alpha = 0.62f),
                elevatedFill = Color(0xFF132232).copy(alpha = 0.78f),
                dockFill = Color(0xFF0C1520).copy(alpha = 0.80f),
                drawerFill = Color(0xFF09121C).copy(alpha = 0.90f),
                topSheen = Color.White.copy(alpha = 0.14f),
                bottomSheen = cs.secondary.copy(alpha = 0.06f),
                rimTopLeft = Color.White.copy(alpha = 0.42f),
                rimMid = cs.primary.copy(alpha = 0.34f),
                rimBottomRight = Color.White.copy(alpha = 0.14f),
                activeLensFill = Color.White.copy(alpha = 0.14f),
                activeLensGlow = cs.primary.copy(alpha = 0.24f),
                shadowElevation = 10.dp
            )
            AppThemeMode.LIQUID_GLASS_LIGHT -> LiquidGlassPalette(
                surfaceFill = Color(0xFFFFFFFF).copy(alpha = 0.68f),
                elevatedFill = Color(0xFFFFFFFF).copy(alpha = 0.84f),
                dockFill = Color(0xFFFBFEFF).copy(alpha = 0.80f),
                drawerFill = Color(0xFFF6FBFE).copy(alpha = 0.92f),
                topSheen = Color.White.copy(alpha = 0.82f),
                bottomSheen = cs.secondary.copy(alpha = 0.05f),
                rimTopLeft = Color.White.copy(alpha = 0.98f),
                rimMid = cs.primary.copy(alpha = 0.26f),
                rimBottomRight = Color.White.copy(alpha = 0.60f),
                activeLensFill = cs.primary.copy(alpha = 0.14f),
                activeLensGlow = Color.White.copy(alpha = 0.65f),
                shadowElevation = 6.dp
            )
            AppThemeMode.AURORA_PRISM_GLASS -> LiquidGlassPalette(
                surfaceFill = Color(0xFF141734).copy(alpha = 0.64f),
                elevatedFill = Color(0xFF1B1F46).copy(alpha = 0.80f),
                dockFill = Color(0xFF11142E).copy(alpha = 0.82f),
                drawerFill = Color(0xFF0E1028).copy(alpha = 0.91f),
                topSheen = Color.White.copy(alpha = 0.16f),
                bottomSheen = AuroraOrbPrimary.copy(alpha = 0.10f),
                rimTopLeft = Color.White.copy(alpha = 0.44f),
                rimMid = AuroraOrbSecondary.copy(alpha = 0.42f),
                rimBottomRight = AuroraOrbAccent.copy(alpha = 0.28f),
                activeLensFill = AuroraOrbPrimary.copy(alpha = 0.24f),
                activeLensGlow = AuroraOrbSecondary.copy(alpha = 0.24f),
                shadowElevation = 12.dp
            )
            AppThemeMode.ISLAMIC_EMERALD -> LiquidGlassPalette(
                surfaceFill = Color(0xFFFFFCF6).copy(alpha = 0.78f),
                elevatedFill = Color(0xFFFFFDF9).copy(alpha = 0.90f),
                dockFill = Color(0xFFFFFCF3).copy(alpha = 0.84f),
                drawerFill = Color(0xFFFDF8EC).copy(alpha = 0.93f),
                topSheen = Color.White.copy(alpha = 0.78f),
                bottomSheen = GoldAccentLight.copy(alpha = 0.07f),
                rimTopLeft = GoldAccentLight.copy(alpha = 0.52f),
                rimMid = Color.White.copy(alpha = 0.90f),
                rimBottomRight = IslamicGreenLight.copy(alpha = 0.30f),
                activeLensFill = IslamicGreenLight.copy(alpha = 0.14f),
                activeLensGlow = GoldAccentLight.copy(alpha = 0.18f),
                shadowElevation = 6.dp
            )
            AppThemeMode.ISLAMIC_NIGHT -> LiquidGlassPalette(
                surfaceFill = Color(0xFF0F2019).copy(alpha = 0.70f),
                elevatedFill = Color(0xFF152C22).copy(alpha = 0.84f),
                dockFill = Color(0xFF0C1B14).copy(alpha = 0.84f),
                drawerFill = Color(0xFF0A1711).copy(alpha = 0.92f),
                topSheen = GoldAccentDark.copy(alpha = 0.12f),
                bottomSheen = IslamicGreenDark.copy(alpha = 0.06f),
                rimTopLeft = GoldAccentDark.copy(alpha = 0.46f),
                rimMid = Color.White.copy(alpha = 0.22f),
                rimBottomRight = IslamicGreenDark.copy(alpha = 0.26f),
                activeLensFill = IslamicGreenDark.copy(alpha = 0.22f),
                activeLensGlow = GoldAccentDark.copy(alpha = 0.18f),
                shadowElevation = 10.dp
            )
            AppThemeMode.MEDINA_PEARL -> LiquidGlassPalette(
                surfaceFill = Color(0xFFFFFAF7).copy(alpha = 0.76f),
                elevatedFill = Color(0xFFFFFCFA).copy(alpha = 0.89f),
                dockFill = Color(0xFFFFF8F5).copy(alpha = 0.84f),
                drawerFill = Color(0xFFFDF3EE).copy(alpha = 0.93f),
                topSheen = Color.White.copy(alpha = 0.82f),
                bottomSheen = MedinaOrbPrimary.copy(alpha = 0.08f),
                rimTopLeft = Color.White.copy(alpha = 0.96f),
                rimMid = MedinaOrbPrimary.copy(alpha = 0.36f),
                rimBottomRight = MedinaSecondary.copy(alpha = 0.24f),
                activeLensFill = MedinaPrimary.copy(alpha = 0.14f),
                activeLensGlow = MedinaOrbPrimary.copy(alpha = 0.22f),
                shadowElevation = 6.dp
            )
        }
    }
}

data class LiquidGlassPalette(
    val surfaceFill: Color,
    val elevatedFill: Color,
    val dockFill: Color,
    val drawerFill: Color,
    val topSheen: Color,
    val bottomSheen: Color,
    val rimTopLeft: Color,
    val rimMid: Color,
    val rimBottomRight: Color,
    val activeLensFill: Color,
    val activeLensGlow: Color,
    val shadowElevation: Dp
)

/**
 * Full-screen iOS 26 Liquid Glass Ambient Light Backdrop.
 * Renders dynamic multi-stop gradient + slowly breathing luminous orbs (hardware-blurred on Android 12+)
 * so every translucent glass surface refracts rich color and depth.
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val themeMode = LocalAppThemeMode.current
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_glass_ambient")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_drift"
    )

    val (bgColors, orb1, orb2, orb3) = when (themeMode) {
        AppThemeMode.LIQUID_GLASS_DARK -> GlassQuad(
            listOf(LiquidDarkBgTop, LiquidDarkBgMid, Color(0xFF071620), LiquidDarkBgBottom),
            LiquidDarkOrbPrimary.copy(alpha = 0.28f),
            LiquidDarkOrbSecondary.copy(alpha = 0.22f),
            LiquidDarkOrbGold.copy(alpha = 0.16f)
        )
        AppThemeMode.LIQUID_GLASS_LIGHT -> GlassQuad(
            listOf(LiquidLightBgTop, LiquidLightBgMid, LiquidLightBgBottom),
            LiquidLightOrbPrimary.copy(alpha = 0.28f),
            LiquidLightOrbSecondary.copy(alpha = 0.26f),
            LiquidLightOrbGold.copy(alpha = 0.22f)
        )
        AppThemeMode.AURORA_PRISM_GLASS -> GlassQuad(
            listOf(AuroraBgTop, AuroraBgMid, Color(0xFF0E1433), AuroraBgBottom),
            AuroraOrbPrimary.copy(alpha = 0.34f),
            AuroraOrbSecondary.copy(alpha = 0.24f),
            AuroraOrbAccent.copy(alpha = 0.20f)
        )
        AppThemeMode.ISLAMIC_EMERALD -> GlassQuad(
            listOf(Color(0xFFF6EEDF), IslamicBackgroundLight, Color(0xFFE5F0E8)),
            IslamicGreenLight.copy(alpha = 0.18f),
            GoldAccentLight.copy(alpha = 0.24f),
            EarthGreenTertiary.copy(alpha = 0.14f)
        )
        AppThemeMode.ISLAMIC_NIGHT -> GlassQuad(
            listOf(Color(0xFF05100B), IslamicBackgroundDark, Color(0xFF0C1E16), Color(0xFF050D09)),
            IslamicGreenDark.copy(alpha = 0.24f),
            GoldAccentDark.copy(alpha = 0.20f),
            TealGlow.copy(alpha = 0.18f)
        )
        AppThemeMode.MEDINA_PEARL -> GlassQuad(
            listOf(MedinaBgTop, MedinaBgMid, MedinaBgBottom),
            MedinaOrbPrimary.copy(alpha = 0.26f),
            MedinaOrbSecondary.copy(alpha = 0.22f),
            MedinaOrbAccent.copy(alpha = 0.16f)
        )
    }

    val orbModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.fillMaxSize().blur(44.dp)
    } else {
        Modifier.fillMaxSize()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(bgColors))
    ) {
        Canvas(modifier = orbModifier) {
            val offset1 = Offset(size.width * (0.14f + 0.08f * drift), size.height * (0.10f + 0.05f * drift))
            val offset2 = Offset(size.width * (0.88f - 0.07f * drift), size.height * (0.42f + 0.06f * drift))
            val offset3 = Offset(size.width * (0.42f + 0.09f * drift), size.height * (0.90f - 0.05f * drift))

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb1, Color.Transparent),
                    center = offset1,
                    radius = size.width * 0.78f
                ),
                radius = size.width * 0.78f,
                center = offset1
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb2, Color.Transparent),
                    center = offset2,
                    radius = size.width * 0.72f
                ),
                radius = size.width * 0.72f,
                center = offset2
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb3, Color.Transparent),
                    center = offset3,
                    radius = size.width * 0.80f
                ),
                radius = size.width * 0.80f,
                center = offset3
            )
        }
        content()
    }
}

private data class GlassQuad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * iOS 26 Floating Liquid Glass Dock:
 * - Left / Main translucent pill capsule containing strictly 4 items (Home, Quran, Prayer, AI)
 *   with a spring-animated glowing glass lens bubble on the active tab.
 * - Right detached circular Liquid Glass "More" button (matching the video's detached circle)
 *   that opens the Left-Side ChatGPT / Gemini-style Liquid Glass Sidebar Drawer.
 */
@Composable
fun LiquidGlassFloatingDock(
    items: List<Screen>,
    currentRoute: String,
    isDrawerOpen: Boolean,
    onSelect: (Screen) -> Unit,
    onOpenMoreDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = rememberLiquidGlassPalette()
    val themeMode = LocalAppThemeMode.current
    val isDark = themeMode.isDark

    val specularRim = Brush.linearGradient(
        colors = listOf(
            palette.rimTopLeft,
            palette.rimMid,
            palette.rimBottomRight,
            palette.rimTopLeft.copy(alpha = 0.55f)
        )
    )

    val topGlassSheen = Brush.verticalGradient(
        colors = listOf(
            palette.topSheen,
            Color.Transparent,
            palette.bottomSheen
        )
    )

    val moreInteraction = remember { MutableInteractionSource() }
    val morePressed by moreInteraction.collectIsPressedAsState()
    val moreScale by animateFloatAsState(
        targetValue = if (morePressed) 0.90f else if (isDrawerOpen) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "more_circle_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Main 4-Tab Floating Liquid Glass Pill Capsule
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .testTag("liquid_glass_bottom_bar"),
                shape = RoundedCornerShape(36.dp),
                color = palette.dockFill,
                border = BorderStroke(1.25.dp, specularRim),
                shadowElevation = if (isDark) 16.dp else 10.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(topGlassSheen)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEach { screen ->
                            val isSelected = currentRoute == screen.route
                            val tabInteraction = remember { MutableInteractionSource() }
                            val isPressed by tabInteraction.collectIsPressedAsState()

                            val itemScale by animateFloatAsState(
                                targetValue = when {
                                    isPressed -> 0.92f
                                    isSelected -> 1.03f
                                    else -> 1f
                                },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "dock_scale_${screen.route}"
                            )
                            val activeBubbleColor by animateColorAsState(
                                targetValue = if (isSelected) palette.activeLensGlow else Color.Transparent,
                                label = "dock_bubble_${screen.route}"
                            )
                            val tintColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                label = "dock_tint_${screen.route}"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .scale(itemScale)
                                    .defaultMinSize(minHeight = 54.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(activeBubbleColor)
                                    .then(
                                        if (isSelected) {
                                            Modifier
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = if (isDark) 0.22f else 0.72f),
                                                            Color.Transparent
                                                        )
                                                    )
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    brush = Brush.linearGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = if (isDark) 0.50f else 0.95f),
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
                                                            Color.White.copy(alpha = 0.14f)
                                                        )
                                                    ),
                                                    shape = RoundedCornerShape(28.dp)
                                                )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable(
                                        interactionSource = tabInteraction,
                                        indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary)
                                    ) { onSelect(screen) }
                                    .padding(horizontal = 4.dp, vertical = 6.dp)
                                    .testTag("nav_item_${screen.route}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) screen.filledIcon else screen.outlinedIcon,
                                        contentDescription = screen.title,
                                        tint = tintColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = screen.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = tintColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Detached Right-Side Circular Liquid Glass "More" Button (like the video's detached circle)
            Surface(
                modifier = Modifier
                    .size(62.dp)
                    .scale(moreScale)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = moreInteraction,
                        indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary),
                        onClick = onOpenMoreDrawer
                    )
                    .testTag("dock_more_drawer_button"),
                shape = CircleShape,
                color = if (isDrawerOpen) {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.34f else 0.22f)
                } else {
                    palette.dockFill
                },
                border = BorderStroke(
                    width = 1.3.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.52f else 0.98f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.16f)
                        )
                    )
                ),
                shadowElevation = if (isDark) 16.dp else 10.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(topGlassSheen),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isDrawerOpen) Icons.Filled.Close else Icons.Filled.GridView,
                            contentDescription = "More options",
                            tint = if (isDrawerOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "More",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDrawerOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * ChatGPT / Gemini-Style Left-Side Slide-Out Liquid Glass Sidebar Drawer.
 * Opens smoothly from the left edge whenever the user taps the detached right-side "More"
 * glass circle (or top-left menu icon), holding all remaining Hidayah features & instant themes.
 */
@Composable
fun LiquidGlassLeftDrawerOverlay(
    visible: Boolean,
    currentRoute: String,
    cityName: String,
    hijriDateText: String?,
    hasGeminiKey: Boolean,
    onDismiss: () -> Unit,
    onSelectRoute: (String) -> Unit,
    onSelectTheme: (AppThemeMode) -> Unit
) {
    val palette = rememberLiquidGlassPalette()
    val activeTheme = LocalAppThemeMode.current
    val isDark = activeTheme.isDark

    Box(modifier = Modifier.fillMaxSize()) {
        // Frosted Scrim behind the left sidebar
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isDark) 0.56f else 0.34f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                    .testTag("liquid_drawer_scrim")
            )
        }

        // Left-Side Floating Liquid Glass Panel (slides from left like ChatGPT / Gemini)
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(tween(180)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(210)
            ) + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 285.dp, max = 330.dp)
                    .fillMaxWidth(0.82f)
                    .padding(top = 8.dp, bottom = 8.dp, start = 8.dp, end = 6.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .testTag("liquid_glass_left_drawer"),
                shape = RoundedCornerShape(30.dp),
                color = palette.drawerFill,
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            palette.rimTopLeft,
                            palette.rimMid,
                            palette.rimBottomRight
                        )
                    )
                ),
                shadowElevation = 20.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    palette.topSheen,
                                    Color.Transparent,
                                    palette.bottomSheen
                                )
                            )
                        )
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 18.dp, bottom = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Brand Header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
                                            .border(
                                                1.dp,
                                                Color.White.copy(alpha = if (isDark) 0.30f else 0.85f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Mosque,
                                            contentDescription = "Hidayah",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Hidayah",
                                            fontFamily = BrandDisplayFontFamily,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = listOfNotNull(hijriDateText, cityName.takeIf { it.isNotBlank() }).joinToString(" • "),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                LiquidGlassIconButton(
                                    onClick = onDismiss,
                                    icon = Icons.Filled.Close,
                                    contentDescription = "Close Menu",
                                    size = 38.dp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        }

                        // Explore & Islamic Tools
                        item {
                            DrawerSectionLabel("Explore & Knowledge")
                        }
                        item {
                            LiquidDrawerMenuItem(
                                title = "Islamic Search",
                                subtitle = "Quran, Hadith, Duas & Fiqh",
                                icon = Icons.Filled.Search,
                                isActive = currentRoute == Screen.Search.route,
                                testTag = "drawer_item_search",
                                onClick = {
                                    onDismiss()
                                    onSelectRoute(Screen.Search.route)
                                }
                            )
                        }
                        item {
                            LiquidDrawerMenuItem(
                                title = "Discover Tools",
                                subtitle = "Qibla, Tasbeeh, Hadith, Duas, Ramadan",
                                icon = Icons.Filled.Explore,
                                isActive = currentRoute == Screen.Discover.route,
                                testTag = "drawer_item_discover",
                                onClick = {
                                    onDismiss()
                                    onSelectRoute(Screen.Discover.route)
                                }
                            )
                        }
                        item {
                            LiquidDrawerMenuItem(
                                title = "Deen Tracker",
                                subtitle = "Daily Salah, Quran & Azkar log",
                                icon = Icons.Filled.TaskAlt,
                                isActive = currentRoute == Screen.Tracker.route,
                                testTag = "drawer_item_tracker",
                                onClick = {
                                    onDismiss()
                                    onSelectRoute(Screen.Tracker.route)
                                }
                            )
                        }

                        // Worship & Learning
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            DrawerSectionLabel("Worship & Audio Tools")
                        }
                        item {
                            LiquidDrawerMenuItem(
                                title = "Learn Salah",
                                subtitle = "Step-by-step Wudu, postures & Rakahs",
                                icon = Icons.Filled.School,
                                isActive = currentRoute == Screen.LearnSalah.route,
                                testTag = "drawer_item_learn_salah",
                                onClick = {
                                    onDismiss()
                                    onSelectRoute(Screen.LearnSalah.route)
                                }
                            )
                        }
                        item {
                            LiquidDrawerMenuItem(
                                title = "Recitation Practice",
                                subtitle = "Recite aloud & check pronunciation",
                                icon = Icons.Filled.Mic,
                                isActive = currentRoute == Screen.RecitationPractice.route,
                                testTag = "drawer_item_recitation",
                                onClick = {
                                    onDismiss()
                                    onSelectRoute(Screen.RecitationPractice.route)
                                }
                            )
                        }
                        item {
                            LiquidDrawerMenuItem(
                                title = "Online Quran Audio",
                                subtitle = "Stream reciters & resume position",
                                icon = Icons.Filled.Headphones,
                                isActive = currentRoute == Screen.OnlineQuranAudio.route,
                                testTag = "drawer_item_online_audio",
                                onClick = {
                                    onDismiss()
                                    onSelectRoute(Screen.OnlineQuranAudio.route)
                                }
                            )
                        }

                        // Liquid Glass & Islamic Theme Switcher right inside Drawer
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                            Spacer(modifier = Modifier.height(6.dp))
                            DrawerSectionLabel("Liquid Glass & Islamic Themes")
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(AppThemeMode.entries) { mode ->
                                    val selected = activeTheme == mode
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onSelectTheme(mode) }
                                            .testTag("drawer_theme_${mode.id.lowercase()}"),
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        },
                                        border = BorderStroke(
                                            width = if (selected) 1.4.dp else 0.8.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.22f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(mode.swatchPrimary, mode.swatchSecondary)
                                                        )
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = mode.shortName,
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Settings & Gemini API Key
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                            Spacer(modifier = Modifier.height(6.dp))
                            LiquidDrawerMenuItem(
                                title = "Settings & Gemini API Key",
                                subtitle = if (hasGeminiKey) "Gemini AI connected • Adhan, Fonts & About" else "Add Gemini API key, Adhan, Fonts & About",
                                icon = Icons.Filled.Settings,
                                isActive = currentRoute == Screen.Settings.route,
                                badgeText = if (hasGeminiKey) "AI Ready" else "Setup AI",
                                testTag = "overflow_settings_item",
                                onClick = {
                                    onDismiss()
                                    onSelectRoute(Screen.Settings.route)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.9.sp,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun LiquidDrawerMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    badgeText: String? = null,
    testTag: String,
    onClick: () -> Unit
) {
    val isDark = LocalAppThemeMode.current.isDark
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "drawer_item_scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary),
                onClick = onClick
            )
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
        } else {
            if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.56f)
        },
        border = BorderStroke(
            width = if (isActive) 1.15.dp else 0.8.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                Color.White.copy(alpha = if (isDark) 0.14f else 0.72f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Circular iOS 26 Liquid Glass Icon Button with specular rim and tactile spring bounce.
 */
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val isDark = LocalAppThemeMode.current.isDark
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "glass_icon_btn_scale"
    )

    Surface(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary),
                onClick = onClick
            ),
        shape = CircleShape,
        color = if (isDark) Color.White.copy(alpha = 0.11f) else Color.White.copy(alpha = 0.72f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isDark) 0.42f else 0.98f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                    Color.White.copy(alpha = 0.12f)
                )
            )
        ),
        shadowElevation = if (isDark) 6.dp else 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * iOS 26 Floating Liquid Glass Segmented Tab Control (like the video's frosted glass pill tabs).
 */
@Composable
fun LiquidGlassSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = rememberLiquidGlassPalette()
    val isDark = LocalAppThemeMode.current.isDark

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.52f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    palette.rimTopLeft.copy(alpha = 0.65f),
                    palette.rimMid.copy(alpha = 0.35f),
                    palette.rimBottomRight
                )
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { idx, label ->
                val isSelected = selectedIndex == idx
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "seg_bg_$idx"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "seg_txt_$idx"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 38.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(bgColor)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    0.9.dp,
                                    Color.White.copy(alpha = if (isDark) 0.35f else 0.65f),
                                    RoundedCornerShape(18.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onSelect(idx) }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag("segmented_tab_$idx"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Translucent Liquid Glass Outlined Text Field for Search, Quran Filter, AI & API Keys.
 */
@Composable
fun LiquidGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val isDark = LocalAppThemeMode.current.isDark
    val containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.68f)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else null,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            unfocusedBorderColor = Color.White.copy(alpha = if (isDark) 0.22f else 0.78f),
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/**
 * Translucent iOS 26 Liquid Glass Dialog replacing plain opaque Material AlertDialogs.
 */
@Composable
fun LiquidGlassDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    val palette = rememberLiquidGlassPalette()
    val isDark = LocalAppThemeMode.current.isDark

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(28.dp),
            color = palette.elevatedFill,
            border = BorderStroke(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        palette.rimTopLeft,
                        palette.rimMid,
                        palette.rimBottomRight
                    )
                )
            ),
            shadowElevation = if (isDark) 20.dp else 12.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                palette.topSheen,
                                Color.Transparent,
                                palette.bottomSheen
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp)
                ) {
                    ProvideTextStyle(
                        value = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        title()
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    ProvideTextStyle(
                        value = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        text()
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissButton != null) {
                            dismissButton()
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}

/**
 * Reusable Liquid Glass Top App Bar container with frosted translucency & subtle rim.
 */
@Composable
fun LiquidGlassTopBarSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val palette = rememberLiquidGlassPalette()
    val isDark = LocalAppThemeMode.current.isDark
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = palette.dockFill.copy(alpha = if (isDark) 0.66f else 0.72f),
        border = BorderStroke(
            width = 0.85.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isDark) 0.16f else 0.78f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                    Color.White.copy(alpha = if (isDark) 0.16f else 0.78f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.topSheen.copy(alpha = if (isDark) 0.08f else 0.45f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            content()
        }
    }
}
