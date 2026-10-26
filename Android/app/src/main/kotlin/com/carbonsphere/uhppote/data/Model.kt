package com.carbonsphere.uhppote.data

import com.carbonsphere.uhppote.protocol.Device
import com.carbonsphere.uhppote.protocol.doorCountForSerial
import kotlinx.serialization.Serializable

/** A controller board saved in a profile. */
@Serializable
data class SavedController(
    val serial: Long,
    val ip: String,
    val mask: String,
    val gateway: String,
    val firmware: String,
    val mac: String,
    val name: String = "",
    val doorCount: Int,
    val doorNames: List<String> = emptyList(),
) {
    /** The name given to door 1-4, or fallback if it has none. */
    fun doorName(door: Int, fallback: String): String =
        doorNames.getOrNull(door - 1)?.takeIf { it.isNotBlank() } ?: fallback

    /** The controller's name, or its serial number if it has none. */
    fun displayName(): String = name.ifBlank { serial.toString() }
}

/** A site or network with the controller boards on it. */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val controllers: List<SavedController>,
)

/** Everything the app remembers. */
@Serializable
data class AppState(
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: String? = null,
    val keepScreenOn: Boolean = false,
    val showRawPackets: Boolean = false,
)

fun Device.toSaved(): SavedController = SavedController(
    serial = serial,
    ip = ip.hostAddress ?: "",
    mask = mask.hostAddress ?: "",
    gateway = gateway.hostAddress ?: "",
    firmware = firmware,
    mac = mac,
    doorCount = doorCountForSerial(serial),
)
