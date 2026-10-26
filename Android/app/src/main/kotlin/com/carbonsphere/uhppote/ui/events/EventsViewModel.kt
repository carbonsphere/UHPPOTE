package com.carbonsphere.uhppote.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.domain.eventIndexes
import com.carbonsphere.uhppote.net.ActivityLog
import com.carbonsphere.uhppote.protocol.ControllerApi
import com.carbonsphere.uhppote.protocol.Event
import com.carbonsphere.uhppote.ui.orRefused
import com.carbonsphere.uhppote.ui.runOn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/** The selected controller's event log. */
class EventsViewModel(
    val controller: StateFlow<SavedController?>,
    private val log: ActivityLog,
    private val controllerFor: (SavedController) -> ControllerApi,
) : ViewModel() {
    private val _newest = MutableStateFlow<Long?>(null)
    val newest: StateFlow<Long?> = _newest.asStateFlow()

    private val _readIndex = MutableStateFlow<Long?>(null)
    val readIndex: StateFlow<Long?> = _readIndex.asStateFlow()

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    /** Events fetched so far while a fetch runs, else null. */
    private val _progress = MutableStateFlow<Int?>(null)
    val progress: StateFlow<Int?> = _progress.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var fetchJob: Job? = null

    init {
        viewModelScope.launch {
            controller.distinctUntilChangedBy { it?.serial }.collect {
                cancelFetch()
                _newest.value = null
                _readIndex.value = null
                _events.value = emptyList()
                _message.value = null
            }
        }
    }

    private fun current(): Long? = controller.value?.serial

    /** Reads the newest and "marked read" event indexes. */
    fun readIndexes() {
        val c = controller.value ?: return
        viewModelScope.runOn(
            c.serial, "Read event indexes", log, ::current,
            block = {
                val api = controllerFor(c)
                api.status().lastEvent.index to api.getEventIndex()
            },
            onResult = { (newest, read) -> _newest.value = newest; _readIndex.value = read },
            onError = { _message.value = it },
        )
    }

    /** Fetches the last [want] events. */
    fun fetch(want: Long) = fetch("Fetch last $want events", want, unreadOnly = false)

    /** Fetches every event after the "marked read" index. */
    fun fetchUnread() = fetch("Fetch unread events", 0, unreadOnly = true)

    private fun fetch(desc: String, want: Long, unreadOnly: Boolean) {
        val c = controller.value ?: return
        if (fetchJob?.isActive == true) {
            _message.value = "Already fetching events…"
            return
        }
        _message.value = null
        _progress.value = 0
        val job = viewModelScope.runOn(
            c.serial, desc, log, ::current,
            block = {
                val api = controllerFor(c)
                val newest = api.status().lastEvent.index
                val read = if (unreadOnly) api.getEventIndex() else null
                val out = mutableListOf<Event>()
                for (i in eventIndexes(newest, want, read)) {
                    out += api.getEvent(i)
                    if (current() == c.serial) _progress.value = out.size
                }
                Triple(newest, read, out)
            },
            onResult = { (newest, read, list) ->
                _newest.value = newest
                if (read != null) _readIndex.value = read
                _events.value = list
                if (list.isEmpty()) _message.value = "No events to show."
            },
            onError = { _message.value = it },
        )
        fetchJob = job
        job.invokeOnCompletion { if (fetchJob === job) _progress.value = null }
    }

    fun cancelFetch() {
        fetchJob?.cancel()
        fetchJob = null
        _progress.value = null
    }

    /** Marks every event up to the newest as read. */
    fun markAllRead() {
        val c = controller.value ?: return
        viewModelScope.runOn(
            c.serial, "Mark all events as read", log, ::current,
            block = {
                val api = controllerFor(c)
                val newest = api.status().lastEvent.index
                api.setEventIndex(newest).orRefused()
                newest
            },
            onResult = { readIndexes() },
            onError = { _message.value = it },
        )
    }
}
