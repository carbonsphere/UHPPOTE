package com.carbonsphere.uhppote.domain

import com.carbonsphere.uhppote.protocol.Card
import com.carbonsphere.uhppote.protocol.CardSlot
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CardListLoaderTest {
    private fun card(n: Long) = Card(n, LocalDate.of(2024, 1, 1), LocalDate.of(2029, 12, 31), listOf(1, 0, 0, 0))

    /** Slots 1.. as given; past the list the controller reports End. */
    private class Slots(vararg val slots: CardSlot) {
        val asked = mutableListOf<Long>()
        suspend fun fetch(i: Long): CardSlot {
            asked += i
            return slots.getOrElse((i - 1).toInt()) { CardSlot.End }
        }
    }

    @Test
    fun loaderSkipsDeletedSlots() = runTest {
        val s = Slots(CardSlot.Found(card(1)), CardSlot.Deleted, CardSlot.Found(card(3)), CardSlot.End)
        val done = loadCards(3, s::fetch).last()
        assertEquals(CardLoad.Done(listOf(card(1), card(3)), complete = true), done)
    }

    @Test
    fun loaderStopsAtEnd() = runTest {
        val s = Slots(CardSlot.Found(card(1)), CardSlot.End, CardSlot.Found(card(3)))
        val done = loadCards(10, s::fetch).last()
        assertEquals(CardLoad.Done(listOf(card(1)), complete = true), done)
        assertEquals(listOf(1L, 2L), s.asked)
    }

    @Test
    fun loaderStopsWhenCountReached() = runTest {
        val s = Slots(CardSlot.Found(card(1)), CardSlot.Deleted, CardSlot.Found(card(3)), CardSlot.Found(card(4)))
        val all = loadCards(2, s::fetch).toList()
        assertEquals(CardLoad.Done(listOf(card(1), card(3)), complete = true), all.last())
        assertEquals(listOf(1L, 2L, 3L), s.asked)
        assertEquals(CardLoad.Progress(1, listOf(card(1))), all.first())
    }

    @Test
    fun loaderHonoursCancel() = runTest {
        val s = Slots(CardSlot.Found(card(1)), CardSlot.Found(card(2)), CardSlot.Found(card(3)))
        val first = loadCards(3, s::fetch).first()
        assertIs<CardLoad.Progress>(first)
        assertEquals(listOf(1L), s.asked)
    }

    @Test
    fun loaderMaxIndexIncomplete() = runTest {
        val s = Slots(*Array(10) { CardSlot.Deleted })
        val done = loadCards(5, s::fetch, maxIndex = 4).last()
        assertEquals(CardLoad.Done(emptyList(), complete = false), done)
        assertEquals(listOf(1L, 2L, 3L, 4L), s.asked)
    }

    @Test
    fun loaderZeroCountIsEmptyAndComplete() = runTest {
        val s = Slots(CardSlot.Found(card(1)))
        assertEquals(listOf<CardLoad>(CardLoad.Done(emptyList(), complete = true)), loadCards(0, s::fetch).toList())
        assertEquals(emptyList(), s.asked)
    }
}
