package com.carbonsphere.uhppote

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carbonsphere.uhppote.ui.AppScaffold
import com.carbonsphere.uhppote.ui.Destination
import com.carbonsphere.uhppote.ui.MainScreen
import com.carbonsphere.uhppote.ui.MainViewModel
import com.carbonsphere.uhppote.ui.TopBar
import com.carbonsphere.uhppote.ui.cards.CardsScreen
import com.carbonsphere.uhppote.ui.cards.CardsViewModel
import com.carbonsphere.uhppote.ui.cardsViewModel
import com.carbonsphere.uhppote.ui.doors.DoorsScreen
import com.carbonsphere.uhppote.ui.doors.DoorsViewModel
import com.carbonsphere.uhppote.ui.doorsViewModel
import com.carbonsphere.uhppote.ui.events.EventsScreen
import com.carbonsphere.uhppote.ui.events.EventsViewModel
import com.carbonsphere.uhppote.ui.eventsViewModel
import com.carbonsphere.uhppote.ui.mainViewModel
import com.carbonsphere.uhppote.ui.profiles.ProfilesScreen
import com.carbonsphere.uhppote.ui.settings.SettingsScreen
import com.carbonsphere.uhppote.ui.settings.SettingsViewModel
import com.carbonsphere.uhppote.ui.settingsViewModel
import com.carbonsphere.uhppote.ui.status.StatusScreen
import com.carbonsphere.uhppote.ui.status.StatusViewModel
import com.carbonsphere.uhppote.ui.statusViewModel
import com.carbonsphere.uhppote.ui.theme.UhppoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UhppoteTheme {
                Surface(Modifier.fillMaxSize()) {
                    val vm: MainViewModel = viewModel { mainViewModel(graph) }
                    val state by vm.state.collectAsState()
                    LaunchedEffect(state.keepScreenOn) {
                        if (state.keepScreenOn) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                    MainContent(vm)
                }
            }
        }
    }
}

@Composable
private fun MainContent(vm: MainViewModel) {
    // Coming back after a while may mean another site.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onBackground(SystemClock.elapsedRealtime()) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onForeground(SystemClock.elapsedRealtime()) }
    when (val screen = vm.screen.collectAsState().value) {
        MainScreen.Loading -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.looking_for_controllers))
        }
        MainScreen.Profiles -> ProfilesScreen(vm)
        is MainScreen.Connected -> AppScaffold(topBar = { d -> TopBar(vm, screen.profile, d) }) { destination ->
            when (destination) {
                Destination.DOORS -> {
                    val graph = LocalContext.current.graph
                    val doors: DoorsViewModel = viewModel { doorsViewModel(graph, vm.activeProfile) }
                    DoorsScreen(doors)
                }
                Destination.STATUS -> {
                    val graph = LocalContext.current.graph
                    val status: StatusViewModel = viewModel { statusViewModel(graph, vm.selectedController) }
                    StatusScreen(status)
                }
                Destination.CARDS -> {
                    val graph = LocalContext.current.graph
                    val cards: CardsViewModel = viewModel { cardsViewModel(graph, vm.selectedController) }
                    CardsScreen(cards)
                }
                Destination.EVENTS -> {
                    val graph = LocalContext.current.graph
                    val events: EventsViewModel = viewModel { eventsViewModel(graph, vm.selectedController) }
                    EventsScreen(events)
                }
                Destination.SETTINGS -> {
                    val graph = LocalContext.current.graph
                    val settings: SettingsViewModel = viewModel { settingsViewModel(graph, vm.selectedController) }
                    SettingsScreen(settings)
                }
            }
        }
    }
}
