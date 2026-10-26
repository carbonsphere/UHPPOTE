package com.carbonsphere.uhppote.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbonsphere.uhppote.data.AppState
import com.carbonsphere.uhppote.data.AppStore
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.data.renameController
import com.carbonsphere.uhppote.data.setDoorCount
import com.carbonsphere.uhppote.data.setDoorName
import com.carbonsphere.uhppote.data.updateControllerIp
import com.carbonsphere.uhppote.net.ActivityLog
import com.carbonsphere.uhppote.net.parseIpv4
import com.carbonsphere.uhppote.protocol.ControllerApi
import com.carbonsphere.uhppote.protocol.Device
import com.carbonsphere.uhppote.protocol.Listener
import com.carbonsphere.uhppote.ui.formatTime
import com.carbonsphere.uhppote.ui.orRefused
import com.carbonsphere.uhppote.ui.runOn
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The controller clock next to this device's clock. */
data class TimeInfo(val controller: String, val phone: String, val drift: String)

/** The event listener fields as edited on screen. */
data class ListenerForm(val ip: String = "", val port: Int = 0, val interval: Int = 0)

/** "controller is 3s ahead" / "controller is 1h2m5s behind", like the Windows app. */
fun driftText(controller: LocalDateTime, phone: LocalDateTime): String {
    val d = Duration.between(phone.truncatedTo(ChronoUnit.SECONDS), controller)
    val secs = Math.round(d.toMillis() / 1000.0)
    val abs = Math.abs(secs)
    val h = abs / 3600
    val m = abs % 3600 / 60
    val s = abs % 60
    val text = when {
        h > 0 -> "${h}h${m}m${s}s"
        m > 0 -> "${m}m${s}s"
        else -> "${s}s"
    }
    return "controller is $text ${if (secs >= 0) "ahead" else "behind"}"
}

private fun badIp(text: String) = "\"${text.trim()}\" is not a valid IPv4 address"

