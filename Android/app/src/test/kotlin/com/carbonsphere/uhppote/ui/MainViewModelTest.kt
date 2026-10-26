package com.carbonsphere.uhppote.ui

import com.carbonsphere.uhppote.data.AppState
import com.carbonsphere.uhppote.data.addProfile
import com.carbonsphere.uhppote.data.device
import com.carbonsphere.uhppote.domain.Resolution
import com.carbonsphere.uhppote.net.ActivityLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MainViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val saved = AppState().addProfile("Office", listOf(device(1), device(2)), "office")

    private fun vm(store: FakeStore, resolution: Resolution, found: List<com.carbonsphere.uhppote.protocol.Device> = emptyList()) =
        MainViewModel(store, ActivityLog(), resolve = { resolution }, searchNet = { found }, newId = { "new" })

    @Test
    fun launchActiveGoesToConnected() = runTest {
        val m = vm(FakeStore(saved), Resolution.Active("office", emptyMap()))
        val s = m.screen.value
        assertIs<MainScreen.Connected>(s)
        assertEquals("office", s.profile.id)
        assertEquals(1L, m.selectedController.value?.serial)
    }

    @Test
    fun longAbsenceReResolvesSite() = runTest {
        val two = FakeStore(saved.addProfile("Warehouse", listOf(device(3)), "warehouse"))
        var resolution: Resolution = Resolution.Active("office", emptyMap())
        var calls = 0
        val m = MainViewModel(two, ActivityLog(), resolve = { calls++; resolution }, searchNet = { emptyList() }, newId = { "new" })
        assertEquals("office", (m.screen.value as MainScreen.Connected).profile.id)

        resolution = Resolution.Active("warehouse", emptyMap())
        m.onBackground(nowMs = 1_000)
        m.onForeground(nowMs = 31_000) // 30 s away: same visit
        assertEquals(1, calls)
        m.onBackground(nowMs = 40_000)
        m.onForeground(nowMs = 400_000) // 6 min away: maybe another site
        assertEquals(2, calls)
        assertEquals("warehouse", (m.screen.value as MainScreen.Connected).profile.id)
        assertEquals(3L, m.selectedController.value?.serial)
    }

    @Test
    fun reResolveWithNoAnswerKeepsSite() = runTest {
        var resolution: Resolution = Resolution.Active("office", emptyMap())
        val m = MainViewModel(FakeStore(saved), ActivityLog(), resolve = { resolution }, searchNet = { emptyList() }, newId = { "new" })
        resolution = Resolution.Choose
        m.onBackground(nowMs = 0)
        m.onForeground(nowMs = 600_000)
        assertEquals("office", (m.screen.value as MainScreen.Connected).profile.id)
    }

    @Test
    fun launchChooseGoesToProfiles() = runTest {
        assertEquals(MainScreen.Profiles, vm(FakeStore(saved), Resolution.Choose).screen.value)
    }

    @Test
    fun launchAppliesIpUpdates() = runTest {
        val store = FakeStore(saved)
        vm(store, Resolution.Active("office", mapOf(2L to "192.168.1.77")))
        assertEquals("192.168.1.77", store.state.value.profiles.single().controllers[1].ip)
        assertEquals("office", store.state.value.activeProfileId)
    }

    @Test
    fun saveFoundCreatesNewProfileAndActivates() = runTest {
        val store = FakeStore()
        val m = vm(store, Resolution.Choose)
        m.saveFound(listOf(device(7)), profileId = null, newProfileName = "Warehouse")
        assertEquals("Warehouse", store.state.value.profiles.single().name)
        assertEquals("new", store.state.value.activeProfileId)
        assertIs<MainScreen.Connected>(m.screen.value)
    }

    @Test
    fun searchFindsDevices() = runTest {
        val m = vm(FakeStore(), Resolution.Choose, found = listOf(device(7)))
        m.search("")
        assertEquals(SearchState.Found(listOf(device(7))), m.searchState.value)
    }

    @Test
    fun searchInvalidIpFails() = runTest {
        val m = vm(FakeStore(), Resolution.Choose)
        m.search("300.1.1.1")
        assertIs<SearchState.Failed>(m.searchState.value)
    }

    @Test
    fun selectControllerSwitches() = runTest {
        val m = vm(FakeStore(saved), Resolution.Active("office", emptyMap()))
        m.selectController(2)
        assertEquals(2L, m.selectedController.value?.serial)
    }

    @Test
    fun staleResultIsDropped() = runTest {
        var current: Long? = 1
        val gate = CompletableDeferred<Unit>()
        var delivered = false
        val log = ActivityLog()
        val job = runOn(1, "Read status", log, { current }, block = { gate.await(); "status" }, onResult = { delivered = true })
        current = 2
        gate.complete(Unit)
        job.join()
        assertFalse(delivered)
        assertTrue(log.lines.value.isNotEmpty())
    }

    @Test
    fun runOnReportsErrors() = runTest {
        var error: String? = null
        val log = ActivityLog()
        runOn(1, "Open door 1", log, { 1L }, block = { throw ControllerRefused() }, onResult = {}, onError = { error = it }).join()
        assertEquals("Controller rejected the command", error)
        assertEquals("✖ Open door 1: Controller rejected the command", log.lines.value.single().text)
    }
}
