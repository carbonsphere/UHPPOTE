package com.carbonsphere.uhppote.domain

import com.carbonsphere.uhppote.data.AppState
import com.carbonsphere.uhppote.net.parseIpv4
import com.carbonsphere.uhppote.protocol.Device
import java.net.Inet4Address

/**
 * The site newly found controllers most likely belong to: one that already
 * has one of them, else one with a controller on the same subnet (the active
 * site first). Null means a new site.
 */
fun defaultSaveTarget(state: AppState, found: List<Device>): String? {
    val sites = state.profiles.sortedBy { if (it.id == state.activeProfileId) 0 else 1 }
    val serials = found.map { it.serial }.toSet()
    sites.firstOrNull { p -> p.controllers.any { it.serial in serials } }?.let { return it.id }
    return sites.firstOrNull { p ->
        p.controllers.any { c ->
            val ip = parseIpv4(c.ip) ?: return@any false
            found.any { d -> sameSubnet(ip, d.ip, d.mask) }
        }
    }?.id
}

private fun sameSubnet(a: Inet4Address, b: Inet4Address, mask: Inet4Address): Boolean {
    val m = mask.address
    return a.address.indices.all { i -> (a.address[i].toInt() and m[i].toInt()) == (b.address[i].toInt() and m[i].toInt()) }
}
