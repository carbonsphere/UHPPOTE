package com.carbonsphere.uhppote.net

import com.carbonsphere.uhppote.data.ip4
import com.carbonsphere.uhppote.protocol.Cmd
import com.carbonsphere.uhppote.protocol.Packet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityLogTest {
    @Test
    fun logDropsOldest() {
        val log = ActivityLog(max = 3)
        (1..5).forEach { log.info("line $it") }
        assertEquals(listOf("line 3", "line 4", "line 5"), log.lines.value.map { it.text })
    }

    @Test
    fun okAndErrorArePrefixed() {
        val log = ActivityLog()
        log.ok("Open door 1")
        log.error("Open door 2: no reply")
        assertEquals(listOf("✔ Open door 1", "✖ Open door 2: no reply"), log.lines.value.map { it.text })
    }

    @Test
    fun packetLogOnlyWhenEnabled() {
        val log = ActivityLog()
        var enabled = false
        val pl = log.packetLog { enabled }
        pl.log("TX", ip4("192.168.1.50"), Packet.request(Cmd.OPEN_DOOR, 1))
        assertEquals(0, log.lines.value.size)
        enabled = true
        pl.log("RX", ip4("192.168.1.50"), Packet.request(Cmd.OPEN_DOOR, 1))
        val line = log.lines.value.single().text
        assertTrue(line.startsWith("RX ← 192.168.1.50"), line)
        assertTrue(line.contains("17 40 00 00 01 00 00 00"), line)
    }
}
