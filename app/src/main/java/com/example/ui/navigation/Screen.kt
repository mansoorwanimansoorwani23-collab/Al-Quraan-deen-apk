package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector
) {
    // 4 Primary Liquid Glass Bottom Bar destinations
    object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Quran : Screen("quran", "Quran", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook)
    object Prayer : Screen("prayer", "Prayer", Icons.Filled.AccessTimeFilled, Icons.Outlined.AccessTime)
    object AiAssistant : Screen("ai_assistant", "AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)

    // Secondary destinations accessible from Top-Right 3-dot menu & quick shortcuts
    object Discover : Screen("discover", "Discover", Icons.Filled.Explore, Icons.Outlined.Explore)
    object Tracker : Screen("tracker", "Deen Tracker", Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt)
    object Search : Screen("search", "Search", Icons.Filled.Search, Icons.Outlined.Search)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)

    // Dedicated Hidayah feature screens
    object OnlineQuranAudio : Screen("online_quran_audio", "Online Quran Audio", Icons.Filled.Headphones, Icons.Outlined.Headphones)
    object RecitationPractice : Screen("recitation_practice", "Recitation Practice", Icons.Filled.Mic, Icons.Outlined.Mic)
    object LearnSalah : Screen("learn_salah", "Learn Salah", Icons.Filled.School, Icons.Outlined.School)
}

enum class DiscoverTab(val title: String, val icon: ImageVector) {
    SEARCH("In-App Search", Icons.Filled.Search),
    QIBLA("Qibla Finder", Icons.Filled.Explore),
    TASBEEH("Digital Tasbeeh", Icons.Filled.TouchApp),
    HADITH("Hadith", Icons.Filled.FormatQuote),
    DUAS("Duas & Azkar", Icons.Filled.Favorite),
    CALENDAR("Hijri Calendar", Icons.Filled.CalendarMonth),
    RAMADAN("Ramadan Mode", Icons.Filled.NightsStay)
}
