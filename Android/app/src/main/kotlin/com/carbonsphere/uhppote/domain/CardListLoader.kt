package com.carbonsphere.uhppote.domain

import com.carbonsphere.uhppote.protocol.Card
import com.carbonsphere.uhppote.protocol.CardSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface CardLoad {
    /** Slot [index] was read; [cards] holds every card found so far. */
    data class Progress(val index: Long, val cards: List<Card>) : CardLoad

    /** Loading finished; complete is false only when maxIndex was reached first. */
    data class Done(val cards: List<Card>, val complete: Boolean) : CardLoad
}

/**
 * Reads the controller's card table slot by slot from index 1, skipping
 * deleted slots, until the end of the table, until [count] cards were found,
 * or until [maxIndex]. Stop collecting to cancel.
 */
fun loadCards(count: Long, fetch: suspend (Long) -> CardSlot, maxIndex: Long = 100_000): Flow<CardLoad> = flow {
    val cards = mutableListOf<Card>()
    var index = 1L
    while (cards.size < count) {
        if (index > maxIndex) {
            emit(CardLoad.Done(cards.toList(), complete = false))
            return@flow
        }
        when (val slot = fetch(index)) {
            is CardSlot.Found -> cards += slot.card
            CardSlot.Deleted -> {}
            CardSlot.End -> break
        }
        emit(CardLoad.Progress(index, cards.toList()))
        index++
    }
    emit(CardLoad.Done(cards.toList(), complete = true))
}
