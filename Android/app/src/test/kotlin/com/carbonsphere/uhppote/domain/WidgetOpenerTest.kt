package com.carbonsphere.uhppote.domain

import com.carbonsphere.uhppote.data.device
import com.carbonsphere.uhppote.data.toSaved
import com.carbonsphere.uhppote.protocol.NoReplyException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetOpenerTest {
    private val serial = 423206957L
    private val saved = device(serial, "192.168.1.50").toSaved()

    @Test
    fun opensAtSavedIp() = runTest {
        val asked = mutableListOf<String>()
        val r = openFromWidget(saved, 2, open = { ip -> asked += ip; true }, search = { error("no search expected") })
        assertEquals(WidgetResult.Opened, r)
        assertEquals(listOf("192.168.1.50"), asked)
    }

    @Test
    fun refusedWhenControllerSaysNo() = runTest {
        assertEquals(WidgetResult.Refused, openFromWidget(saved, 1, open = { false }, search = { emptyList() }))
    }

    @Test
    fun noReplyThenFoundAtNewIp() = runTest {
        val asked = mutableListOf<String>()
        val r = openFromWidget(
            saved, 1,
            open = { ip -> asked += ip; if (ip == "192.168.1.50") throw NoReplyException() else true },
            search = { listOf(device(serial, "192.168.1.77")) },
        )
        assertEquals(WidgetResult.OpenedAtNewIp("192.168.1.77"), r)
        assertEquals(listOf("192.168.1.50", "192.168.1.77"), asked)
    }

    @Test
    fun noReplyAndNotFound() = runTest {
        var searches = 0
        val r = openFromWidget(saved, 1, open = { throw NoReplyException() }, search = { searches++; emptyList() })
        assertEquals(WidgetResult.NoReply, r)
        assertEquals(1, searches)
    }

    @Test
    fun searchFindsOtherSerialOnly() = runTest {
        val r = openFromWidget(saved, 1, open = { throw NoReplyException() }, search = { listOf(device(223456789, "192.168.1.77")) })
        assertEquals(WidgetResult.NoReply, r)
    }

    @Test
    fun staleOpeningDoesNotBlock() {
        assertEquals(false, openingInProgress("idle", null, 100_000))
        assertEquals(true, openingInProgress("opening", 95_000, 100_000))
        assertEquals(false, openingInProgress("opening", 80_000, 100_000)) // stuck for 20 s
        assertEquals(false, openingInProgress("opening", null, 100_000)) // left by an old version / killed process
    }

    @Test
    fun foundAtSameIpRetriesThere() = runTest {
        var calls = 0
        val r = openFromWidget(saved, 1, open = { calls++; if (calls == 1) throw NoReplyException() else true }, search = { listOf(device(serial, "192.168.1.50")) })
        assertEquals(WidgetResult.Opened, r)
        assertEquals(2, calls)
    }
}
