package com.carbonsphere.uhppote.domain

import com.carbonsphere.uhppote.data.AppState
import com.carbonsphere.uhppote.data.addProfile
import com.carbonsphere.uhppote.data.device
import com.carbonsphere.uhppote.data.setActive
import kotlin.test.Test
import kotlin.test.assertEquals

class SaveTargetTest {
    private val state = AppState()
        .addProfile("Office", listOf(device(1, "192.168.1.50")), "office")
        .addProfile("Warehouse", listOf(device(2, "10.0.0.20")), "warehouse")
        .setActive("office")

    @Test
    fun knownSerialPicksItsSite() =
        assertEquals("warehouse", defaultSaveTarget(state, listOf(device(2, "10.0.5.5"))))

    @Test
    fun sameSubnetPicksThatSiteActiveFirst() {
        assertEquals("office", defaultSaveTarget(state, listOf(device(9, "192.168.1.77"))))
        assertEquals("warehouse", defaultSaveTarget(state, listOf(device(9, "10.0.0.99").copy(mask = com.carbonsphere.uhppote.data.ip4("255.255.255.0")))))
    }

    @Test
    fun unknownNetworkMeansNewSite() =
        assertEquals(null, defaultSaveTarget(state, listOf(device(9, "172.16.0.5"))))

    @Test
    fun noSitesMeansNewSite() =
        assertEquals(null, defaultSaveTarget(AppState(), listOf(device(9))))
}