/** Controller clock, event listener, network, door names and app settings. */
class SettingsViewModel(
    val controller: StateFlow<SavedController?>,
    private val store: AppStore,
    val log: ActivityLog,
    private val controllerFor: (SavedController) -> ControllerApi,
    private val localAddress: () -> Inet4Address?,
    private val searchNet: suspend (InetAddress?) -> List<Device>,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val settleMs: Long = 2000,
    private val onDoorsChanged: suspend () -> Unit = {},
) : ViewModel() {
    val state: StateFlow<AppState> = store.state.stateIn(viewModelScope, SharingStarted.Eagerly, AppState())

    private val _time = MutableStateFlow<TimeInfo?>(null)
    val time: StateFlow<TimeInfo?> = _time.asStateFlow()

    private val _listener = MutableStateFlow(ListenerForm())
    val listener: StateFlow<ListenerForm> = _listener.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            controller.distinctUntilChangedBy { it?.serial }.collect {
                _time.value = null
                _listener.value = ListenerForm()
                _message.value = null
            }
        }
    }

    private fun current(): Long? = controller.value?.serial

    fun clearMessage() {
        _message.value = null
    }

    private fun showTime(t: LocalDateTime?) {
        if (t == null) {
            _message.value = "The controller did not report a valid time."
            return
        }
        val phone = now()
        _time.value = TimeInfo(formatTime(t), formatTime(phone), driftText(t, phone))
    }

    fun readTime() {
        val c = controller.value ?: return
        viewModelScope.runOn(
            c.serial, "Read controller time", log, ::current,
            block = { controllerFor(c).getTime() },
            onResult = ::showTime,
            onError = { _message.value = it },
        )
    }

    fun syncTime() {
        val c = controller.value ?: return
        viewModelScope.runOn(
            c.serial, "Sync controller clock to this device", log, ::current,
            block = { controllerFor(c).setTime(now().truncatedTo(ChronoUnit.SECONDS)) },
            onResult = ::showTime,
            onError = { _message.value = it },
        )
    }

    fun readListener() {
        val c = controller.value ?: return
        viewModelScope.runOn(
            c.serial, "Read event listener", log, ::current,
            block = { controllerFor(c).getListener() },
            onResult = { _listener.value = ListenerForm(it.ip.hostAddress ?: "", it.port, it.interval) },
            onError = { _message.value = it },
        )
    }

    fun updateListener(form: ListenerForm) {
        _listener.value = form
    }

    fun saveListener(ip: String, port: Int, interval: Int) {
        val c = controller.value ?: return
        val address = parseIpv4(ip)
        if (address == null) {
            _message.value = badIp(ip)
            return
        }
        val l = Listener(address, port.coerceIn(0, 65535), interval.coerceIn(0, 255))
        viewModelScope.runOn(
            c.serial, "Set event listener to ${address.hostAddress}:${l.port}", log, ::current,
            block = { controllerFor(c).setListener(l).orRefused() },
            onResult = { _message.value = "Event listener saved." },
            onError = { _message.value = it },
        )
    }

    /** Fills in this device's Wi-Fi address (and port 60001 if none is set). */
    fun useThisDevice() {
        val a = localAddress()?.hostAddress
        if (a == null) {
            _message.value = "Could not work out this device's address on the Wi-Fi network."
            return
        }
        _listener.value = _listener.value.let { it.copy(ip = a, port = if (it.port == 0) 60001 else it.port) }
        _message.value = "Filled in $a. Tap Save to send it to the controller."
    }

    /** The first problem with the typed network settings, or null if they are valid. */
    fun networkProblem(ip: String, mask: String, gateway: String): String? =
        listOf(ip, mask, gateway).firstOrNull { parseIpv4(it) == null }?.let(::badIp)

    /**
     * Sends new IP settings (the controller does not reply), waits for it to
     * restart, then searches for it and saves its new address.
     */
    fun changeNetwork(ip: String, mask: String, gateway: String) {
        val c = controller.value ?: return
        networkProblem(ip, mask, gateway)?.let {
            _message.value = it
            return
        }
        val newIp = parseIpv4(ip)!!
        viewModelScope.runOn(
            c.serial, "Change controller IP to ${newIp.hostAddress}", log, ::current,
            block = { controllerFor(c).setAddress(newIp, parseIpv4(mask)!!, parseIpv4(gateway)!!) },
            onResult = { confirmMove(c.serial, newIp) },
            onError = { _message.value = it },
        )
    }

    private fun confirmMove(serial: Long, newIp: Inet4Address) {
        log.info("Network settings sent. Searching again to confirm…")
        _message.value = "Network settings sent. Searching again to confirm…"
        viewModelScope.launch {
            delay(settleMs)
            val found = try {
                searchNet(null).firstOrNull { it.serial == serial } ?: searchNet(newIp).firstOrNull { it.serial == serial }
            } catch (e: IOException) {
                null
            }
            val at = found?.ip?.hostAddress
            if (at != null) {
                store.update { it.updateControllerIp(serial, at) }
                log.ok("Controller $serial found at $at")
                _message.value = "Controller found at $at."
            } else {
                log.error("Controller $serial not found after the change")
                _message.value = "The controller did not answer at its new address. Check the settings, then search again from Sites."
            }
        }
    }

    fun setKeepScreenOn(on: Boolean) = update { it.copy(keepScreenOn = on) }

    fun setShowRawPackets(on: Boolean) = update { it.copy(showRawPackets = on) }

    fun setDoorCount(serial: Long, n: Int) = update(doors = true) { it.setDoorCount(serial, n) }

    fun setDoorName(serial: Long, door: Int, name: String) = update(doors = true) { it.setDoorName(serial, door, name) }

    fun renameController(serial: Long, name: String) = update { it.renameController(serial, name) }

    private fun update(doors: Boolean = false, transform: (AppState) -> AppState) {
        viewModelScope.launch {
            store.update(transform)
            // Home-screen widgets show door names.
            if (doors) onDoorsChanged()
        }
    }
}
