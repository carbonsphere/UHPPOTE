package com.carbonsphere.uhppote.ui.cards

import com.carbonsphere.uhppote.data.CardCache
import com.carbonsphere.uhppote.data.device
import com.carbonsphere.uhppote.data.toSaved
import com.carbonsphere.uhppote.domain.CardForm
import com.carbonsphere.uhppote.domain.CardProblem
import com.carbonsphere.uhppote.net.ActivityLog
import com.carbonsphere.uhppote.protocol.Card
import com.carbonsphere.uhppote.protocol.CardSlot
import com.carbonsphere.uhppote.ui.FakeApi
import com.carbonsphere.uhppote.ui.MainDispatcherRule
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class CardsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val d1 = LocalDate.of(2024, 1, 1)
    private val d2 = LocalDate.of(2029, 12, 31)
    private fun card(n: Long) = Card(n, d1, d2, listOf(1, 1, 0, 0))

    /** A controller holding a mutable card table. */
    private class Api(val table: MutableList<Card>) : FakeApi(423206957) {
        override suspend fun getCardCount() = table.size.toLong()
        override suspend fun getCardByIndex(index: Long): CardSlot = table.getOrNull((index - 1).toInt())?.let { CardSlot.Found(it) } ?: CardSlot.End
        override suspend fun putCard(card: Card): Boolean {
            table.removeAll { it.number == card.number }
            table += card
            return true
        }
        override suspend fun deleteCard(number: Long) = table.removeAll { it.number == number }
    }

    @Test
    fun loadsThenSaveAndDeleteUpdateListCountAndCache() = runTest(main.dispatcher) {
        val dir = Files.createTempDirectory("cards").toFile()
        val cache = CardCache(dir, main.dispatcher)
        val api = Api(mutableListOf(card(1), card(2)))
        val m = CardsViewModel(MutableStateFlow(device(423206957).toSaved()), ActivityLog(), { api }, cache)
        assertEquals(listOf(1L, 2L), m.cards.value.map { it.number })
        assertEquals(2L, m.count.value)

        assertEquals(null, m.save(CardForm("3", d1, d2, listOf(1, 0, 0, 0))))
        assertEquals(listOf(1L, 2L, 3L), m.cards.value.map { it.number })
        assertEquals(3L, m.count.value)

        m.delete(1)
        assertEquals(listOf(2L, 3L), m.cards.value.map { it.number })
        assertEquals(2L, m.count.value)
        assertEquals(listOf(2L, 3L), cache.load(423206957)?.map { it.number })

        m.filter("3")
        assertEquals(listOf(3L), m.cards.value.map { it.number })
        dir.deleteRecursively()
    }

    @Test
    fun badFormIsNotSent() = runTest(main.dispatcher) {
        val api = Api(mutableListOf())
        val m = CardsViewModel(MutableStateFlow(device(423206957).toSaved()), ActivityLog(), { api }, CardCache(Files.createTempDirectory("c").toFile(), main.dispatcher))
        assertEquals(CardProblem.NUMBER_INVALID, m.save(CardForm("0", d1, d2, listOf(1, 0, 0, 0))))
        assertEquals(emptyList(), api.table)
    }
}
