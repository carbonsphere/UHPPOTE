package com.carbonsphere.uhppote.ui.doors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbonsphere.uhppote.data.Profile
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.net.ActivityLog
import com.carbonsphere.uhppote.protocol.ControllerApi
import com.carbonsphere.uhppote.protocol.DoorControl
import com.carbonsphere.uhppote.protocol.INTERLOCK_MODES
import com.carbonsphere.uhppote.protocol.Status
import com.carbonsphere.uhppote.ui.ControllerRefused
import com.carbonsphere.uhppote.ui.orRefused
import com.carbonsphere.uhppote.ui.runOn
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class OpenResult { OPENED, REFUSED, NO_REPLY }

data class DoorTileState(
    val door: Int,
    val name: String,
    val open: Boolean?, // door sensor, null = unknown
    val unlocked: Boolean?, // lock relay, null = unknown
    val opening: Boolean,
    val lastResult: OpenResult?,
)

data class ControllerDoors(
    val controller: SavedController,
    val reachable: Boolean?, // null = not checked yet
    val doors: List<DoorTileState>,
)

private data class DoorKey(val serial: Long, val door: Int)

/** Every door of every controller in the active profile, with live status. */
class DoorsViewModel(
    private val profile: StateFlow<Profile?>,
    val log: ActivityLog,
    private val controllerFor: (SavedController) -> ControllerApi,
    private val pollMs: Long = 2000,
) : ViewModel() {
    private val statuses = MutableStateFlow<Map<Long, Status?>>(emptyMap())
    private val reachable = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    private val opening = MutableStateFlow<Set<DoorKey>>(emptySet())
    private val results = MutableStateFlow<Map<DoorKey, OpenResult>>(emptyMap())

    val tiles: StateFlow<List<ControllerDoors>> =
        combine(profile, statuses, reachable, opening, results) { p, st, re, op, rs ->
            p?.controllers.orEmpty().map { c ->
                val s = st[c.serial]
                ControllerDoors(
                    controller = c,
                    reachable = re[c.serial],
                    doors = (1..c.doorCount).map { d ->
                        val k = DoorKey(c.serial, d)
                        DoorTileState(
                            door = d,
                            name = c.doorName(d, "Door $d"),
                            open = s?.doorOpen?.getOrNull(d - 1),
                            unlocked = s?.let { it.relays and (1 shl (d - 1)) != 0 },
                            opening = k in op,
                            lastResult = rs[k],
                        )
                    },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _doorControl = MutableStateFlow<DoorControl?>(null)
    val doorControl: StateFlow<DoorControl?> = _doorControl.asStateFlow()

    private var poller: Job? = null

    private fun find(serial: Long): SavedController? = profile.value?.controllers?.firstOrNull { it.serial == serial }

    private fun current(serial: Long): () -> Long? = { find(serial)?.serial }

    fun open(serial: Long, door: Int) {
        val c = find(serial) ?: return
        val key = DoorKey(serial, door)
        opening.update { it + key }
        val name = c.doorName(door, "Door $door")
        viewModelScope.runOn(
            serial, "Open $name", log, current(serial),
            block = { controllerFor(c).openDoor(door).orRefused() },
            onResult = { finish(key, OpenResult.OPENED) },
            onError = { reason -> finish(key, if (reason == ControllerRefused.MESSAGE) OpenResult.REFUSED else OpenResult.NO_REPLY) },
        ).invokeOnCompletion { opening.update { it - key } }
    }

    private fun finish(key: DoorKey, r: OpenResult) {
        opening.update { it - key }
        results.update { it + (key to r) }
        viewModelScope.launch {
            delay(3000)
            results.update { if (it[key] == r) it - key else it }
        }
    }

    /** Reads the status of every controller in the profile once. */
    suspend fun refresh() = coroutineScope {
        profile.value?.controllers.orEmpty().map { c ->
            async {
                val s = try {
                    controllerFor(c).status()
                } catch (e: IOException) {
                    null
                }
                c.serial to s
            }
        }.awaitAll().forEach { (serial, s) ->
            reachable.update { it + (serial to (s != null)) }
            if (s != null) statuses.update { it + (serial to s) } else statuses.update { it - serial }
        }
    }

    /** Polls status every pollMs until [stopPolling]; call while the screen is visible. */
    fun startPolling() {
        if (poller?.isActive == true) return
        poller = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(pollMs)
            }
        }
    }

    fun stopPolling() {
        poller?.cancel()
        poller = null
    }

    fun clearMessage() {
        _message.value = null
    }

    fun readDoorControl(serial: Long, door: Int) {
        val c = find(serial) ?: return
        _doorControl.value = null
        viewModelScope.runOn(
            serial, "Read ${c.doorName(door, "Door $door")} settings", log, current(serial),
            block = { controllerFor(c).getDoorControl(door) },
            onResult = { _doorControl.value = it },
            onError = { _message.value = it },
        )
    }

    fun saveDoorControl(serial: Long, dc: DoorControl) {
        val c = find(serial) ?: return
        viewModelScope.runOn(
            serial, "Save ${c.doorName(dc.door, "Door ${dc.door}")} settings (${dc.modeText()}, ${dc.delay} s)", log, current(serial),
            block = { controllerFor(c).setDoorControl(dc) },
            onResult = { _doorControl.value = it; _message.value = "Saved" },
            onError = { _message.value = it },
        )
    }

    fun setInterlock(serial: Long, mode: Int) {
        val c = find(serial) ?: return
        val text = INTERLOCK_MODES.firstOrNull { it.first == mode }?.second ?: "mode $mode"
        viewModelScope.runOn(
            serial, "Set interlock: $text", log, current(serial),
            block = { controllerFor(c).setInterlock(mode).orRefused() },
            onResult = { _message.value = "Interlock set" },
            onError = { _message.value = it },
        )
    }

    fun setKeypads(serial: Long, enabled: List<Boolean>) {
        val c = find(serial) ?: return
        viewModelScope.runOn(
            serial, "Set reader keypads", log, current(serial),
            block = { controllerFor(c).setKeypads(enabled).orRefused() },
            onResult = { _message.value = "Keypads set" },
            onError = { _message.value = it },
        )
    }
}
