package com.carbonsphere.uhppote.net

import com.carbonsphere.uhppote.data.device
import com.carbonsphere.uhppote.data.toSaved
import com.carbonsphere.uhppote.ui.FakeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ControllerCacheTest {
    @Test
    fun sameClientForSameSerialAndIp() {
        var created = 0
        val cache = ControllerCache { created++; FakeApi(it.serial) }
        val a = cache.get(device(1, "192.168.1.50").toSaved())
        val b = cache.get(device(1, "192.168.1.50").toSaved().copy(name = "Renamed"))
        assertSame(a, b)
        assertEquals(1, created)
    }

    @Test
    fun newClientWhenIpChangesOrOtherSerial() {
        val cache = ControllerCache { FakeApi(it.serial) }
        val a = cache.get(device(1, "192.168.1.50").toSaved())
        assertNotSame(a, cache.get(device(1, "192.168.1.60").toSaved()))
        assertNotSame(a, cache.get(device(2, "192.168.1.50").toSaved()))
    }
}
