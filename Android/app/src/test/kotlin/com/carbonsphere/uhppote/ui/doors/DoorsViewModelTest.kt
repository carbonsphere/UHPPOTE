package com.carbonsphere.uhppote.ui.doors

import com.carbonsphere.uhppote.data.AppState
import com.carbonsphere.uhppote.data.Profile
import com.carbonsphere.uhppote.data.addProfile
import com.carbonsphere.uhppote.data.device
import com.carbonsphere.uhppote.data.setDoorName
import com.carbonsphere.uhppote.net.ActivityLog
import com.carbonsphere.uhppote.protocol.NoReplyException
import com.carbonsphere.uhppote.protocol.Status
import com.carbonsphere.uhppote.ui.FakeApi
import com.carbonsphere.uhppote.ui.MainDispatcherRule
import com.carbonsphere.uhppote.ui.status
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DoorsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun profile(vararg serials: Long): Profile =
        AppState().addProfile("Office", serials.map { device(it) }, "p").setDoorName(serials.first(), 1, "Front door").profiles.single()

    private fun vm(p: Profile, api: (Long) -> FakeApi) =
        DoorsViewModel(MutableStateFlow(p), ActivityLog(), controllerFor = { api(it.serial) })

    @Test
    fun tilesFollowDoorCount() = runTest(main.dispatcher) {
        val m = vm(profile(223456789)) { FakeApi(it) }
        val doors = m.tiles.value.single().doors
        assertEquals(listOf(1, 2), doors.map { it.door })
        assertEquals("Front door", doors[0].name)
        assertEquals("Door 2", doors[1].name)
    }

    @Test
    fun openSuccessMarksOpened() = runTest(main.dispatcher) {
        var opened = 0
        val m = vm(profile(423206957)) { object : FakeApi(it) { override suspend fun openDoor(door: Int) = true.also { opened = door } } }
        m.open(423206957, 3)
        assertEquals(3, opened)
        assertEquals(OpenResult.OPENED, m.tiles.value.single().doors[2].lastResult)
    }

    @Test
    fun openRefusedMarksRefused() = runTest(main.dispatcher) {
        val m = vm(profile(423206957)) { object : FakeApi(it) { override suspend fun openDoor(door: Int) = false } }
        m.open(423206957, 1)
        assertEquals(OpenResult.REFUSED, m.tiles.value.single().doors[0].lastResult)
    }

    @Test
    fun openTimeoutMarksNoReply() = runTest(main.dispatcher) {
        val m = vm(profile(423206957)) { object : FakeApi(it) { override suspend fun openDoor(door: Int): Boolean = throw NoReplyException() } }
        m.open(423206957, 1)
        assertEquals(OpenResult.NO_REPLY, m.tiles.value.single().doors[0].lastResult)
    }

    @Test
    fun resultClearsAfterThreeSeconds() = runTest(main.dispatcher) {
        val m = vm(profile(423206957)) { object : FakeApi(it) { override suspend fun openDoor(door: Int) = true } }
        m.open(423206957, 1)
        testScheduler.advanceTimeBy(3100)
        assertEquals(null, m.tiles.value.single().doors[0].lastResult)
    }

    @Test
    fun pollingUpdatesSensors() = runTest(main.dispatcher) {
        val m = vm(profile(423206957)) { object : FakeApi(it) { override suspend fun status() = status(open = listOf(true, false, false, false), relays = 2) } }
        m.refresh()
        val c = m.tiles.value.single()
        assertEquals(true, c.reachable)
        assertEquals(true, c.doors[0].open)
        assertEquals(false, c.doors[1].open)
        assertEquals(true, c.doors[1].unlocked)
        assertEquals(false, c.doors[0].unlocked)
    }

    @Test
    fun pollFailureMarksUnreachableButKeepsTiles() = runTest(main.dispatcher) {
        val m = vm(profile(423206957, 223456789)) { serial ->
            object : FakeApi(serial) {
                override suspend fun status(): Status = if (serial == 223456789L) throw NoReplyException() else com.carbonsphere.uhppote.ui.status()
            }
        }
        m.refresh()
        val (a, b) = m.tiles.value
        assertEquals(true, a.reachable)
        assertEquals(false, b.reachable)
        assertEquals(2, b.doors.size)
        assertEquals(null, b.doors[0].open)
    }
}
