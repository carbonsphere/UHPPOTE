package com.carbonsphere.uhppote.protocol

import java.net.DatagramSocket
import java.net.InetAddress
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControllerTest {
    private val fakes = mutableListOf<FakeController>()

    private fun fake(reply: (Packet) -> Packet?) = FakeController(reply).also { fakes += it }

    @AfterTest
    fun closeFakes() = fakes.forEach { it.close() }

    @Test
    fun openDoorMatchesPHP() = runBlocking {
        val f = fake(::echoResult)
        assertTrue(f.controller().openDoor(3))
        assertEquals(expectHex("17400000 44332211 03"), f.last.plainHex())
    }

    @Test
    fun putCardMatchesPHP() = runBlocking {
        val f = fake(::echoResult)
        val card = Card(10012345, LocalDate.of(2019, 5, 22), LocalDate.of(2020, 5, 22), listOf(1, 1, 0, 2))
        assertTrue(f.controller().putCard(card))
        assertEquals(expectHex("17500000 44332211 B9C69800 20190522 20200522 01010002"), f.last.plainHex())
    }

    @Test
    fun setListenerMatchesPHP() = runBlocking {
        val f = fake(::echoResult)
        f.controller().setListener(Listener(ip("192.168.1.2"), 60002, 0))
        assertEquals(expectHex("17900000 44332211 C0A80102 62EA 00"), f.last.plainHex())
    }

    @Test
    fun setTimeMatchesPHP() = runBlocking {
        val f = fake { req -> Packet.request(req.command, req.serial).also { req.bytes.copyInto(it.bytes, 8, 8, 15) } }
        val want = LocalDateTime.of(2019, 11, 1, 12, 34, 56)
        assertEquals(want, f.controller().setTime(want))
        assertEquals(expectHex("17300000 44332211 20191101123456"), f.last.plainHex())
    }

    @Test
    fun deleteAllAndEventIndexSendMagic() = runBlocking {
        val f = fake(::echoResult)
        val c = f.controller()
        c.deleteAllCards()
        assertEquals(expectHex("17540000 44332211 55AAAA55"), f.last.plainHex())
        c.setEventIndex(7)
        assertEquals(expectHex("17B20000 44332211 07000000 55AAAA55"), f.last.plainHex())
    }

    @Test
    fun doorControl() = runBlocking {
        val f = fake { req ->
            Packet.request(req.command, req.serial).apply {
                this[8] = req[8]; this[9] = DoorMode.CONTROLLED; this[10] = 5
            }
        }
        val c = f.controller()
        assertEquals(DoorControl(2, DoorMode.CONTROLLED, 5), c.getDoorControl(2))
        assertEquals(expectHex("17820000 44332211 02"), f.last.plainHex())
        c.setDoorControl(DoorControl(1, DoorMode.CONTROLLED, 5))
        assertEquals(expectHex("17800000 44332211 01 03 05"), f.last.plainHex())
    }

    @Test
    fun getCard() = runBlocking {
        val f = fake { req ->
            Packet.request(req.command, req.serial).apply {
                if (req.u32(8) == 10012345L) {
                    req.bytes.copyInto(bytes, 8, 8, 12)
                    putBcd(12, "2024010120291231")
                    this[20] = 1; this[21] = 0; this[22] = 5; this[23] = 1
                }
            }
        }
        val card = f.controller().getCard(10012345)!!
        assertEquals(LocalDate.of(2024, 1, 1), card.from)
        assertEquals(LocalDate.of(2029, 12, 31), card.to)
        assertEquals(listOf(1, 0, 5, 1), card.doors)
        assertNull(f.controller().getCard(42))
    }

    @Test
    fun getCardByIndexSendsIndex() = runBlocking {
        val f = fake { req ->
            Packet.request(req.command, req.serial).apply {
                putU32(8, 10012345)
                putBcd(12, "2024010120291231")
                this[20] = 1; this[21] = 1; this[22] = 0; this[23] = 2
            }
        }
        val slot = f.controller().getCardByIndex(7)
        assertEquals(expectHex("175C0000 44332211 07000000"), f.last.plainHex())
        assertIs<CardSlot.Found>(slot)
        assertEquals(10012345L, slot.card.number)
        assertEquals(listOf(1, 1, 0, 2), slot.card.doors)
    }

    @Test
    fun getCardByIndexDeletedAndEnd() = runBlocking {
        val f = fake { req ->
            Packet.request(req.command, req.serial).apply {
                putU32(8, if (req.u32(8) == 1L) 0xFFFFFFFFL else 0L)
            }
        }
        assertEquals(CardSlot.Deleted, f.controller().getCardByIndex(1))
        assertEquals(CardSlot.End, f.controller().getCardByIndex(2))
    }

    @Test
    fun replyFromOtherSerialIgnored() = runBlocking {
        val f = fake { req -> Packet.request(req.command, req.serial + 1) }
        assertFailsWith<NoReplyException> { f.controller(timeoutMs = 200).openDoor(1) }
        Unit
    }

    @Test
    fun invalidInputsRejected() = runBlocking {
        val c = Controller(TEST_SERIAL, InetAddress.getByName("127.0.0.1"))
        assertFailsWith<IllegalArgumentException> { c.openDoor(5) }
        assertFailsWith<IllegalArgumentException> { c.deleteCard(0) }
        Unit
    }

    @Test
    fun concurrentRequestsAreSerialised() = runBlocking {
        val f = FakeController(delayMs = 50) { req -> listOf(echoResult(req)) }.also { fakes += it }
        val c = f.controller()
        val results = (0 until 5).map { i -> async { c.openDoor(i % 4 + 1) } }.awaitAll()
        assertEquals(List(5) { true }, results)
        assertEquals(5, f.received.get())
        assertEquals(1, f.maxInFlight.get())
    }

    @Test
    fun controllerUsesInjectedSocketFactory() = runBlocking {
        val f = fake(::echoResult)
        val created = AtomicInteger()
        f.controller(sockets = SocketFactory { created.incrementAndGet(); DatagramSocket() }).status()
        assertEquals(1, created.get())
    }
}

fun ip(s: String) = InetAddress.getByName(s) as java.net.Inet4Address
