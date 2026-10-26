package com.carbonsphere.uhppote.protocol

import java.io.IOException
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/**
 * Sends a search request to every address in targets (broadcast addresses or
 * single controller IPs) and collects every controller that answers within
 * timeoutMs, sorted by serial number.
 */
suspend fun search(
    targets: List<InetAddress>,
    timeoutMs: Int = 2000,
    port: Int = PORT,
    sockets: SocketFactory = DEFAULT_SOCKETS,
    log: PacketLog? = null,
): List<Device> = runInterruptible(Dispatchers.IO) {
    sockets.create().use { socket ->
        socket.broadcast = true
        val req = Packet.request(Cmd.SEARCH, 0)
        var sent = 0
        var lastError: IOException? = null
        for (ip in targets) {
            log?.log("TX", ip, req)
            try {
                socket.send(DatagramPacket(req.bytes, req.bytes.size, ip, port))
                sent++
            } catch (e: IOException) {
                lastError = e
            }
        }
        if (sent == 0) throw IOException("Could not send search request: ${lastError?.message}", lastError)

        val found = sortedMapOf<Long, Device>()
        val buf = ByteArray(1024)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) break
            socket.soTimeout = left.toInt()
            val dp = DatagramPacket(buf, buf.size)
            try {
                socket.receive(dp)
            } catch (e: SocketTimeoutException) {
                break
            } catch (e: IOException) {
                // An ICMP "port unreachable" from an earlier send can show up
                // as a read error; ignore it and keep listening.
                continue
            }
            val p = Packet.fromBytes(dp.data, dp.length) ?: continue
            if (p.command != Cmd.SEARCH) continue
            log?.log("RX", dp.address, p)
            val d = parseDevice(p)
            found[d.serial] = d
        }
        found.values.toList()
    }
}

/**
 * 255.255.255.255 plus the directed broadcast address of every active IPv4
 * interface, so controllers on any attached network are reached.
 */
fun defaultBroadcastAddresses(): List<InetAddress> {
    val out = linkedSetOf<InetAddress>(InetAddress.getByName("255.255.255.255"))
    val ifaces = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
    } catch (e: IOException) {
        emptyList()
    }
    for (ifc in ifaces) {
        if (!ifc.isUp || ifc.isLoopback) continue
        for (a in ifc.interfaceAddresses) {
            if (a.address is Inet4Address) a.broadcast?.let { out += it }
        }
    }
    return out.toList()
}
