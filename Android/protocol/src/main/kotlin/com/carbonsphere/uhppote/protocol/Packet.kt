// Package protocol implements the UDP protocol spoken by UHPPOTE / WeiGeng
// Wiegand access controller boards (firmware V6.56+). It is a port of the Go
// package in Windows/uhppote and sends exactly the same bytes.
//
// Every request and reply is a fixed 64-byte packet:
//
//	offset 0  : 0x17 (start of frame)
//	offset 1  : command code
//	offset 2-3: 0x0000
//	offset 4-7: controller serial number, uint32 little-endian
//	offset 8+ : command payload, zero padded
//
// Multi-byte integers are little-endian, IP addresses are in network order and
// dates/times are BCD ("20190522" is sent as the bytes 20 19 05 22).
package com.carbonsphere.uhppote.protocol

import java.net.Inet4Address
import java.net.InetAddress
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** The UDP port every controller listens on. */
const val PORT = 60000

/** The fixed size of requests and replies. */
const val PACKET_SIZE = 64

private const val START_OF_FRAME = 0x17

/** Command codes. */
object Cmd {
    const val STATUS = 0x20
    const val SET_TIME = 0x30
    const val GET_TIME = 0x32
    const val OPEN_DOOR = 0x40
    const val PUT_CARD = 0x50
    const val DELETE_CARD = 0x52
    const val DELETE_ALL_CARDS = 0x54
    const val GET_CARD_COUNT = 0x58
    const val GET_CARD = 0x5A
    const val GET_CARD_BY_INDEX = 0x5C
    const val SET_DOOR_CONTROL = 0x80
    const val GET_DOOR_CONTROL = 0x82
    const val SET_LISTENER = 0x90
    const val GET_LISTENER = 0x92
    const val SEARCH = 0x94
    const val SET_ADDRESS = 0x96
    const val SET_INTERLOCK = 0xA2
    const val SET_KEYPADS = 0xA4
    const val GET_EVENT = 0xB0
    const val SET_EVENT_INDEX = 0xB2
    const val GET_EVENT_INDEX = 0xB4
}

/** Must follow the payload of destructive commands. */
internal val CONFIRM_MAGIC = byteArrayOf(0x55, 0xAA.toByte(), 0xAA.toByte(), 0x55)

private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

/** A raw 64-byte request or reply. */
class Packet(val bytes: ByteArray = ByteArray(PACKET_SIZE)) {
    init {
        require(bytes.size == PACKET_SIZE) { "packet must be $PACKET_SIZE bytes" }
    }

    /** The command code of the packet. */
    val command: Int get() = this[1]

    /** The controller serial number carried in the packet. */
    val serial: Long get() = u32(4)

    operator fun get(i: Int): Int = bytes[i].toInt() and 0xFF

    operator fun set(i: Int, v: Int) {
        bytes[i] = v.toByte()
    }

    /** The packet as space-separated hex bytes, for logging. */
    fun hex(): String = hexBytes(bytes)

    fun u16(off: Int): Int = this[off] or (this[off + 1] shl 8)

    fun u32(off: Int): Long =
        (this[off].toLong()) or (this[off + 1].toLong() shl 8) or
            (this[off + 2].toLong() shl 16) or (this[off + 3].toLong() shl 24)

    fun putU16(off: Int, v: Int) {
        this[off] = v
        this[off + 1] = v shr 8
    }

    fun putU32(off: Int, v: Long) {
        for (i in 0..3) this[off + i] = (v shr (8 * i)).toInt()
    }

    fun ip(off: Int): Inet4Address =
        InetAddress.getByAddress(bytes.copyOfRange(off, off + 4)) as Inet4Address

    fun putIp(off: Int, ip: InetAddress) {
        require(ip is Inet4Address) { "${ip.hostAddress} is not an IPv4 address" }
        ip.address.copyInto(bytes, off)
    }

    /** n bytes at off as a string of 2n decimal digits. */
    fun bcd(off: Int, n: Int): String = buildString {
        for (i in 0 until n) append("%02x".format(this@Packet[off + i]))
    }

    /** Writes a string of decimal digits (even length) as BCD bytes. */
    fun putBcd(off: Int, digits: String) {
        require(digits.length % 2 == 0) { "BCD value \"$digits\" has odd length" }
        require(digits.all { it in '0'..'9' }) { "BCD value \"$digits\" is not numeric" }
        for (i in digits.indices step 2) this[off + i / 2] = digits.substring(i, i + 2).toInt(16)
    }

    fun putDate(off: Int, d: LocalDate) = putBcd(off, d.format(DATE))

    /** A 4-byte BCD YYYYMMDD date, or null for an empty or invalid date. */
    fun date(off: Int): LocalDate? = try {
        LocalDate.parse(bcd(off, 4), DATE)
    } catch (e: DateTimeParseException) {
        null
    }

    fun putDateTime(off: Int, t: LocalDateTime) = putBcd(off, t.format(DATE_TIME))

    /** A 7-byte BCD YYYYMMDDhhmmss timestamp, or null for an empty or invalid one. */
    fun dateTime(off: Int): LocalDateTime? = try {
        LocalDateTime.parse(bcd(off, 7), DATE_TIME)
    } catch (e: DateTimeParseException) {
        null
    }

    fun result(): Boolean = this[8] == 0x01

    companion object {
        fun request(cmd: Int, serial: Long): Packet = Packet().apply {
            this[0] = START_OF_FRAME
            this[1] = cmd
            putU32(4, serial)
        }

        /** The packet in buf, or null unless it is 64 bytes starting with 0x17. */
        fun fromBytes(buf: ByteArray, len: Int): Packet? {
            if (len != PACKET_SIZE || buf[0].toInt() != START_OF_FRAME) return null
            return Packet(buf.copyOf(PACKET_SIZE))
        }
    }
}

/** b as space-separated upper-case hex bytes. */
fun hexBytes(b: ByteArray): String = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
