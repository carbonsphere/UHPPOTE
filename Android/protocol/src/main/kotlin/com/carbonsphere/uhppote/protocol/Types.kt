package com.carbonsphere.uhppote.protocol

import java.net.Inet4Address
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** A controller found by search. */
data class Device(
    val serial: Long,
    val ip: Inet4Address,
    val mask: Inet4Address,
    val gateway: Inet4Address,
    val mac: String, // e.g. "00:11:22:33:44:55"
    val firmware: String, // e.g. "6.62"
    val date: String, // firmware release date, YYYY-MM-DD
)

internal fun parseDevice(p: Packet): Device {
    val ver = p.bcd(26, 2)
    val d = p.bcd(28, 4)
    return Device(
        serial = p.serial,
        ip = p.ip(8),
        mask = p.ip(12),
        gateway = p.ip(16),
        mac = (20 until 26).joinToString(":") { "%02x".format(p[it]) },
        firmware = "${ver.substring(0, 2).toIntOrNull() ?: 0}.${ver.substring(2)}",
        date = d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8),
    )
}

/** A swipe, door or alarm record stored by the controller. */
data class Event(
    val index: Long,
    val type: Int,
    val granted: Boolean,
    val door: Int,
    val direction: Int,
    val card: Long,
    val timestamp: LocalDateTime?,
    val reason: Int,
) {
    /** Whether the slot holds no event. */
    fun isEmpty(): Boolean = type == 0x00 && index == 0L

    fun typeText(): String = when (type) {
        0x00 -> "None"
        0x01 -> "Card swipe"
        0x02 -> "Door / button"
        0x03 -> "Alarm"
        0xFF -> "Overwritten"
        else -> "Type $type"
    }

    fun directionText(): String = when (direction) {
        0x01 -> "In"
        0x02 -> "Out"
        else -> "-"
    }

    fun reasonText(): String = EVENT_REASONS[reason]?.let { "$it ($reason)" } ?: "Reason $reason"
}

internal fun parseEvent(p: Packet, off: Int) = Event(
    index = p.u32(off),
    type = p[off + 4],
    granted = p[off + 5] == 0x01,
    door = p[off + 6],
    direction = p[off + 7],
    card = p.u32(off + 8),
    timestamp = p.dateTime(off + 12),
    reason = p[off + 19],
)

/** Reason codes to text. The list is best-effort; unknown codes are shown by number. */
private val EVENT_REASONS = mapOf(
    1 to "Swipe",
    5 to "Denied: PC control",
    6 to "Denied: no access rights",
    7 to "Denied: wrong password",
    8 to "Denied: anti-passback",
    9 to "Denied: more cards required",
    10 to "Denied: first card open",
    11 to "Denied: door normally closed",
    12 to "Denied: interlock",
    13 to "Denied: outside time profile",
    15 to "Denied: invalid time zone",
    18 to "Denied",
    20 to "Push button",
    23 to "Door opened",
    24 to "Door closed",
    25 to "Door opened by super password",
    28 to "Controller power on",
    29 to "Controller reset",
    31 to "Push button denied: door locked",
    32 to "Push button denied: offline",
    33 to "Push button denied: interlock",
    34 to "Push button denied: threat",
    37 to "Door open too long",
    38 to "Forced open",
    39 to "Fire",
    40 to "Forced closed",
    41 to "Anti-theft",
    42 to "24x7 zone",
    43 to "Emergency",
    44 to "Remote open door",
    45 to "Remote open door (USB reader)",
)

/** The controller's current state, as returned by [Cmd.STATUS]. */
data class Status(
    val lastEvent: Event,
    val doorOpen: List<Boolean>,
    val buttonPressed: List<Boolean>,
    val systemError: Int,
    val controllerTime: LocalDateTime?,
    val sequenceNo: Long,
    val specialInfo: Int,
    val relays: Int, // bitmask, bit 0 = door 1
    val inputs: Int, // bitmask
)

private val STATUS_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss")

internal fun parseStatus(p: Packet): Status {
    // Date is YYMMDD at 51, time is hhmmss at 37.
    val time = try {
        LocalDateTime.parse(p.bcd(51, 3) + p.bcd(37, 3), STATUS_TIME)
    } catch (e: DateTimeParseException) {
        null
    }
    return Status(
        lastEvent = parseEvent(p, 8),
        doorOpen = List(4) { p[28 + it] == 0x01 },
        buttonPressed = List(4) { p[32 + it] == 0x01 },
        systemError = p[36],
        controllerTime = time,
        sequenceNo = p.u32(40),
        specialInfo = p[48],
        relays = p[49],
        inputs = p[50],
    )
}

/** An access card stored on the controller. */
data class Card(
    val number: Long,
    val from: LocalDate,
    val to: LocalDate,
    /** Rule for doors 1-4: 0 = no access, 1 = always allowed, 2-254 = time profile index. */
    val doors: List<Int>,
)

/** Door control modes. */
object DoorMode {
    const val NORMALLY_OPEN = 1
    const val NORMALLY_CLOSED = 2
    const val CONTROLLED = 3
}

/** A door's lock mode and unlock delay. */
data class DoorControl(
    val door: Int, // 1-4
    val mode: Int, // a DoorMode value
    val delay: Int, // seconds
) {
    fun modeText(): String = when (mode) {
        DoorMode.NORMALLY_OPEN -> "Normally open"
        DoorMode.NORMALLY_CLOSED -> "Normally closed"
        DoorMode.CONTROLLED -> "Controlled"
        else -> "Mode $mode"
    }
}

/** Where the controller pushes live events. */
data class Listener(
    val ip: Inet4Address,
    val port: Int,
    val interval: Int, // seconds between periodic status packets, 0 = events only
)

/** Interlock modes: value to description. */
val INTERLOCK_MODES: List<Pair<Int, String>> = listOf(
    0x00 to "No interlock",
    0x01 to "Doors 1 & 2",
    0x02 to "Doors 3 & 4",
    0x03 to "Doors 1 & 2, and doors 3 & 4",
    0x04 to "Doors 1, 2 & 3",
    0x08 to "Doors 1, 2, 3 & 4",
)

/**
 * The number of doors a controller has, guessed from the first digit of its
 * serial number (1xxxxxxxx = 1 door, 2xxxxxxxx = 2 doors, 4xxxxxxxx = 4 doors).
 * Anything else is treated as 4 doors.
 */
fun doorCountForSerial(serial: Long): Int = when (serial.toString().first()) {
    '1' -> 1
    '2' -> 2
    else -> 4
}
