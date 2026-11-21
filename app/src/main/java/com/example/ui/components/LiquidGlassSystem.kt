package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.navigation.Screen
import com.example.ui.theme.*

/**
 * Exact measured bottom clearance occupied by the floating Liquid Glass dock + system navigation bar.
 * Automatically becomes 0.dp while the soft keyboard (IME) is open so inputs can sit directly above the keyboard.
 */
val LocalBottomDockPadding = compositionLocalOf { 104.dp }

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
                surfaceFill = Color(0xFF101C28).copy(alpha = 0.80f),
                elevatedFill = Color(0xFF142434).copy(alpha = 0.92f),
                dockFill = Color(0xFF0E1A26).copy(alpha = 0.90f),
                drawerFill = Color(0xFF0B1520).copy(alpha = 0.95f),
                topSheen = Color.White.copy(alpha = 0.10f),
                bottomSheen = cs.secondary.copy(alpha = 0.04f),
                rimTopLeft = Color.White.copy(alpha = 0.34f),
                rimMid = cs.primary.copy(alpha = 0.30f),
                rimBottomRight = Color.White.copy(alpha = 0.12f),
                activeLensFill = Color.White.copy(alpha = 0.14f),
                activeLensGlow = cs.primary.copy(alpha = 0.22f),
                shadowElevation = 0.dp
            )
            AppThemeMode.LIQUID_GLASS_LIGHT -> LiquidGlassPalette(
                surfaceFill = Color(0xFFFFFFFF).copy(alpha = 0.84f),
                elevatedFill = Color(0xFFFFFFFF).copy(alpha = 0.94f),
                dockFill = Color(0xFFFBFEFF).copy(alpha = 0.92f),
                drawerFill = Color(0xFFF6FBFE).copy(alpha = 0.96f),
                topSheen = Color.White.copy(alpha = 0.65f),
                bottomSheen = cs.secondary.copy(alpha = 0.04f),
                rimTopLeft = Color.White.copy(alpha = 0.98f),
                rimMid = cs.primary.copy(alpha = 0.24f),
                rimBottomRight = Color.White.copy(alpha = 0.65f),
                activeLensFill = cs.primary.copy(alpha = 0.14f),
                activeLensGlow = cs.primary.copy(alpha = 0.16f),
                shadowElevation = 0.dp
            )
            AppThemeMode.AURORA_PRISM_GLASS -> LiquidGlassPalette(
                surfaceFill = Color(0xFF171A38).copy(alpha = 0.82f),
                elevatedFill = Color(0xFF1D224A).copy(alpha = 0.93f),
                dockFill = Color(0xFF131632).copy(alpha = 0.90f),
                drawerFill = Color(0xFF10122B).copy(alpha = 0.95f),
                topSheen = Color.White.copy(alpha = 0.12f),
                bottomSheen = AuroraOrbPrimary.copy(alpha = 0.07f),
                rimTopLeft = Color.White.copy(alpha = 0.36f),
                rimMid = AuroraOrbSecondary.copy(alpha = 0.34f),
                rimBottomRight = AuroraOrbAccent.copy(alpha = 0.22f),
                activeLensFill = AuroraOrbPrimary.copy(alpha = 0.22f),
                activeLensGlow = AuroraOrbSecondary.copy(alpha = 0.22f),
                shadowElevation = 0.dp
            )
            AppThemeMode.ISLAMIC_EMERALD -> LiquidGlassPalette(
                surfaceFill = Color(0xFFFFFDF8).copy(alpha = 0.88f),
                elevatedFill = Color(0xFFFFFEFB).copy(alpha = 0.95f),
                dockFill = Color(0xFFFFFCF5).copy(alpha = 0.92f),
                drawerFill = Color(0xFFFDF9EE).copy(alpha = 0.96f),
                topSheen = Color.White.copy(alpha = 0.65f),
                bottomSheen = GoldAccentLight.copy(alpha = 0.05f),
                rimTopLeft = GoldAccentLight.copy(alpha = 0.44f),
                rimMid = Color.White.copy(alpha = 0.90f),
                rimBottomRight = IslamicGreenLight.copy(alpha = 0.26f),
                activeLensFill = IslamicGreenLight.copy(alpha = 0.14f),
                activeLensGlow = IslamicGreenLight.copy(alpha = 0.16f),
                shadowElevation = 0.dp
            )
            AppThemeMode.ISLAMIC_NIGHT -> LiquidGlassPalette(
                surfaceFill = Color(0xFF11231B).copy(alpha = 0.84f),
                elevatedFill = Color(0xFF162F24).copy(alpha = 0.93f),
                dockFill = Color(0xFF0E1F17).copy(alpha = 0.91f),
                drawerFill = Color(0xFF0B1913).copy(alpha = 0.95f),
                topSheen = GoldAccentDark.copy(alpha = 0.10f),
                bottomSheen = IslamicGreenDark.copy(alpha = 0.05f),
                rimTopLeft = GoldAccentDark.copy(alpha = 0.40f),
                rimMid = Color.White.copy(alpha = 0.20f),
                rimBottomRight = IslamicGreenDark.copy(alpha = 0.22f),
                activeLensFill = IslamicGreenDark.copy(alpha = 0.20f),
                activeLensGlow = GoldAccentDark.copy(alpha = 0.18f),
                shadowElevation = 0.dp
            )
            AppThemeMode.MEDINA_PEARL -> LiquidGlassPalette(
                surfaceFill = Color(0xFFFFFCFA).copy(alpha = 0.88f),
                elevatedFill = Color(0xFFFFFEFC).copy(alpha = 0.95f),
                dockFill = Color(0xFFFFFAF7).copy(alpha = 0.92f),
                drawerFill = Color(0xFFFDF5F0).copy(alpha = 0.96f),
                topSheen = Color.White.copy(alpha = 0.68f),
                bottomSheen = MedinaOrbPrimary.copy(alpha = 0.06f),
                rimTopLeft = Color.White.copy(alpha = 0.95f),
                rimMid = MedinaOrbPrimary.copy(alpha = 0.32f),
                rimBottomRight = MedinaSecondary.copy(alpha = 0.22f),
                activeLensFill = MedinaPrimary.copy(alpha = 0.14f),
                activeLensGlow = MedinaPrimary.copy(alpha = 0.15f),
                shadowElevation = 0.dp
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
 * Uses GPU-cached radial gradients via drawWithCache (zero continuous 60fps redraws
 * and zero heavy RenderEffect blur) so it stays silky smooth even on low-end devices.
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val themeMode = LocalAppThemeMode.current

    val paletteData = remember(themeMode) {
        when (themeMode) {
            AppThemeMode.LIQUID_GLASS_DARK -> GlassQuad(
                listOf(LiquidDarkBgTop, LiquidDarkBgMid, Color(0xFF081722), LiquidDarkBgBottom),
                LiquidDarkOrbPrimary.copy(alpha = 0.20f),
                LiquidDarkOrbSecondary.copy(alpha = 0.16f),
                LiquidDarkOrbGold.copy(alpha = 0.12f)
            )
            AppThemeMode.LIQUID_GLASS_LIGHT -> GlassQuad(
                listOf(LiquidLightBgTop, LiquidLightBgMid, LiquidLightBgBottom),
                LiquidLightOrbPrimary.copy(alpha = 0.20f),
                LiquidLightOrbSecondary.copy(alpha = 0.20f),
                LiquidLightOrbGold.copy(alpha = 0.16f)
            )
            AppThemeMode.AURORA_PRISM_GLASS -> GlassQuad(
                listOf(AuroraBgTop, AuroraBgMid, Color(0xFF0E1433), AuroraBgBottom),
                AuroraOrbPrimary.copy(alpha = 0.24f),
                AuroraOrbSecondary.copy(alpha = 0.18f),
                AuroraOrbAccent.copy(alpha = 0.14f)
            )
            AppThemeMode.ISLAMIC_EMERALD -> GlassQuad(
                listOf(Color(0xFFF6EFE2), IslamicBackgroundLight, Color(0xFFE7F1EA)),
                IslamicGreenLight.copy(alpha = 0.14f),
                GoldAccentLight.copy(alpha = 0.18f),
                EarthGreenTertiary.copy(alpha = 0.10f)
            )
            AppThemeMode.ISLAMIC_NIGHT -> GlassQuad(
                listOf(Color(0xFF06110C), IslamicBackgroundDark, Color(0xFF0D2018), Color(0xFF060E0A)),
                IslamicGreenDark.copy(alpha = 0.18f),
                GoldAccentDark.copy(alpha = 0.15f),
                TealGlow.copy(alpha = 0.13f)
            )
            AppThemeMode.MEDINA_PEARL -> GlassQuad(
                listOf(MedinaBgTop, MedinaBgMid, MedinaBgBottom),
                MedinaOrbPrimary.copy(alpha = 0.20f),
                MedinaOrbSecondary.copy(alpha = 0.16f),
                MedinaOrbAccent.copy(alpha = 0.12f)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val bgBrush = Brush.verticalGradient(paletteData.first)
                val c1 = Offset(size.width * 0.16f, size.height * 0.12f)
                val c2 = Offset(size.width * 0.86f, size.height * 0.44f)
                val c3 = Offset(size.width * 0.44f, size.height * 0.88f)
                val r1 = (size.width * 0.85f).coerceAtLeast(1f)
                val r2 = (size.width * 0.78f).coerceAtLeast(1f)
                val r3 = (size.width * 0.88f).coerceAtLeast(1f)

                val orbBrush1 = Brush.radialGradient(
                    colors = listOf(paletteData.second, Color.Transparent),
                    center = c1,
                    radius = r1
                )
                val orbBrush2 = Brush.radialGradient(
                    colors = listOf(paletteData.third, Color.Transparent),
                    center = c2,
                    radius = r2
                )
                val orbBrush3 = Brush.radialGradient(
                    colors = listOf(paletteData.fourth, Color.Transparent),
                    center = c3,
                    radius = r3
                )

                onDrawBehind {
                    drawRect(brush = bgBrush)
                    drawCircle(brush = orbBrush1, radius = r1, center = c1)
                    drawCircle(brush = orbBrush2, radius = r2, center = c2)
                    drawCircle(brush = orbBrush3, radius = r3, center = c3)
                }
            }
    ) {
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

    val specularRim = remember(palette) {
        Brush.linearGradient(
            colors = listOf(
                palette.rimTopLeft,
                palette.rimMid,
                palette.rimBottomRight,
                palette.rimTopLeft.copy(alpha = 0.55f)
            )
        )
    }

    val topGlassSheen = remember(palette) {
        Brush.verticalGradient(
            colors = listOf(
                palette.topSheen,
                Color.Transparent,
                palette.bottomSheen
            )
        )
    }

    val moreInteraction = remember { MutableInteractionSource() }
    val morePressed by moreInteraction.collectIsPressedAsState()
    val moreScale by animateFloatAsState(
        targetValue = if (morePressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "more_circle_scale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        val isCompactWidth = maxWidth < 380.dp
        val outerHorizontalPadding = if (isCompactWidth) 10.dp else 14.dp
        val outerVerticalPadding = if (isCompactWidth) 8.dp else 10.dp
        val dockGap = if (isCompactWidth) 7.dp else 10.dp
        val pillInnerPadding = if (isCompactWidth) 4.dp else 6.dp
        val tabMinHeight = if (isCompactWidth) 48.dp else 52.dp
        val iconSize = if (isCompactWidth) 20.dp else 22.dp
        val labelFontSize = if (isCompactWidth) 10.sp else 11.sp
        val moreCircleSize = if (isCompactWidth) 54.dp else 60.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = outerHorizontalPadding, vertical = outerVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(dockGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Main 4-Tab Floating Liquid Glass Pill Capsule
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .testTag("liquid_glass_bottom_bar"),
                shape = RoundedCornerShape(34.dp),
                color = palette.dockFill,
                border = BorderStroke(1.2.dp, specularRim),
                shadowElevation = if (isDark) 8.dp else 5.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(topGlassSheen)
                        .padding(horizontal = pillInnerPadding, vertical = pillInnerPadding)
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
                                targetValue = if (isPressed) 0.94f else 1f,
                                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
                                label = "dock_scale_${screen.route}"
                            )
                            val activeBubbleColor by animateColorAsState(
                                targetValue = if (isSelected) palette.activeLensGlow else Color.Transparent,
                                animationSpec = tween(durationMillis = 160),
                                label = "dock_bubble_${screen.route}"
                            )
                            val tintColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                animationSpec = tween(durationMillis = 160),
                                label = "dock_tint_${screen.route}"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .scale(itemScale)
                                    .defaultMinSize(minHeight = tabMinHeight)
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(activeBubbleColor)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.45f else 0.35f),
                                                shape = RoundedCornerShape(26.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable(
                                        interactionSource = tabInteraction,
                                        indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary)
                                    ) { onSelect(screen) }
                                    .padding(horizontal = if (isCompactWidth) 2.dp else 4.dp, vertical = 5.dp)
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
                                        modifier = Modifier.size(iconSize)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = screen.title,
                                        fontSize = labelFontSize,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = tintColor,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Detached Right-Side Circular Liquid Glass "More" Button
            Surface(
                modifier = Modifier
                    .size(moreCircleSize)
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
                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.32f else 0.22f)
                } else {
                    palette.dockFill
                },
                border = BorderStroke(1.2.dp, specularRim),
                shadowElevation = if (isDark) 8.dp else 5.dp
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
                            modifier = Modifier.size(iconSize)
                        )
                        Text(
                            text = "More",
                            fontSize = if (isCompactWidth) 8.5.sp else 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDrawerOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
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
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(150)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(120)),
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
                shape = RoundedCornerShape(28.dp),
                color = palette.drawerFill,
                border = BorderStroke(
                    width = 1.1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            palette.rimTopLeft,
                            palette.rimMid,
                            palette.rimBottomRight
                        )
                    )
                ),
                shadowElevation = 10.dp
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
                            Text(
                                text = "ہدایہ کے تمام فیچرز — ہر آپشن کے نیچے اردو میں اس کا مطلب اور استعمال کا طریقہ دیا گیا ہے",
                                fontFamily = AmiriArabicFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        }

                        // Explore & Islamic Tools
                        item {
                            DrawerSectionLabel("Explore & Knowledge • علم اور تلاش")
                        }
                        item {
                            LiquidDrawerMenuItem(
                                title = "Islamic Search",
                                subtitle = "Quran, Hadith, Duas & Fiqh",
                                urduTitle = "اسلامی تلاش",
                                urduGuide = "قرآن، حدیث، دعائیں اور فقہ کے مسائل فوراً تلاش کریں — کوئی بھی لفظ لکھیں اور مستند حوالہ پڑھیں۔",
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
                                urduTitle = "اسلامی ٹولز اور سہولیات",
                                urduGuide = "قبلہ رخ، تسبیح کاؤنٹر، احادیث، روزمرہ دعائیں اور رمضان اوقات — اوپر دیے گئے ٹیب پر کلک کر کے استعمال کریں۔",
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
                                urduTitle = "دین ٹریکر (روزمرہ عبادت)",
                                urduGuide = "اپنی روزانہ کی ۵ نمازیں، تلاوت اور اذکار نوٹ کریں — ہر عبادت مکمل ہونے پر ٹِک کریں اور اپنا یومیہ اسکور دیکھیں۔",
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
                            DrawerSectionLabel("Worship & Audio Tools • عبادت اور تلاوت")
                        }
                        item {
                            LiquidDrawerMenuItem(
                                title = "Learn Salah",
                                subtitle = "Step-by-step Wudu, postures & Rakahs",
                                urduTitle = "نماز سیکھیں (مکمل طریقہ)",
                                urduGuide = "وضو، نماز کے ارکان، رکعتیں اور دعائیں قدم بہ قدم سیکھیں — کسی بھی نماز یا مرحلے پر کلک کر کے تفصیل پڑھیں۔",
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
                                urduTitle = "تلاوت کی مشق (آواز چیک)",
                                urduGuide = "کوئی آیت منتخب کر کے مائیک بٹن دبائیں اور تلاوت کریں — ایپ آپ کی آواز سن کر تلفظ کی درستگی بتائے گی۔",
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
                                urduTitle = "آن لائن قرآن آڈیو سنیں",
                                urduGuide = "مشہور قاریوں کی آواز میں قرآن سنیں — سورت یا پارہ منتخب کریں، جہاں روکیں گے وہیں سے دوبارہ شروع ہوگا۔",
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
                            DrawerSectionLabel("Liquid Glass & Islamic Themes • تھیم بدلیں")
                            Text(
                                text = "اپنی پسند کا رنگ اور گلاس ڈیزائن چنیں — کسی بھی تھیم پر کلک کریں تو ایپ کا انداز فوراً بدل جائے گا۔",
                                fontFamily = AmiriArabicFontFamily,
                                fontSize = 11.5.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
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

                        // Settings & Multi-AI API Keys
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                            Spacer(modifier = Modifier.height(6.dp))
                            LiquidDrawerMenuItem(
                                title = "Settings & Multi-AI Keys",
                                subtitle = if (hasGeminiKey) "AI connected • Adhan, Fonts & About" else "Add Gemini / Grok / OpenAI / Claude keys",
                                urduTitle = "سیٹنگز اور ملٹی اے آئی کیز",
                                urduGuide = "اذان کی آواز، نماز کے اوقات، قرآن کا فونٹ، لوکیشن اور ملٹی اے آئی کیز یہاں سے سیٹ اور محفوظ کریں۔",
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
    urduTitle: String,
    urduGuide: String,
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
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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

            Spacer(modifier = Modifier.height(7.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            Spacer(modifier = Modifier.height(5.dp))

            // Urdu Title + Clear Meaning & How-to-Use Guide
            Text(
                text = urduTitle,
                fontFamily = AmiriArabicFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = urduGuide,
                fontFamily = AmiriArabicFontFamily,
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                softWrap = true,
                modifier = Modifier.fillMaxWidth()
            )
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
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "glass_icon_btn_scale"
    )

    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary),
                onClick = onClick
            ),
        shape = CircleShape,
        color = if (isDark) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.82f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) Color.White.copy(alpha = 0.24f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        ),
        shadowElevation = 0.dp
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
            shadowElevation = if (isDark) 10.dp else 6.dp
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
