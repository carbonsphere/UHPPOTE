package com.carbonsphere.uhppote.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carbonsphere.uhppote.data.CardCache
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.domain.CardForm
import com.carbonsphere.uhppote.domain.CardFormException
import com.carbonsphere.uhppote.domain.CardLoad
import com.carbonsphere.uhppote.domain.CardProblem
import com.carbonsphere.uhppote.domain.loadCards
import com.carbonsphere.uhppote.net.ActivityLog
import com.carbonsphere.uhppote.protocol.Card
import com.carbonsphere.uhppote.protocol.ControllerApi
import com.carbonsphere.uhppote.ui.orRefused
import com.carbonsphere.uhppote.ui.runOn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How far a card list load has got. */
data class LoadProgress(val index: Long, val found: Int, val count: Long)

/** The selected controller's cards: the full list, search, add/edit and delete. */
class CardsViewModel(
    val controller: StateFlow<SavedController?>,
    private val log: ActivityLog,
    private val controllerFor: (SavedController) -> ControllerApi,
    private val cache: CardCache,
) : ViewModel() {
    private val _count = MutableStateFlow<Long?>(null)
    val count: StateFlow<Long?> = _count.asStateFlow()

    private val all = MutableStateFlow<List<Card>>(emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** The cards matching the search, in controller order. */
    val cards: StateFlow<List<Card>> = combine(all, _query) { list, q ->
        val prefix = q.trim()
        if (prefix.isEmpty()) list else list.filter { it.number.toString().startsWith(prefix) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _loadProgress = MutableStateFlow<LoadProgress?>(null)
    val loadProgress: StateFlow<LoadProgress?> = _loadProgress.asStateFlow()

    /** False when the list shown is only part of the controller's cards. */
    private val _complete = MutableStateFlow(true)
    val complete: StateFlow<Boolean> = _complete.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            controller.distinctUntilChangedBy { it?.serial }.collect { c ->
                cancelLoad()
                all.value = emptyList()
                _count.value = null
                _complete.value = true
                _message.value = null
                if (c != null) open(c)
            }
        }
    }

    private fun current(): Long? = controller.value?.serial

    /** Shows the cached list at once, then reloads it if the card count changed. */
    private fun open(c: SavedController) {
        loadJob = viewModelScope.launch {
            val cached = cache.load(c.serial)
            if (current() != c.serial) return@launch
            if (cached != null) all.value = cached
            startLoad(c, cached)
        }
    }

    fun reload() {
        val c = controller.value ?: return
        cancelLoad()
        startLoad(c, null)
    }

    private fun startLoad(c: SavedController, cached: List<Card>?) {
        _loadProgress.value = LoadProgress(0, 0, 0)
        val job = viewModelScope.runOn(
            c.serial, "Load card list", log, ::current,
            block = {
                val api = controllerFor(c)
                val n = api.getCardCount()
                if (current() == c.serial) _count.value = n
                if (cached != null && cached.size.toLong() == n) return@runOn CardLoad.Done(cached, complete = true)
                var done: CardLoad.Done? = null
                loadCards(n, api::getCardByIndex).collect { step ->
                    if (current() != c.serial) return@collect
                    when (step) {
                        is CardLoad.Progress -> {
                            _loadProgress.value = LoadProgress(step.index, step.cards.size, n)
                            all.value = step.cards
                        }
                        is CardLoad.Done -> done = step
                    }
                }
                done ?: CardLoad.Done(all.value, complete = false)
            },
            onResult = { d ->
                all.value = d.cards
                _complete.value = d.complete
                if (d.complete) saveCache(c.serial) else _message.value = "Stopped after 100000 slots; the list may be incomplete."
            },
            onError = { _message.value = it; _complete.value = false },
        )
        loadJob = job
        job.invokeOnCompletion { if (loadJob === job) _loadProgress.value = null }
    }

    fun cancelLoad() {
        val job = loadJob ?: return
        loadJob = null
        job.cancel()
        _loadProgress.value = null
        _complete.value = all.value.size.toLong() == _count.value
    }

    private fun saveCache(serial: Long) {
        if (!_complete.value) return
        val list = all.value
        viewModelScope.launch { cache.save(serial, list) }
    }

    fun filter(query: String) {
        _query.value = query
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Reads one card from the controller; onFound gets it, else a message is shown. */
    fun lookup(number: String, onFound: (Card) -> Unit) {
        val c = controller.value ?: return
        val n = number.trim().toLongOrNull() ?: return
        viewModelScope.runOn(
            c.serial, "Look up card $n", log, ::current,
            block = { controllerFor(c).getCard(n) },
            onResult = { card -> if (card == null) _message.value = "Card $n is not stored on the controller." else onFound(card) },
            onError = { _message.value = it },
        )
    }

    /** Sends the card, or returns why the form is not valid (nothing is sent then). */
    fun save(form: CardForm, onDone: () -> Unit = {}): CardProblem? {
        val card = form.validate().getOrElse { return (it as CardFormException).problem }
        val c = controller.value ?: return null
        viewModelScope.runOn(
            c.serial, "Add / update card ${card.number}", log, ::current,
            block = {
                val api = controllerFor(c)
                api.putCard(card).orRefused()
                api.getCardCount()
            },
            onResult = { n ->
                val list = all.value
                val i = list.indexOfFirst { it.number == card.number }
                all.value = if (i >= 0) list.toMutableList().also { it[i] = card } else list + card
                _count.value = n
                saveCache(c.serial)
                onDone()
            },
            onError = { _message.value = it },
        )
        return null
    }

    fun delete(number: Long, onDone: () -> Unit = {}) {
        val c = controller.value ?: return
        viewModelScope.runOn(
            c.serial, "Delete card $number", log, ::current,
            block = {
                val api = controllerFor(c)
                api.deleteCard(number).orRefused()
                api.getCardCount()
            },
            onResult = { n ->
                all.value = all.value.filterNot { it.number == number }
                _count.value = n
                saveCache(c.serial)
                onDone()
            },
            onError = { _message.value = it },
        )
    }

    fun deleteAll() {
        val c = controller.value ?: return
        cancelLoad()
        viewModelScope.runOn(
            c.serial, "Delete all cards", log, ::current,
            block = {
                val api = controllerFor(c)
                api.deleteAllCards().orRefused()
                api.getCardCount()
            },
            onResult = { n ->
                all.value = emptyList()
                _count.value = n
                _complete.value = n == 0L
                saveCache(c.serial)
            },
            onError = { _message.value = it },
        )
    }
}
