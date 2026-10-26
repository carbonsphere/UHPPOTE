package com.carbonsphere.uhppote.protocol

import java.net.Inet4Address
import java.time.LocalDateTime

/** The commands a controller understands; [Controller] is the real one. */
interface ControllerApi {
    val serial: Long
    suspend fun status(): Status
    suspend fun getTime(): LocalDateTime
    suspend fun setTime(t: LocalDateTime): LocalDateTime?
    suspend fun openDoor(door: Int): Boolean
    suspend fun getDoorControl(door: Int): DoorControl
    suspend fun setDoorControl(dc: DoorControl): DoorControl
    suspend fun getListener(): Listener
    suspend fun setListener(l: Listener): Boolean
    suspend fun getCardCount(): Long
    suspend fun getCard(number: Long): Card?
    suspend fun getCardByIndex(index: Long): CardSlot
    suspend fun putCard(card: Card): Boolean
    suspend fun deleteCard(number: Long): Boolean
    suspend fun deleteAllCards(): Boolean
    suspend fun getEventIndex(): Long
    suspend fun setEventIndex(index: Long): Boolean
    suspend fun getEvent(index: Long): Event
    suspend fun setInterlock(mode: Int): Boolean
    suspend fun setKeypads(enabled: List<Boolean>): Boolean
    suspend fun setAddress(ip: Inet4Address, mask: Inet4Address, gateway: Inet4Address)
}
