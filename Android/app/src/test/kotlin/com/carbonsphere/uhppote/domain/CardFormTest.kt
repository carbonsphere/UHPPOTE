package com.carbonsphere.uhppote.domain

import com.carbonsphere.uhppote.protocol.Card
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CardFormTest {
    private val from = LocalDate.of(2024, 1, 1)
    private val to = LocalDate.of(2029, 12, 31)
    private fun form(number: String, from: LocalDate = this.from, to: LocalDate = this.to) = CardForm(number, from, to, listOf(1, 0, 5, 1))

    private fun problem(f: CardForm): CardProblem? = (f.validate().exceptionOrNull() as? CardFormException)?.problem

    @Test
    fun cardFormRejectsBadInput() {
        assertEquals(CardProblem.NUMBER_MISSING, problem(form("")))
        assertEquals(CardProblem.NUMBER_MISSING, problem(form("12a45")))
        assertEquals(CardProblem.NUMBER_MISSING, problem(form("-5")))
        assertEquals(CardProblem.NUMBER_INVALID, problem(form("0")))
        assertEquals(CardProblem.NUMBER_INVALID, problem(form("4294967040")))
        assertEquals(CardProblem.NUMBER_INVALID, problem(form("99999999999999999999999")))
        assertEquals(CardProblem.DATES_REVERSED, problem(form("10012345", from = to, to = from)))
    }

    @Test
    fun cardFormAcceptsValid() {
        assertEquals(Card(10012345, from, to, listOf(1, 0, 5, 1)), form(" 10012345 ").validate().getOrThrow())
        assertEquals(4294967039L, form("4294967039").validate().getOrThrow().number)
        assertEquals(from, form("1", from = from, to = from).validate().getOrThrow().to)
    }
}
