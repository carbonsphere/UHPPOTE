package com.carbonsphere.uhppote.ui.settings

import com.carbonsphere.uhppote.data.AppState
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.data.addProfile
import com.carbonsphere.uhppote.data.device
import com.carbonsphere.uhppote.data.findController
import com.carbonsphere.uhppote.data.ip4
import com.carbonsphere.uhppote.data.setActive
import com.carbonsphere.uhppote.data.toSaved
import com.carbonsphere.uhppote.net.ActivityLog
import com.carbonsphere.uhppote.protocol.Listener
import com.carbonsphere.uhppote.ui.FakeApi
import com.carbonsphere.uhppote.ui.FakeStore
import com.carbonsphere.uhppote.ui.MainDispatcherRule
import java.net.Inet4Address
import java.net.InetAddress
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val serial = 423206957L
    private val store = FakeStore(AppState().addProfile("Office", listOf(device(serial, "192.168.1.50")), "p"))
    private val saved: SavedController = store.state.value.findController(serial)!!

    private fun vm(
        api: FakeApi = FakeApi(serial),
        localAddress: () -> Inet4Address? = { null },
        searchNet: suspend (InetAddress?) -> List<com.carbonsphere.uhppote.protocol.Device> = { emptyList() },
    ) = SettingsViewModel(
        controller = MutableStateFlow(saved),
        store = store,
        log = ActivityLog(),
        controllerFor = { api },
        localAddress = localAddress,
        searchNet = searchNet,
    )

    @Test
    fun driftTextAheadBehind() {
        val phone = LocalDateTime.of(2024, 3, 15, 10, 0, 0)
        assertEquals("controller is 3s ahead", driftText(phone.plusSeconds(3), phone))
        assertEquals("controller is 3s behind", driftText(phone.minusSeconds(3), phone))
        assertEquals("controller is 0s ahead", driftText(phone, phone.plusNanos(400_000_000)))
        assertEquals("controller is 1h2m5s behind", driftText(phone.minusSeconds(3725), phone))
    }

    @Test
    fun saveListenerRejectsBadIp() = runTest(main.dispatcher) {
        var sent: Listener? = null
        val m = vm(api = object : FakeApi(serial) { override suspend fun setListener(l: Listener) = true.also { sent = l } })
        m.saveListener("192.168.1.300", 60001, 0)
        assertEquals(null, sent)
        assertEquals("\"192.168.1.300\" is not a valid IPv4 address", m.message.value)
        m.saveListener(" 192.168.1.2 ", 60001, 5)
        assertEquals(Listener(ip4("192.168.1.2"), 60001, 5), sent)
    }

    @Test
    fun saveListenerRejectsBadPortAndInterval() = runTest(main.dispatcher) {
        var sent: Listener? = null
        val m = vm(api = object : FakeApi(serial) { override suspend fun setListener(l: Listener) = true.also { sent = l } })
        m.saveListener("192.168.1.2", 70000, 0)
        assertEquals("Port must be 0–65535.", m.message.value)
        m.saveListener("192.168.1.2", -1, 0)
        assertEquals("Port must be 0–65535.", m.message.value)
        m.saveListener("192.168.1.2", 60001, 256)
        assertEquals("Status interval must be 0–255 s.", m.message.value)
        assertEquals(null, sent)
    }

    @Test
    fun removeControllerFromSite() = runTest(main.dispatcher) {
        var refreshed = 0
        val two = FakeStore(AppState().addProfile("Office", listOf(device(serial), device(223456789)), "p").setActive("p"))
        val m = SettingsViewModel(
            controller = MutableStateFlow(saved), store = two, log = ActivityLog(), controllerFor = { FakeApi(serial) },
            localAddress = { null }, searchNet = { emptyList() }, onDoorsChanged = { refreshed++ },
        )
        m.removeController(serial)
        assertEquals(listOf(223456789L), two.state.value.profiles.single().controllers.map { it.serial })
        assertEquals(1, refreshed)
    }

    @Test
    fun changeNetworkUpdatesStoredIpAfterSearch() = runTest(main.dispatcher) {
        var sentIp: String? = null
        val m = vm(
            api = object : FakeApi(serial) {
                override suspend fun setAddress(ip: Inet4Address, mask: Inet4Address, gateway: Inet4Address) {
                    sentIp = ip.hostAddress
                }
            },
            searchNet = { listOf(device(serial, "192.168.1.60")) },
        )
        m.changeNetwork("192.168.1.60", "255.255.255.0", "192.168.1.1")
        assertEquals("192.168.1.60", sentIp)
        assertEquals("192.168.1.50", store.state.value.findController(serial)?.ip) // waits 2 s first
        testScheduler.advanceUntilIdle()
        assertEquals("192.168.1.60", store.state.value.findController(serial)?.ip)
    }

    @Test
    fun changeNetworkResultNotShownAfterSwitch() = runTest(main.dispatcher) {
        val selected = MutableStateFlow(saved)
        val m = SettingsViewModel(
            controller = selected, store = store, log = ActivityLog(),
            controllerFor = { object : FakeApi(serial) { override suspend fun setAddress(ip: Inet4Address, mask: Inet4Address, gateway: Inet4Address) {} } },
            localAddress = { null }, searchNet = { listOf(device(serial, "192.168.1.60")) },
        )
        m.changeNetwork("192.168.1.60", "255.255.255.0", "192.168.1.1")
        selected.value = device(223456789).toSaved() // manager picks another controller while it restarts
        testScheduler.advanceUntilIdle()
        assertEquals(null, m.message.value)
        assertEquals("192.168.1.60", store.state.value.findController(serial)?.ip) // the move is still saved
    }

    @Test
    fun changeNetworkRejectsBadMaskWithoutSending() = runTest(main.dispatcher) {
        val m = vm()
        m.changeNetwork("192.168.1.60", "255.255.255", "192.168.1.1")
        assertTrue(m.message.value!!.contains("not a valid IPv4 address"))
    }

    @Test
    fun useThisDeviceDefaultsPort60001() = runTest(main.dispatcher) {
        val m = vm(localAddress = { ip4("192.168.1.77") })
        m.useThisDevice()
        assertEquals(ListenerForm("192.168.1.77", 60001, 0), m.listener.value)
        m.updateListener(m.listener.value.copy(port = 60005))
        m.useThisDevice()
        assertEquals(60005, m.listener.value.port)
    }
}
