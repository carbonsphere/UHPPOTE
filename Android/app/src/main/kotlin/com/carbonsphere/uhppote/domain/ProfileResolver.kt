package com.carbonsphere.uhppote.domain

import com.carbonsphere.uhppote.data.AppState
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.net.LocalNet
import com.carbonsphere.uhppote.protocol.Device
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** The outcome of choosing a profile on launch. */
sealed interface Resolution {
    /** Use this profile; ipUpdates holds controllers found at a new address (serial to IP). */
    data class Active(val profileId: String, val ipUpdates: Map<Long, String>) : Resolution

    /** Let the manager pick from the profile list. */
    data object Choose : Resolution
}

/**
 * Picks the profile for the network this device is on, without needing the
 * Wi-Fi name (which Android hides without location permission):
 *
 * 1. exactly one profile with a controller in this device's subnet;
 * 2. otherwise the first profile with a controller that answers, the last
 *    active profile first;
 * 3. otherwise a search: a known controller at a new address;
 * 4. otherwise let the manager choose.
 */
suspend fun resolveProfile(
    state: AppState,
    localNet: LocalNet?,
    probe: suspend (SavedController) -> Boolean,
    search: suspend () -> List<Device>,
): Resolution {
    val profiles = state.profiles.filter { it.controllers.isNotEmpty() }
    if (profiles.isEmpty()) return Resolution.Choose

    if (localNet != null) {
        val inSubnet = profiles.filter { p -> p.controllers.any { localNet.contains(it.ip) } }
        if (inSubnet.size == 1) return Resolution.Active(inSubnet.single().id, emptyMap())
    }

    // Last active profile first, then the others in list order.
    val ordered = profiles.sortedBy { if (it.id == state.activeProfileId) 0 else 1 }

    val answering: Set<Long> = coroutineScope {
        ordered.flatMap { it.controllers }.distinctBy { it.serial }
            .map { c -> async { c.serial to safeProbe(probe, c) } }
            .awaitAll()
            .filter { it.second }
            .map { it.first }
            .toSet()
    }
    ordered.firstOrNull { p -> p.controllers.any { it.serial in answering } }?.let {
        return Resolution.Active(it.id, emptyMap())
    }

    val found = try {
        search()
    } catch (e: IOException) {
        emptyList()
    }
    for (p in ordered) {
        val moved = p.controllers.mapNotNull { c ->
            val d = found.firstOrNull { it.serial == c.serial } ?: return@mapNotNull null
            c.serial to (d.ip.hostAddress ?: return@mapNotNull null)
        }.toMap()
        if (moved.isNotEmpty()) return Resolution.Active(p.id, moved.filter { (serial, ip) -> p.controllers.first { it.serial == serial }.ip != ip })
    }
    return Resolution.Choose
}

private suspend fun safeProbe(probe: suspend (SavedController) -> Boolean, c: SavedController): Boolean = try {
    probe(c)
} catch (e: IOException) {
    false
} catch (e: IllegalArgumentException) {
    false
}
