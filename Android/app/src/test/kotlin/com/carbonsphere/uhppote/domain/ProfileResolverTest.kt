package com.carbonsphere.uhppote.domain

import com.carbonsphere.uhppote.data.AppState
import com.carbonsphere.uhppote.data.addProfile
import com.carbonsphere.uhppote.data.device
import com.carbonsphere.uhppote.data.ip4
import com.carbonsphere.uhppote.data.setActive
import com.carbonsphere.uhppote.net.LocalNet
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ProfileResolverTest {
    private val office = AppState()
        .addProfile("Office", listOf(device(1, "192.168.1.50")), "office")
        .addProfile("Warehouse", listOf(device(2, "10.0.0.50")), "warehouse")

    @Test
    fun noProfilesChoose() = runTest {
        val r = resolveProfile(AppState(), null, probe = { fail("no probe") }, search = { fail("no search") })
        assertEquals(Resolution.Choose, r)
    }

    @Test
    fun singleSubnetMatchWinsWithoutProbing() = runTest {
        val r = resolveProfile(office, LocalNet(ip4("10.0.0.7"), 24), probe = { fail("no probe") }, search = { fail("no search") })
        assertEquals(Resolution.Active("warehouse", emptyMap()), r)
    }

    @Test
    fun twoSubnetMatchesFallsBackToProbe() = runTest {
        val s = office.addProfile("Office 2", listOf(device(3, "192.168.1.60")), "office2")
        val r = resolveProfile(s, LocalNet(ip4("192.168.1.7"), 24), probe = { it.serial == 3L }, search = { fail("no search") })
        assertEquals(Resolution.Active("office2", emptyMap()), r)
    }

    @Test
    fun probePrefersLastActive() = runTest {
        val s = office.setActive("warehouse")
        val r = resolveProfile(s, null, probe = { true }, search = { fail("no search") })
        assertEquals(Resolution.Active("warehouse", emptyMap()), r)
    }

    @Test
    fun nothingAnswersSearchFindsMovedController() = runTest {
        val r = resolveProfile(office, null, probe = { false }, search = { listOf(device(9), device(2, "10.0.0.77")) })
        assertEquals(Resolution.Active("warehouse", mapOf(2L to "10.0.0.77")), r)
    }

    @Test
    fun nothingFoundChoose() = runTest {
        val r = resolveProfile(office, null, probe = { false }, search = { listOf(device(9)) })
        assertEquals(Resolution.Choose, r)
    }

    @Test
    fun searchFailureChoose() = runTest {
        val r = resolveProfile(office, null, probe = { false }, search = { throw java.io.IOException("no wifi") })
        assertEquals(Resolution.Choose, r)
    }
}
