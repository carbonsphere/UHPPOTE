package com.carbonsphere.uhppote.net

import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.protocol.ControllerApi
import java.util.concurrent.ConcurrentHashMap

/**
 * One client per controller, so the app's screens, polling and the widget
 * share its one-request-at-a-time lock. A new address gets a new client.
 */
class ControllerCache(private val create: (SavedController) -> ControllerApi) {
    private val clients = ConcurrentHashMap<Long, Pair<String, ControllerApi>>()

    fun get(saved: SavedController): ControllerApi =
        clients.compute(saved.serial) { _, old -> if (old?.first == saved.ip) old else saved.ip to create(saved) }!!.second
}
