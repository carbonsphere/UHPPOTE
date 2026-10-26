package com.carbonsphere.uhppote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbonsphere.uhppote.data.AppState
import com.carbonsphere.uhppote.data.AppStore
import com.carbonsphere.uhppote.data.Profile
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.data.activeProfile
import com.carbonsphere.uhppote.data.addProfile
import com.carbonsphere.uhppote.data.addToProfile
import com.carbonsphere.uhppote.data.setActive
import com.carbonsphere.uhppote.data.updateControllerIp
import com.carbonsphere.uhppote.domain.Resolution
import com.carbonsphere.uhppote.net.ActivityLog
import com.carbonsphere.uhppote.net.parseIpv4
import com.carbonsphere.uhppote.protocol.Device
import java.io.IOException
import java.net.InetAddress
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the main window shows. */
sealed interface MainScreen {
    data object Loading : MainScreen
    data object Profiles : MainScreen
    data class Connected(val profile: Profile) : MainScreen
}

sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Found(val devices: List<Device>) : SearchState
    data class Failed(val message: String) : SearchState
}

/** Profiles, the active profile and the selected controller. */
class MainViewModel(
    private val store: AppStore,
    val log: ActivityLog,
    private val resolve: suspend () -> Resolution,
    private val searchNet: suspend (InetAddress?) -> List<Device>,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    val state: StateFlow<AppState> = store.state.stateIn(viewModelScope, SharingStarted.Eagerly, AppState())

    val activeProfile: StateFlow<Profile?> = state.map { it.activeProfile() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // null while the launch check is running, true to show the profile list.
    private val showProfiles = MutableStateFlow<Boolean?>(null)

    val screen: StateFlow<MainScreen> = combine(showProfiles, state) { show, s ->
        val active = s.activeProfile()
        when {
            show == null -> MainScreen.Loading
            show || active == null -> MainScreen.Profiles
            else -> MainScreen.Connected(active)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainScreen.Loading)

    private val selectedSerial = MutableStateFlow<Long?>(null)

    /** The controller the Status, Cards, Events and Settings sections work on. */
    val selectedController: StateFlow<SavedController?> = combine(selectedSerial, state) { serial, s ->
        val cs = s.activeProfile()?.controllers.orEmpty()
        cs.firstOrNull { it.serial == serial } ?: cs.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    init {
        resolveOnLaunch()
    }

    fun resolveOnLaunch() {
        showProfiles.value = null
        viewModelScope.launch {
            when (val r = resolve()) {
                is Resolution.Active -> {
                    activate(r)
                    showProfiles.value = false
                }
                Resolution.Choose -> showProfiles.value = true
            }
        }
    }

    private suspend fun activate(r: Resolution.Active) {
        store.update { s ->
            r.ipUpdates.entries.fold(s) { acc, (serial, ip) -> acc.updateControllerIp(serial, ip) }
                .setActive(r.profileId)
        }
        r.ipUpdates.forEach { (serial, ip) -> log.info("Controller $serial moved to $ip") }
    }

    private var backgroundAt: Long? = null

    /** The app went to the background at nowMs (a monotonic clock). */
    fun onBackground(nowMs: Long) {
        backgroundAt = nowMs
    }

    /**
     * The app is back. After more than awayMs the manager may be at another
     * site, so the site is chosen again; if nothing answers, the current one stays.
     */
    fun onForeground(nowMs: Long, awayMs: Long = 120_000) {
        val at = backgroundAt ?: return
        backgroundAt = null
        val before = (screen.value as? MainScreen.Connected)?.profile?.id ?: return
        if (nowMs - at < awayMs) return
        viewModelScope.launch {
            val r = resolve() as? Resolution.Active ?: return@launch
            activate(r)
            if (r.profileId != before) {
                selectedSerial.value = null
                log.info("Switched to site ${state.value.activeProfile()?.name ?: r.profileId}")
            }
        }
    }

    fun selectController(serial: Long) {
        selectedSerial.value = serial
    }

    fun switchProfile(id: String) {
        viewModelScope.launch {
            store.update { it.setActive(id) }
            selectedSerial.value = null
            showProfiles.value = false
        }
    }

    fun showProfiles() {
        showProfiles.value = true
    }

    /** Back from the profile list to the active profile, if there is one. */
    fun closeProfiles() {
        if (state.value.activeProfile() != null) showProfiles.value = false
    }

    /** Searches this network, or one controller IP if target is not blank. */
    fun search(target: String?) {
        val text = target?.trim().orEmpty()
        val address = if (text.isEmpty()) null else parseIpv4(text)
        if (text.isNotEmpty() && address == null) {
            _searchState.value = SearchState.Failed("\"$text\" is not a valid IPv4 address")
            return
        }
        _searchState.value = SearchState.Searching
        log.info("Searching ${address?.hostAddress ?: "this network"}…")
        viewModelScope.launch {
            _searchState.value = try {
                val found = searchNet(address)
                found.forEach { log.info("Found controller ${it.serial} at ${it.ip.hostAddress} (firmware ${it.firmware})") }
                SearchState.Found(found)
            } catch (e: IOException) {
                log.error("Search failed: ${e.message}")
                SearchState.Failed(e.message ?: "Search failed")
            }
        }
    }

    fun clearSearch() {
        _searchState.value = SearchState.Idle
    }

    /** Saves found controllers into an existing profile, or a new one when profileId is null. */
    fun saveFound(devices: List<Device>, profileId: String?, newProfileName: String?) {
        if (devices.isEmpty()) return
        viewModelScope.launch {
            val id = profileId ?: newId()
            store.update { s ->
                val added = if (profileId != null) {
                    s.addToProfile(profileId, devices)
                } else {
                    s.addProfile(newProfileName?.trim().takeUnless { it.isNullOrEmpty() } ?: "Site", devices, id)
                }
                added.setActive(id)
            }
            _searchState.value = SearchState.Idle
            selectedSerial.value = null
            showProfiles.value = false
        }
    }

    fun updateState(transform: (AppState) -> AppState) {
        viewModelScope.launch { store.update(transform) }
    }
}
