package com.carbonsphere.uhppote.ui

import com.carbonsphere.uhppote.AppGraph
import com.carbonsphere.uhppote.data.Profile
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.domain.resolveProfile
import com.carbonsphere.uhppote.ui.cards.CardsViewModel
import com.carbonsphere.uhppote.ui.doors.DoorsViewModel
import com.carbonsphere.uhppote.ui.events.EventsViewModel
import com.carbonsphere.uhppote.ui.settings.SettingsViewModel
import com.carbonsphere.uhppote.ui.status.StatusViewModel
import java.io.IOException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/** Builds the MainViewModel from the app graph. */
fun mainViewModel(g: AppGraph) = MainViewModel(
    store = g.store,
    log = g.log,
    resolve = {
        resolveProfile(
            state = g.store.state.first(),
            localNet = g.wifi.localNet(),
            probe = { c ->
                try {
                    g.controller(c).status()
                    true
                } catch (e: IOException) {
                    false
                }
            },
            search = { g.search() },
        )
    },
    searchNet = { g.search(it) },
)

/** Builds the DoorsViewModel for the active profile. */
fun doorsViewModel(g: AppGraph, profile: StateFlow<Profile?>) = DoorsViewModel(profile, g.log, g::controller)

/** Builds the StatusViewModel for the selected controller. */
fun statusViewModel(g: AppGraph, controller: StateFlow<SavedController?>) = StatusViewModel(controller, g.log, g::controller)

/** Builds the EventsViewModel for the selected controller. */
fun eventsViewModel(g: AppGraph, controller: StateFlow<SavedController?>) = EventsViewModel(controller, g.log, g::controller)

/** Builds the CardsViewModel for the selected controller. */
fun cardsViewModel(g: AppGraph, controller: StateFlow<SavedController?>) = CardsViewModel(controller, g.log, g::controller, g.cardCache)

/** Builds the SettingsViewModel for the selected controller. */
fun settingsViewModel(g: AppGraph, controller: StateFlow<SavedController?>) = SettingsViewModel(
    controller = controller,
    store = g.store,
    log = g.log,
    controllerFor = g::controller,
    localAddress = { g.wifi.localNet()?.address },
    searchNet = { g.search(it) },
)
