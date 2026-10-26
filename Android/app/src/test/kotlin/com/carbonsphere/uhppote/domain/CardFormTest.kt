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
    fun cardFormRejectsBadProfile() {
        fun doors(vararg d: Int) = CardForm("10012345", from, to, d.toList())
        assertEquals(CardProblem.PROFILE_INVALID, (doors(1, 300, 0, 0).validate().exceptionOrNull() as CardFormException).problem)
        assertEquals(CardProblem.PROFILE_INVALID, (doors(1, -1, 0, 0).validate().exceptionOrNull() as CardFormException).problem)
        assertEquals(CardProblem.PROFILE_INVALID, (doors(255, 0, 0, 0).validate().exceptionOrNull() as CardFormException).problem)
        assertEquals(listOf(0, 1, 2, 254), doors(0, 1, 2, 254).validate().getOrThrow().doors)
    }

    @Test
    fun cardFormRejectsUnreadableDate() {
        // A card whose dates the controller reported as invalid comes back as LocalDate.MIN.
        assertEquals(CardProblem.DATES_INVALID, problem(form("10012345", from = LocalDate.MIN)))
        assertEquals(CardProblem.DATES_INVALID, problem(form("10012345", to = LocalDate.of(10000, 1, 1))))
    }

    @Test
    fun cardFormAcceptsValid() {
        assertEquals(Card(10012345, from, to, listOf(1, 0, 5, 1)), form(" 10012345 ").validate().getOrThrow())
        assertEquals(4294967039L, form("4294967039").validate().getOrThrow().number)
        assertEquals(from, form("1", from = from, to = from).validate().getOrThrow().to)
    }
}
