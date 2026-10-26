package com.carbonsphere.uhppote.ui

import com.carbonsphere.uhppote.protocol.Event
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    // The status packet from the protocol ParseTest.
    private val event = Event(5, 1, true, 2, 1, 10012345, LocalDateTime.of(2024, 3, 15, 9, 30, 0), 1)

    @Test
    fun formatTimeNull() {
        assertEquals("-", formatTime(null))
        assertEquals("2024-03-15 09:30:00", formatTime(LocalDateTime.of(2024, 3, 15, 9, 30, 0)))
    }

    @Test
    fun statusRowsNoEvent() {
        val rows = statusRows(status(relays = 1)) { "Door $it" }
        assertEquals(
            listOf(
                "Controller time" to "2024-03-15 10:15:30",
                "System error" to "None",
                "Packet sequence no." to "42",
                "Special info" to "0x00",
                "Relay state" to "0x01",
                "Input state" to "0x00",
                "Last event" to "No events recorded",
            ),
            rows,
        )
    }

    @Test
    fun statusRowsWithEvent() {
        val rows = statusRows(status(relays = 1, last = event).copy(systemError = 3)) { if (it == 2) "Back door" else "Door $it" }
        assertEquals("Error code 3", rows.toMap()["System error"])
        assertEquals(
            listOf(
                "Last event #" to "5",
                "Time" to "2024-03-15 09:30:00",
                "Type" to "Card swipe",
                "Card" to "10012345",
                "Door / direction" to "Back door / In",
                "Access" to "Granted",
                "Reason" to event.reasonText(),
            ),
            rows.drop(6),
        )
    }
}
