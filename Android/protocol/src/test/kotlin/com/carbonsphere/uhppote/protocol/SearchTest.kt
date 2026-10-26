package com.carbonsphere.uhppote.protocol

import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTest {
    private fun searchReply(serial: Long): Packet =
        packet("17940000 00000000 C0A80132 FFFFFF00 C0A80101 001122334455 0662 20180816").apply { putU32(4, serial) }

    @Test
    fun searchCollectsAndDeduplicates() = runBlocking {
        FakeController(replies = { req ->
            if (req.command == Cmd.SEARCH) listOf(searchReply(2), searchReply(1), searchReply(2)) else emptyList()
        }).use { f ->
            val found = search(listOf(InetAddress.getByName("127.0.0.1")), timeoutMs = 500, port = f.port)
            assertEquals(listOf(1L, 2L), found.map { it.serial })
            assertEquals(expectHex("17940000"), f.last.plainHex())
        }
    }

    @Test
    fun searchIgnoresNonSearchReplies() = runBlocking {
        FakeController(replies = { listOf(Packet.request(Cmd.STATUS, 5)) }).use { f ->
            val found = search(listOf(InetAddress.getByName("127.0.0.1")), timeoutMs = 300, port = f.port)
            assertEquals(emptyList(), found)
        }
    }

    @Test
    fun defaultBroadcastAddressesStartsWithLimitedBroadcast() {
        assertEquals("255.255.255.255", defaultBroadcastAddresses().first().hostAddress)
    }
}
