package com.carbonsphere.uhppote.protocol

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Creates the UDP sockets used to talk to controllers. */
fun interface SocketFactory {
    fun create(): DatagramSocket
}

val DEFAULT_SOCKETS = SocketFactory { DatagramSocket() }

/** Receives every packet sent ("TX") or received ("RX"). */
fun interface PacketLog {
    fun log(direction: String, address: InetAddress, packet: Packet)
}

/** Thrown when a controller does not answer in time. */
class NoReplyException : IOException("No reply from controller (check IP address, serial number and firewall)")

/** One slot of the controller's card table, see [Controller.getCardByIndex]. */
sealed interface CardSlot {
    data class Found(val card: Card) : CardSlot
    data object Deleted : CardSlot
    data object End : CardSlot
}

/** Pass to [Controller.getEvent] to fetch the most recent event. */
const val NEWEST_EVENT = 0xFFFFFFFFL

/**
 * Talks to one board. It is safe for concurrent use; requests are sent one
 * at a time.
 */
class Controller(
    override val serial: Long,
    val address: InetAddress,
    val port: Int = PORT,
    val timeoutMs: Int = 2500,
    private val sockets: SocketFactory = DEFAULT_SOCKETS,
    private val log: PacketLog? = null,
) : ControllerApi {
    private val mutex = Mutex()

    /** Sends req and waits for the matching reply. */
    private suspend fun exec(req: Packet): Packet = mutex.withLock {
        runInterruptible(Dispatchers.IO) {
            sockets.create().use { socket ->
                socket.connect(address, port)
                socket.soTimeout = timeoutMs
                log?.log("TX", address, req)
                socket.send(DatagramPacket(req.bytes, req.bytes.size))
                val buf = ByteArray(1024)
                while (true) {
                    val dp = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(dp)
                    } catch (e: SocketTimeoutException) {
                        throw NoReplyException()
                    }
                    val p = Packet.fromBytes(dp.data, dp.length) ?: continue
                    if (p.command != req.command || p.serial != serial) continue
                    log?.log("RX", address, p)
                    return@runInterruptible p
                }
                @Suppress("UNREACHABLE_CODE")
                error("unreachable")
            }
        }
    }

    /** Transmits req without waiting for a reply. */
    private suspend fun send(req: Packet) = mutex.withLock {
        runInterruptible(Dispatchers.IO) {
            sockets.create().use { socket ->
                socket.connect(address, port)
                log?.log("TX", address, req)
                socket.send(DatagramPacket(req.bytes, req.bytes.size))
            }
        }
    }

    private fun req(cmd: Int) = Packet.request(cmd, serial)

    private suspend fun execResult(req: Packet): Boolean = exec(req).result()

    /** The controller's current state. */
    override suspend fun status(): Status = parseStatus(exec(req(Cmd.STATUS)))

    /** The controller clock. */
    override suspend fun getTime(): LocalDateTime {
        val p = exec(req(Cmd.GET_TIME))
        return p.dateTime(8) ?: throw IOException("Invalid time in reply: ${p.bcd(8, 7)}")
    }

    /** Sets the controller clock and returns the time it now reports. */
    override suspend fun setTime(t: LocalDateTime): LocalDateTime? {
        val r = req(Cmd.SET_TIME).apply { putDateTime(8, t) }
        return exec(r).dateTime(8)
    }

    /** Unlocks door 1-4 for its configured delay. */
    override suspend fun openDoor(door: Int): Boolean {
        checkDoor(door)
        return execResult(req(Cmd.OPEN_DOOR).apply { this[8] = door })
    }

    /** A door's mode and unlock delay. */
    override suspend fun getDoorControl(door: Int): DoorControl {
        checkDoor(door)
        val p = exec(req(Cmd.GET_DOOR_CONTROL).apply { this[8] = door })
        return DoorControl(p[8], p[9], p[10])
    }

    /** Sets a door's mode and unlock delay. */
    override suspend fun setDoorControl(dc: DoorControl): DoorControl {
        checkDoor(dc.door)
        val p = exec(req(Cmd.SET_DOOR_CONTROL).apply { this[8] = dc.door; this[9] = dc.mode; this[10] = dc.delay })
        return DoorControl(p[8], p[9], p[10])
    }

    /** Where the controller pushes live events. */
    override suspend fun getListener(): Listener {
        val p = exec(req(Cmd.GET_LISTENER))
        return Listener(p.ip(8), p.u16(12), p[14])
    }

    /** Sets where the controller pushes live events. */
    override suspend fun setListener(l: Listener): Boolean {
        val r = req(Cmd.SET_LISTENER).apply {
            putIp(8, l.ip)
            putU16(12, l.port)
            this[14] = l.interval
        }
        return execResult(r)
    }

    /** The number of cards stored on the controller. */
    override suspend fun getCardCount(): Long = exec(req(Cmd.GET_CARD_COUNT)).u32(8)

    /** Looks up a card; null if it is not stored. */
    override suspend fun getCard(number: Long): Card? {
        checkCard(number)
        val p = exec(req(Cmd.GET_CARD).apply { putU32(8, number) })
        val got = p.u32(8)
        if (got == 0L || got == 0xFFFFFFFFL) return null
        return parseCard(p)
    }

    /**
     * The card in slot index (1-based) of the controller's card table:
     * [CardSlot.End] past the last slot, [CardSlot.Deleted] for a deleted card.
     */
    override suspend fun getCardByIndex(index: Long): CardSlot {
        val p = exec(req(Cmd.GET_CARD_BY_INDEX).apply { putU32(8, index) })
        return when (p.u32(8)) {
            0L -> CardSlot.End
            0xFFFFFFFFL -> CardSlot.Deleted
            else -> CardSlot.Found(parseCard(p))
        }
    }

    private fun parseCard(p: Packet) = Card(
        number = p.u32(8),
        from = p.date(12) ?: java.time.LocalDate.MIN,
        to = p.date(16) ?: java.time.LocalDate.MIN,
        doors = List(4) { p[20 + it] },
    )

    /** Adds or updates a card. */
    override suspend fun putCard(card: Card): Boolean {
        checkCard(card.number)
        val r = req(Cmd.PUT_CARD).apply {
            putU32(8, card.number)
            putDate(12, card.from)
            putDate(16, card.to)
            card.doors.take(4).forEachIndexed { i, d -> this[20 + i] = d }
        }
        return execResult(r)
    }

    /** Removes a card. */
    override suspend fun deleteCard(number: Long): Boolean {
        checkCard(number)
        return execResult(req(Cmd.DELETE_CARD).apply { putU32(8, number) })
    }

    /** Removes every card from the controller. */
    override suspend fun deleteAllCards(): Boolean =
        execResult(req(Cmd.DELETE_ALL_CARDS).apply { CONFIRM_MAGIC.copyInto(bytes, 8) })

    /** The "already read" event index. */
    override suspend fun getEventIndex(): Long = exec(req(Cmd.GET_EVENT_INDEX)).u32(8)

    /** Sets the "already read" event index. */
    override suspend fun setEventIndex(index: Long): Boolean =
        execResult(req(Cmd.SET_EVENT_INDEX).apply { putU32(8, index); CONFIRM_MAGIC.copyInto(bytes, 12) })

    /** The event stored at index. */
    override suspend fun getEvent(index: Long): Event =
        parseEvent(exec(req(Cmd.GET_EVENT).apply { putU32(8, index) }), 8)

    /** Sets the door interlock mode (see [INTERLOCK_MODES]). */
    override suspend fun setInterlock(mode: Int): Boolean = execResult(req(Cmd.SET_INTERLOCK).apply { this[8] = mode })

    /** Enables or disables the keypad on each reader. */
    override suspend fun setKeypads(enabled: List<Boolean>): Boolean {
        val r = req(Cmd.SET_KEYPADS).apply {
            enabled.take(4).forEachIndexed { i, on -> if (on) this[8 + i] = 0x01 }
        }
        return execResult(r)
    }

    /**
     * Changes the controller's IP settings. The controller does not reply;
     * search again afterwards to confirm the change.
     */
    override suspend fun setAddress(ip: Inet4Address, mask: Inet4Address, gateway: Inet4Address) {
        val r = req(Cmd.SET_ADDRESS).apply {
            putIp(8, ip)
            putIp(12, mask)
            putIp(16, gateway)
            CONFIRM_MAGIC.copyInto(bytes, 20)
        }
        send(r)
    }
}

private fun checkDoor(door: Int) {
    require(door in 1..4) { "Door must be 1-4, got $door" }
}

private fun checkCard(number: Long) {
    require(number != 0L && number < 0xFFFFFF00L) { "Invalid card number $number" }
}
