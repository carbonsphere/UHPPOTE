package com.carbonsphere.uhppote.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.carbonsphere.uhppote.protocol.SocketFactory
import com.carbonsphere.uhppote.protocol.defaultBroadcastAddresses
import java.io.IOException
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Access to the Wi-Fi network the controllers are on. Sockets are bound to
 * Wi-Fi so that traffic doesn't go out over mobile data when the Wi-Fi has
 * no internet access.
 */
class WifiNetwork(context: Context) {
    private val app = context.applicationContext
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = app.getSystemService(WifiManager::class.java)

    @Suppress("DEPRECATION") // allNetworks: the simplest way to find the Wi-Fi network on API 26+
    private fun wifi(): Network? = connectivity?.allNetworks?.firstOrNull { n ->
        connectivity.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    /** Creates sockets bound to Wi-Fi when there is a Wi-Fi network. */
    fun socketFactory(): SocketFactory = SocketFactory {
        DatagramSocket().also { socket ->
            try {
                wifi()?.bindSocket(socket)
            } catch (e: IOException) {
                // Not bound; the system picks the route.
            }
        }
    }

    /** This device's IPv4 address and prefix on Wi-Fi, if connected. */
    fun localNet(): LocalNet? {
        val n = wifi() ?: return null
        val link = connectivity?.getLinkProperties(n) ?: return null
        val a = link.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        return LocalNet(a.address as Inet4Address, a.prefixLength)
    }

    /** Where to send a search: the limited broadcast plus the Wi-Fi network's broadcast address. */
    fun broadcastTargets(): List<InetAddress> {
        val net = localNet() ?: return defaultBroadcastAddresses()
        return listOf(InetAddress.getByName("255.255.255.255"), net.broadcast()).distinct()
    }

    /** Runs block while holding a multicast lock; some devices drop broadcast replies without it. */
    suspend fun <T> withMulticastLock(block: suspend () -> T): T {
        val lock = wifiManager?.createMulticastLock("uhppote-search")?.apply { setReferenceCounted(false) }
        try {
            lock?.acquire()
        } catch (e: SecurityException) {
            // Permission missing; search may still work.
        }
        try {
            return block()
        } finally {
            if (lock?.isHeld == true) lock.release()
        }
    }
}
