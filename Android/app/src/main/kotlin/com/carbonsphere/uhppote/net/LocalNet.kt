package com.carbonsphere.uhppote.net

import java.net.Inet4Address
import java.net.InetAddress

/** This device's IPv4 address on a network and the network's prefix length. */
data class LocalNet(val address: Inet4Address, val prefixLength: Int) {
    private val mask: Int = if (prefixLength <= 0) 0 else -1 shl (32 - prefixLength.coerceAtMost(32))

    /** Whether ip (dotted text) is on this network. */
    fun contains(ip: String): Boolean {
        val other = parseIpv4(ip) ?: return false
        return (toInt(other) and mask) == (toInt(address) and mask)
    }

    /** The network's directed broadcast address. */
    fun broadcast(): Inet4Address = fromInt(toInt(address) or mask.inv())
}

/** ip as an IPv4 address if it is a dotted IPv4 literal, else null (no DNS lookups). */
fun parseIpv4(ip: String): Inet4Address? {
    val parts = ip.trim().split(".")
    if (parts.size != 4) return null
    val bytes = ByteArray(4)
    for ((i, p) in parts.withIndex()) {
        val n = p.toIntOrNull() ?: return null
        if (n !in 0..255 || p.isEmpty() || p.length > 3) return null
        bytes[i] = n.toByte()
    }
    return InetAddress.getByAddress(bytes) as Inet4Address
}

private fun toInt(a: Inet4Address): Int =
    a.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }

private fun fromInt(v: Int): Inet4Address =
    InetAddress.getByAddress(ByteArray(4) { (v shr (24 - 8 * it)).toByte() }) as Inet4Address
