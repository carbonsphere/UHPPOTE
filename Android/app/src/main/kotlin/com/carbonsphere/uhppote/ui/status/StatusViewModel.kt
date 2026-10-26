package com.carbonsphere.uhppote.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.net.ActivityLog
import com.carbonsphere.uhppote.protocol.ControllerApi
import com.carbonsphere.uhppote.protocol.Status
import com.carbonsphere.uhppote.ui.runOn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The selected controller's status, refreshed on demand or every 2 s. */
class StatusViewModel(
    val controller: StateFlow<SavedController?>,
    private val log: ActivityLog,
    private val controllerFor: (SavedController) -> ControllerApi,
    private val pollMs: Long = 2000,
) : ViewModel() {
    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _autoRefresh = MutableStateFlow(false)
    val autoRefresh: StateFlow<Boolean> = _autoRefresh.asStateFlow()

    private var inFlight: Job? = null
    private var poller: Job? = null

    init {
        // A different controller's status must not stay on screen.
        viewModelScope.launch {
            controller.distinctUntilChangedBy { it?.serial }.collect {
                _status.value = null
                _error.value = null
            }
        }
    }

    /** Reads the status once; skipped while a read is still running. */
    fun refresh() {
        val c = controller.value ?: return
        if (inFlight?.isActive == true) return
        inFlight = viewModelScope.runOn(
            c.serial, "Status of ${c.displayName()}", log, { controller.value?.serial },
            block = { controllerFor(c).status() },
            onResult = { _status.value = it; _error.value = null },
            onError = { _error.value = it },
        )
    }

    fun setAutoRefresh(on: Boolean) {
        _autoRefresh.value = on
    }

    /** Polls while auto-refresh is on; call while the screen is visible. */
    fun startPolling() {
        if (poller?.isActive == true) return
        poller = viewModelScope.launch {
            while (isActive) {
                if (_autoRefresh.value) refresh()
                delay(pollMs)
            }
        }
    }

    fun stopPolling() {
        poller?.cancel()
        poller = null
    }
}
