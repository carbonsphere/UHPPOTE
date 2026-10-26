package com.carbonsphere.uhppote.ui

import com.carbonsphere.uhppote.protocol.Status
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** A controller time as yyyy-MM-dd HH:mm:ss, or "-" when the controller has none. */
fun formatTime(t: LocalDateTime?): String = t?.format(TIME) ?: "-"

fun hex2(v: Int): String = "0x%02X".format(v)

/** The label/value rows the Windows app shows under the door table. */
fun statusRows(s: Status, doorName: (Int) -> String): List<Pair<String, String>> {
    val rows = mutableListOf(
        "Controller time" to formatTime(s.controllerTime),
        "System error" to if (s.systemError == 0) "None" else "Error code ${s.systemError}",
        "Packet sequence no." to s.sequenceNo.toString(),
        "Special info" to hex2(s.specialInfo),
        "Relay state" to hex2(s.relays),
        "Input state" to hex2(s.inputs),
    )
    val e = s.lastEvent
    if (e.isEmpty()) {
        rows += "Last event" to "No events recorded"
    } else {
        rows += listOf(
            "Last event #" to e.index.toString(),
            "Time" to formatTime(e.timestamp),
            "Type" to e.typeText(),
            "Card" to e.card.toString(),
            "Door / direction" to "${if (e.door in 1..4) doorName(e.door) else e.door.toString()} / ${e.directionText()}",
            "Access" to if (e.granted) "Granted" else "Denied",
            "Reason" to e.reasonText(),
        )
    }
    return rows
}
