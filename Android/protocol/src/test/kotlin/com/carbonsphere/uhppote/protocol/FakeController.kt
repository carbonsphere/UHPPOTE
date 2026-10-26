package com.carbonsphere.uhppote.protocol

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

const val TEST_SERIAL = 0x11223344L // setSn("11223344") in the PHP library

/**
 * Answers requests on a loopback port. [reply] builds the answer (null = no
 * answer); [replies] lets one request get several answers. Requests are
 * recorded as they arrive and answered after [delayMs], so overlapping
 * requests show up in [maxInFlight].
 */
class FakeController(
    private val delayMs: Long = 0,
    private val replies: (Packet) -> List<Packet> = { emptyList() },
) : AutoCloseable {
    constructor(reply: (Packet) -> Packet?) : this(0, { req -> listOfNotNull(reply(req)) })

    val socket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = socket.localPort

    @Volatile var last: Packet = Packet()
    val received = AtomicInteger()
    private val inFlight = AtomicInteger()
    val maxInFlight = AtomicInteger()

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val thread = Thread {
        val buf = ByteArray(1024)
        while (!socket.isClosed) {
            val dp = DatagramPacket(buf, buf.size)
            try {
                socket.receive(dp)
            } catch (e: SocketException) {
                return@Thread
            }
            val req = Packet.fromBytes(dp.data.copyOf(dp.length), dp.length) ?: continue
            last = req
            received.incrementAndGet()
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            val from = dp.socketAddress
            scheduler.schedule({
                for (r in replies(req)) {
                    try {
                        socket.send(DatagramPacket(r.bytes, r.bytes.size, from))
                    } catch (e: SocketException) {
                        // closed
                    }
                }
                inFlight.decrementAndGet()
            }, delayMs, TimeUnit.MILLISECONDS)
        }
    }.apply { isDaemon = true; start() }

    fun controller(timeoutMs: Int = 1000, sockets: SocketFactory = DEFAULT_SOCKETS) =
        Controller(TEST_SERIAL, InetAddress.getByName("127.0.0.1"), port, timeoutMs, sockets)

    override fun close() {
        socket.close()
        scheduler.shutdownNow()
    }
}

/** Replies with the request header and a success byte. */
fun echoResult(req: Packet): Packet = Packet.request(req.command, req.serial).apply { this[8] = 0x01 }
