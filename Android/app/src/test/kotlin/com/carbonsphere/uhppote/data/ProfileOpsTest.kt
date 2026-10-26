package com.carbonsphere.uhppote.data

import com.carbonsphere.uhppote.protocol.Device
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

fun ip4(s: String) = InetAddress.getByName(s) as Inet4Address

fun device(serial: Long, ip: String = "192.168.1.50") =
    Device(serial, ip4(ip), ip4("255.255.255.0"), ip4("192.168.1.1"), "00:11:22:33:44:55", "6.62", "2018-08-16")

class ProfileOpsTest {
    @Test
    fun addProfileStoresDevicesWithDoorCount() {
        val s = AppState().addProfile("Office", listOf(device(223456789)), newId = "p1")
        val c = s.profiles.single().controllers.single()
        assertEquals("Office", s.profiles.single().name)
        assertEquals(2, c.doorCount)
        assertEquals("192.168.1.50", c.ip)
    }

    @Test
    fun addToProfileRefreshesIpButKeepsNames() {
        val s = AppState().addProfile("Office", listOf(device(423206957)), newId = "p1")
            .renameController(423206957, "Main board")
            .setDoorName(423206957, 1, "Front door")
            .addToProfile("p1", listOf(device(423206957, ip = "192.168.1.77"), device(423206958)))
        val cs = s.profiles.single().controllers
        assertEquals(2, cs.size)
        assertEquals("192.168.1.77", cs[0].ip)
        assertEquals("Main board", cs[0].name)
        assertEquals("Front door", cs[0].doorName(1, "Door 1"))
        assertEquals("Door 2", cs[0].doorName(2, "Door 2"))
    }

    @Test
    fun deleteActiveProfileClearsActive() {
        val s = AppState().addProfile("A", listOf(device(1)), "a").setActive("a").deleteProfile("a")
        assertNull(s.activeProfileId)
        assertEquals(emptyList(), s.profiles)
    }

    @Test
    fun updateControllerIpTouchesEveryProfile() {
        val s = AppState()
            .addProfile("A", listOf(device(42)), "a")
            .addProfile("B", listOf(device(42)), "b")
            .updateControllerIp(42, "10.0.0.9")
        assertEquals(listOf("10.0.0.9", "10.0.0.9"), s.profiles.map { it.controllers.single().ip })
    }

    @Test
    fun setDoorCountClampsTo1to4() {
        val s = AppState().addProfile("A", listOf(device(423206957)), "a")
        assertEquals(1, s.setDoorCount(423206957, 0).findController(423206957)!!.doorCount)
        assertEquals(4, s.setDoorCount(423206957, 9).findController(423206957)!!.doorCount)
        assertEquals(2, s.setDoorCount(423206957, 2).findController(423206957)!!.doorCount)
    }

    @Test
    fun moveAndRenameProfile() {
        val s = AppState().addProfile("A", emptyList(), "a").addProfile("B", emptyList(), "b")
            .moveProfile("b", 0).renameProfile("a", "Warehouse")
        assertEquals(listOf("B", "Warehouse"), s.profiles.map { it.name })
    }

    @Test
    fun activeProfileAndRemoveController() {
        val s = AppState().addProfile("A", listOf(device(1), device(2)), "a").setActive("a").removeController("a", 1)
        assertEquals(listOf(2L), s.activeProfile()!!.controllers.map { it.serial })
    }
}
