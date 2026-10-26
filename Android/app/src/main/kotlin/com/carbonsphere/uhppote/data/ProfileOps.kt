package com.carbonsphere.uhppote.data

import com.carbonsphere.uhppote.protocol.Device

// Pure changes to the app state. Each returns a new AppState.

fun AppState.activeProfile(): Profile? = profiles.firstOrNull { it.id == activeProfileId }

fun AppState.findController(serial: Long): SavedController? =
    profiles.asSequence().flatMap { it.controllers }.firstOrNull { it.serial == serial }

fun AppState.addProfile(name: String, devices: List<Device>, newId: String): AppState =
    copy(profiles = profiles + Profile(newId, name, devices.distinctBy { it.serial }.map { it.toSaved() }))

/** Adds devices to a profile; ones already in it get their address refreshed and keep their names. */
fun AppState.addToProfile(profileId: String, devices: List<Device>): AppState = mapProfile(profileId) { p ->
    var list = p.controllers
    for (d in devices) {
        val fresh = d.toSaved()
        list = if (list.any { it.serial == d.serial }) {
            list.map {
                if (it.serial == d.serial) {
                    it.copy(ip = fresh.ip, mask = fresh.mask, gateway = fresh.gateway, firmware = fresh.firmware, mac = fresh.mac)
                } else it
            }
        } else {
            list + fresh
        }
    }
    p.copy(controllers = list)
}

fun AppState.renameProfile(id: String, name: String): AppState = mapProfile(id) { it.copy(name = name) }

fun AppState.deleteProfile(id: String): AppState = copy(
    profiles = profiles.filterNot { it.id == id },
    activeProfileId = activeProfileId.takeUnless { it == id },
)

fun AppState.moveProfile(id: String, toIndex: Int): AppState {
    val p = profiles.firstOrNull { it.id == id } ?: return this
    val rest = profiles.filterNot { it.id == id }.toMutableList()
    rest.add(toIndex.coerceIn(0, rest.size), p)
    return copy(profiles = rest)
}

fun AppState.removeController(profileId: String, serial: Long): AppState =
    mapProfile(profileId) { p -> p.copy(controllers = p.controllers.filterNot { it.serial == serial }) }

fun AppState.renameController(serial: Long, name: String): AppState = mapController(serial) { it.copy(name = name) }

fun AppState.setDoorName(serial: Long, door: Int, name: String): AppState = mapController(serial) { c ->
    val names = MutableList(maxOf(4, c.doorNames.size)) { c.doorNames.getOrElse(it) { "" } }
    if (door in 1..names.size) names[door - 1] = name.trim()
    c.copy(doorNames = names)
}

fun AppState.setDoorCount(serial: Long, count: Int): AppState =
    mapController(serial) { it.copy(doorCount = count.coerceIn(1, 4)) }

fun AppState.updateControllerIp(serial: Long, ip: String): AppState = mapController(serial) { it.copy(ip = ip) }

fun AppState.setActive(id: String?): AppState = copy(activeProfileId = id)

private fun AppState.mapProfile(id: String, f: (Profile) -> Profile): AppState =
    copy(profiles = profiles.map { if (it.id == id) f(it) else it })

/** Applies f to the controller in every profile that holds it. */
private fun AppState.mapController(serial: Long, f: (SavedController) -> SavedController): AppState =
    copy(profiles = profiles.map { p -> p.copy(controllers = p.controllers.map { if (it.serial == serial) f(it) else it }) })
