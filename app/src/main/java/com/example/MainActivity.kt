package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.IslamicDataSource
import com.example.data.model.Surah
import com.example.ui.MainViewModel
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassFloatingDock
import com.example.ui.components.LiquidGlassIconButton
import com.example.ui.components.LiquidGlassLeftDrawerOverlay
import com.example.ui.components.LiquidGlassTopBarSurface
import com.example.ui.components.LocalBottomDockPadding
import com.example.ui.navigation.DiscoverTab
import com.example.ui.navigation.Screen
import com.example.ui.screens.ai.HidayahAiScreen
import com.example.ui.screens.discover.DiscoverScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.prayer.PrayerScreen
import com.example.ui.screens.quran.OnlineQuranAudioScreen
import com.example.ui.screens.quran.QuranRecitationPracticeScreen
import com.example.ui.screens.quran.QuranScreen
import com.example.ui.screens.quran.SurahReaderScreen
import com.example.ui.screens.salah.LearnSalahScreen
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.tracker.TrackerScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.BrandDisplayFontFamily
import com.example.ui.theme.HidayahTheme
import com.example.ui.theme.LocalAppThemeMode

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val userSettings by viewModel.userSettings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val themeMode = AppThemeMode.fromId(userSettings.appTheme, systemDark)

            HidayahTheme(themeMode = themeMode) {
                HidayahMainApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HidayahMainApp(viewModel: MainViewModel) {
    // True navigation backstack: Current Screen -> Back Gesture -> Previous Screen.
    // Exits the app ONLY when the user is on the root Home screen with no previous history.
    val backStack = remember { mutableStateListOf<String>(Screen.Home.route) }
    val currentScreen = backStack.lastOrNull() ?: Screen.Home.route

    var activeDiscoverTab by remember { mutableStateOf(DiscoverTab.QIBLA) }
    var activeSurahForReading by remember { mutableStateOf<Surah?>(null) }
    var activeJuzForReading by remember { mutableStateOf<Int?>(null) }
    var isLeftDrawerOpen by remember { mutableStateOf(false) }

    val homeState by viewModel.homeUiState.collectAsState()
    val themeMode = LocalAppThemeMode.current

    fun navigateTo(route: String) {
        isLeftDrawerOpen = false
        if (currentScreen == route) return
        if (route == Screen.Home.route) {
            backStack.clear()
            backStack.add(Screen.Home.route)
        } else {
            backStack.add(route)
            if (backStack.size > 20) {
                backStack.removeAt(1)
            }
        }
    }

    fun navigateBack() {
        when {
            isLeftDrawerOpen -> {
                isLeftDrawerOpen = false
            }
            activeSurahForReading != null || activeJuzForReading != null -> {
                activeSurahForReading = null
                activeJuzForReading = null
            }
            backStack.size > 1 -> {
                backStack.removeAt(backStack.lastIndex)
            }
            currentScreen != Screen.Home.route -> {
                backStack.clear()
                backStack.add(Screen.Home.route)
            }
        }
    }

    // Intercept system back gesture whenever drawer is open or not on root Home screen
    BackHandler(
        enabled = isLeftDrawerOpen ||
            activeSurahForReading != null ||
            activeJuzForReading != null ||
            backStack.size > 1 ||
            currentScreen != Screen.Home.route
    ) {
        navigateBack()
    }

    // Dedicated full-screen Pure Decorated Offline Quran Reader (no audio/video)
    if (activeSurahForReading != null || activeJuzForReading != null) {
        SurahReaderScreen(
            initialSurah = activeSurahForReading,
            initialJuzNumber = activeJuzForReading,
            viewModel = viewModel,
            onBack = { navigateBack() }
        )
        return
    }

    // Dedicated full-screen secondary routes (accessible via Left-Side Liquid Glass Drawer or shortcuts)
    when (currentScreen) {
        Screen.OnlineQuranAudio.route -> {
            OnlineQuranAudioScreen(
                viewModel = viewModel,
                onBack = { navigateBack() }
            )
            return
        }
        Screen.RecitationPractice.route -> {
            QuranRecitationPracticeScreen(
                viewModel = viewModel,
                onBack = { navigateBack() }
            )
            return
        }
        Screen.LearnSalah.route -> {
            LearnSalahScreen(
                viewModel = viewModel,
                onBack = { navigateBack() }
            )
            return
        }
    }

    // Exactly 4 primary options in the floating Liquid Glass bottom bar
    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Quran,
        Screen.Prayer,
        Screen.AiAssistant
    )

    val density = LocalDensity.current
    val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val isKeyboardVisible = imeBottomDp > navBarBottomDp + 24.dp
    var measuredDockHeightPx by remember { mutableIntStateOf(0) }
    val measuredDockHeightDp = if (measuredDockHeightPx > 0) {
        with(density) { measuredDockHeightPx.toDp() }
    } else {
        84.dp + navBarBottomDp
    }
    val effectiveBottomDockPadding = if (isKeyboardVisible) 0.dp else measuredDockHeightDp

    LiquidGlassBackdrop {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    LiquidGlassTopBarSurface {
                        TopAppBar(
                            navigationIcon = {
                                Box(modifier = Modifier.padding(start = 10.dp, end = 4.dp)) {
                                    LiquidGlassIconButton(
                                        onClick = { isLeftDrawerOpen = true },
                                        icon = Icons.Filled.Menu,
                                        contentDescription = "Open Sidebar Menu",
                                        size = 42.dp,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("top_left_drawer_button")
                                    )
                                }
                            },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f))
                                            .border(
                                                1.dp,
                                                Color.White.copy(alpha = if (themeMode.isDark) 0.28f else 0.85f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Mosque,
                                            contentDescription = "Hidayah Logo",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Hidayah",
                                            fontFamily = BrandDisplayFontFamily,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1
                                        )
                                        homeState.hijriDate?.let { hijri ->
                                            Text(
                                                text = "${hijri.formatShort()} • ${homeState.userSettings.cityName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            },
                            actions = {
                                Row(
                                    modifier = Modifier.padding(end = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LiquidGlassIconButton(
                                        onClick = {
                                            val allModes = AppThemeMode.entries
                                            val nextIdx = (allModes.indexOf(themeMode) + 1) % allModes.size
                                            viewModel.updateAppTheme(allModes[nextIdx].id)
                                        },
                                        icon = Icons.Filled.Palette,
                                        contentDescription = "Cycle Theme",
                                        size = 40.dp,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("top_quick_theme_button")
                                    )
                                    LiquidGlassIconButton(
                                        onClick = { navigateTo(Screen.Settings.route) },
                                        icon = Icons.Filled.Settings,
                                        contentDescription = "Settings",
                                        size = 40.dp,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("top_settings_button")
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent
                            )
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding())
                ) {
                    CompositionLocalProvider(LocalBottomDockPadding provides effectiveBottomDockPadding) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(170)) togetherWith
                                    fadeOut(animationSpec = tween(110))
                            },
                            label = "screen_navigation"
                        ) { screenRoute ->
                            when (screenRoute) {
                                Screen.Home.route -> {
                                    HomeScreen(
                                        viewModel = viewModel,
                                        onNavigateToScreen = { navigateTo(it) },
                                        onNavigateToDiscoverTab = { tab ->
                                            activeDiscoverTab = tab
                                            navigateTo(Screen.Discover.route)
                                        },
                                        onOpenSurah = { surahNum ->
                                            val surah = IslamicDataSource.SURAHS.find { it.number == surahNum } ?: IslamicDataSource.SURAHS[0]
                                            activeSurahForReading = surah
                                        }
                                    )
                                }
                                Screen.Quran.route -> {
                                    QuranScreen(
                                        viewModel = viewModel,
                                        onOpenSurahReader = { surah, juzNum ->
                                            activeSurahForReading = surah
                                            activeJuzForReading = juzNum
                                        }
                                    )
                                }
                                Screen.Prayer.route -> {
                                    PrayerScreen(
                                        viewModel = viewModel,
                                        onOpenLearnSalah = { navigateTo(Screen.LearnSalah.route) }
                                    )
                                }
                                Screen.AiAssistant.route -> {
                                    HidayahAiScreen(
                                        viewModel = viewModel,
                                        onOpenSettings = { navigateTo(Screen.Settings.route) }
                                    )
                                }
                                Screen.Discover.route -> {
                                    DiscoverScreen(
                                        viewModel = viewModel,
                                        initialTab = activeDiscoverTab
                                    )
                                }
                                Screen.Search.route -> {
                                    SearchScreen(
                                        viewModel = viewModel,
                                        onBack = { navigateBack() },
                                        onOpenSurah = { surahNum ->
                                            val surah = IslamicDataSource.SURAHS.find { it.number == surahNum } ?: IslamicDataSource.SURAHS[0]
                                            activeSurahForReading = surah
                                        },
                                        onNavigateToDiscoverTab = { tab ->
                                            activeDiscoverTab = tab
                                            navigateTo(Screen.Discover.route)
                                        }
                                    )
                                }
                                Screen.Tracker.route -> {
                                    TrackerScreen(viewModel = viewModel)
                                }
                                Screen.Settings.route -> {
                                    SettingsScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }

                    // Floating iOS 26 Liquid Glass Dock: auto-hides while soft keyboard (IME) is open
                    AnimatedVisibility(
                        visible = !isKeyboardVisible,
                        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(tween(150)),
                        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(110)),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        LiquidGlassFloatingDock(
                            items = bottomNavItems,
                            currentRoute = currentScreen,
                            isDrawerOpen = isLeftDrawerOpen,
                            onSelect = { screen -> navigateTo(screen.route) },
                            onOpenMoreDrawer = { isLeftDrawerOpen = !isLeftDrawerOpen },
                            modifier = Modifier.onSizeChanged { size ->
                                if (size.height > 0 && size.height != measuredDockHeightPx) {
                                    measuredDockHeightPx = size.height
                                }
                            }
                        )
                    }
                }
            }

            // ChatGPT / Gemini-style Left-Side Slide-Out Liquid Glass Sidebar Drawer
            LiquidGlassLeftDrawerOverlay(
                visible = isLeftDrawerOpen,
                currentRoute = currentScreen,
                cityName = homeState.userSettings.cityName,
                hijriDateText = homeState.hijriDate?.formatShort(),
                hasGeminiKey = homeState.userSettings.hasAnyAiApiKey(),
                onDismiss = { isLeftDrawerOpen = false },
                onSelectRoute = { route -> navigateTo(route) },
                onSelectTheme = { mode -> viewModel.updateAppTheme(mode.id) }
            )
        }
    }
}
