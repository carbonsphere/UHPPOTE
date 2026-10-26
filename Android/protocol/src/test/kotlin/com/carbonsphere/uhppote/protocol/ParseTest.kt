package com.carbonsphere.uhppote.protocol

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Builds a packet from hex (spaces allowed), zero padded to 64 bytes. */
fun packet(hex: String): Packet {
    val h = hex.replace(" ", "").padEnd(PACKET_SIZE * 2, '0')
    val b = ByteArray(PACKET_SIZE) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    return Packet.fromBytes(b, b.size) ?: error("bad packet")
}

/** Hex of a packet without spaces, lower case, for comparisons. */
fun Packet.plainHex(): String = hex().replace(" ", "").lowercase()

/** Expected packet hex: the prefix padded with zeros to 64 bytes. */
fun expectHex(prefix: String): String = prefix.replace(" ", "").lowercase().padEnd(PACKET_SIZE * 2, '0')

const val STATUS_HEX = "17200000 44332211" +
    "05000000 01 01 02 01 B9C69800 20240315093000 01" + // last event
    "01000000" + // door 1 open
    "00010000" + // button 2 pressed
    "00 101530 2A000000 00000000 00 01 00 240315"

class ParseTest {
    @Test
    fun parseDevice() {
        val d = parseDevice(packet("17940000 78563412 C0A80132 FFFFFF00 C0A80101 001122334455 0662 20180816"))
        assertEquals(305419896L, d.serial)
        assertEquals("192.168.1.50", d.ip.hostAddress)
        assertEquals("255.255.255.0", d.mask.hostAddress)
        assertEquals("192.168.1.1", d.gateway.hostAddress)
        assertEquals("00:11:22:33:44:55", d.mac)
        assertEquals("6.62", d.firmware)
        assertEquals("2018-08-16", d.date)
    }

    @Test
    fun parseStatus() {
        val s = parseStatus(packet(STATUS_HEX))
        val e = s.lastEvent
        assertEquals(5L, e.index)
        assertEquals(1, e.type)
        assertTrue(e.granted)
        assertEquals(2, e.door)
        assertEquals(1, e.direction)
        assertEquals(10012345L, e.card)
        assertEquals(LocalDateTime.of(2024, 3, 15, 9, 30, 0), e.timestamp)
        assertEquals(listOf(true, false, false, false), s.doorOpen)
        assertEquals(listOf(false, true, false, false), s.buttonPressed)
        assertEquals(LocalDateTime.of(2024, 3, 15, 10, 15, 30), s.controllerTime)
        assertEquals(42L, s.sequenceNo)
        assertEquals(1, s.relays)
    }

    @Test
    fun emptyTimestampIsNull() {
        val s = parseStatus(packet("17200000 44332211"))
        assertNull(s.lastEvent.timestamp)
        assertNull(s.controllerTime)
        assertTrue(s.lastEvent.isEmpty())
    }

    @Test
    fun fromBytesRejectsWrongSizeOrStart() {
        assertNull(Packet.fromBytes(ByteArray(63), 63))
        assertNull(Packet.fromBytes(ByteArray(64), 64))
    }

    @Test
    fun putBcdRejectsBadDigits() {
        assertFailsWith<IllegalArgumentException> { Packet().putBcd(8, "123") }
        assertFailsWith<IllegalArgumentException> { Packet().putBcd(8, "12a4") }
    }

    @Test
    fun doorCountFromSerial() {
        assertEquals(1, doorCountForSerial(123456789))
        assertEquals(2, doorCountForSerial(223456789))
        assertEquals(4, doorCountForSerial(423206957))
        assertEquals(4, doorCountForSerial(305419896))
    }
}
