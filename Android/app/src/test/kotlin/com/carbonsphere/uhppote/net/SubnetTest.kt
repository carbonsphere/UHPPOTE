package com.carbonsphere.uhppote.net

import com.carbonsphere.uhppote.data.ip4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubnetTest {
    @Test
    fun localNetContains() {
        val n = LocalNet(ip4("192.168.1.20"), 24)
        assertTrue(n.contains("192.168.1.50"))
        assertFalse(n.contains("192.168.2.50"))
        assertFalse(n.contains("not an ip"))
    }

    @Test
    fun localNetBroadcast() {
        assertEquals("10.0.255.255", LocalNet(ip4("10.0.5.9"), 16).broadcast().hostAddress)
        assertEquals("192.168.1.255", LocalNet(ip4("192.168.1.20"), 24).broadcast().hostAddress)
    }
}
