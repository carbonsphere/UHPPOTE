package com.carbonsphere.uhppote.ui

import com.carbonsphere.uhppote.data.AppState
import com.carbonsphere.uhppote.data.AppStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** An in-memory AppStore. */
class FakeStore(initial: AppState = AppState()) : AppStore {
    override val state = MutableStateFlow(initial)
    override suspend fun update(transform: (AppState) -> AppState) {
        state.value = transform(state.value)
    }
}

/** Runs viewModelScope on a test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    val dispatcher = UnconfinedTestDispatcher()
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
