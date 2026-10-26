package com.carbonsphere.uhppote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.carbonsphere.uhppote.ui.AppScaffold
import com.carbonsphere.uhppote.ui.Destination
import com.carbonsphere.uhppote.ui.cards.CardsScreen
import com.carbonsphere.uhppote.ui.doors.DoorsScreen
import com.carbonsphere.uhppote.ui.events.EventsScreen
import com.carbonsphere.uhppote.ui.settings.SettingsScreen
import com.carbonsphere.uhppote.ui.status.StatusScreen
import com.carbonsphere.uhppote.ui.theme.UhppoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UhppoteTheme {
                AppScaffold(topBar = {}) { destination ->
                    when (destination) {
                        Destination.DOORS -> DoorsScreen()
                        Destination.STATUS -> StatusScreen()
                        Destination.CARDS -> CardsScreen()
                        Destination.EVENTS -> EventsScreen()
                        Destination.SETTINGS -> SettingsScreen()
                    }
                }
            }
        }
    }
}
