package com.carbonsphere.uhppote.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.carbonsphere.uhppote.R

/** The app's sections. Doors comes first: opening a door is the phone's main job. */
enum class Destination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    DOORS("doors", R.string.nav_doors, Icons.Filled.Home),
    STATUS("status", R.string.nav_status, Icons.Filled.Info),
    CARDS("cards", R.string.nav_cards, Icons.Filled.AccountBox),
    @Suppress("DEPRECATION")
    EVENTS("events", R.string.nav_events, Icons.Filled.List),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}
