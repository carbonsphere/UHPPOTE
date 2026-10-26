package com.carbonsphere.uhppote.ui

import com.carbonsphere.uhppote.protocol.Card
import com.carbonsphere.uhppote.protocol.CardSlot
import com.carbonsphere.uhppote.protocol.ControllerApi
import com.carbonsphere.uhppote.protocol.DoorControl
import com.carbonsphere.uhppote.protocol.Event
import com.carbonsphere.uhppote.protocol.Listener
import com.carbonsphere.uhppote.protocol.Status
import java.net.Inet4Address
import java.time.LocalDateTime

/** A controller whose every call fails unless a test overrides it. */
open class FakeApi(override val serial: Long = 1) : ControllerApi {
    override suspend fun status(): Status = TODO()
    override suspend fun getTime(): LocalDateTime = TODO()
    override suspend fun setTime(t: LocalDateTime): LocalDateTime? = TODO()
    override suspend fun openDoor(door: Int): Boolean = TODO()
    override suspend fun getDoorControl(door: Int): DoorControl = TODO()
    override suspend fun setDoorControl(dc: DoorControl): DoorControl = TODO()
    override suspend fun getListener(): Listener = TODO()
    override suspend fun setListener(l: Listener): Boolean = TODO()
    override suspend fun getCardCount(): Long = TODO()
    override suspend fun getCard(number: Long): Card? = TODO()
    override suspend fun getCardByIndex(index: Long): CardSlot = TODO()
    override suspend fun putCard(card: Card): Boolean = TODO()
    override suspend fun deleteCard(number: Long): Boolean = TODO()
    override suspend fun deleteAllCards(): Boolean = TODO()
    override suspend fun getEventIndex(): Long = TODO()
    override suspend fun setEventIndex(index: Long): Boolean = TODO()
    override suspend fun getEvent(index: Long): Event = TODO()
    override suspend fun setInterlock(mode: Int): Boolean = TODO()
    override suspend fun setKeypads(enabled: List<Boolean>): Boolean = TODO()
    override suspend fun setAddress(ip: Inet4Address, mask: Inet4Address, gateway: Inet4Address): Unit = TODO()
}

val EMPTY_EVENT = Event(0, 0, false, 0, 0, 0, null, 0)

fun status(open: List<Boolean> = List(4) { false }, relays: Int = 0, last: Event = EMPTY_EVENT) =
    Status(last, open, List(4) { false }, 0, LocalDateTime.of(2024, 3, 15, 10, 15, 30), 42, 0, relays, 0)
