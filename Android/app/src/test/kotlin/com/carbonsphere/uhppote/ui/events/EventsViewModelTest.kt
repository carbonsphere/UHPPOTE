package com.carbonsphere.uhppote.ui.events

import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.data.device
import com.carbonsphere.uhppote.data.toSaved
import com.carbonsphere.uhppote.net.ActivityLog
import com.carbonsphere.uhppote.protocol.Event
import com.carbonsphere.uhppote.protocol.Status
import com.carbonsphere.uhppote.ui.EMPTY_EVENT
import com.carbonsphere.uhppote.ui.FakeApi
import com.carbonsphere.uhppote.ui.MainDispatcherRule
import com.carbonsphere.uhppote.ui.status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class EventsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val saved: SavedController = device(423206957).toSaved()

    private inner class Api : FakeApi(423206957) {
        var readIndex = 7L
        override suspend fun status(): Status = status(last = EMPTY_EVENT.copy(index = 10, type = 1))
        override suspend fun getEventIndex() = readIndex
        override suspend fun setEventIndex(index: Long) = true.also { readIndex = index }
        override suspend fun getEvent(index: Long): Event = EMPTY_EVENT.copy(index = index, type = 1)
    }

    @Test
    fun fetchUnreadNewestFirst() = runTest(main.dispatcher) {
        val api = Api()
        val m = EventsViewModel(MutableStateFlow(saved), ActivityLog(), controllerFor = { api })
        m.fetchUnread()
        assertEquals(listOf(10L, 9L, 8L), m.events.value.map { it.index })
        assertEquals(10L, m.newest.value)
        assertEquals(7L, m.readIndex.value)
        assertEquals(null, m.progress.value)
    }

    @Test
    fun markAllReadSetsIndexToNewest() = runTest(main.dispatcher) {
        val api = Api()
        val m = EventsViewModel(MutableStateFlow(saved), ActivityLog(), controllerFor = { api })
        m.markAllRead()
        assertEquals(10L, api.readIndex)
        assertEquals(10L, m.readIndex.value)
    }
}
