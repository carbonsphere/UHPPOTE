package com.carbonsphere.uhppote.ui

import com.carbonsphere.uhppote.AppGraph
import com.carbonsphere.uhppote.domain.resolveProfile
import java.io.IOException
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
