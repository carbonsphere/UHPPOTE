package com.carbonsphere.uhppote.domain

import com.carbonsphere.uhppote.protocol.Card
import java.time.LocalDate

/** Why a card form cannot be sent; the UI shows the matching strings.xml message. */
enum class CardProblem { NUMBER_MISSING, NUMBER_INVALID, DATES_REVERSED }

class CardFormException(val problem: CardProblem) : IllegalArgumentException(problem.name)

/** The add/edit card form as typed. doors: 0 = no access, 1 = allowed, 2-254 = time profile. */
data class CardForm(val number: String, val from: LocalDate, val to: LocalDate, val doors: List<Int>) {
    fun validate(): Result<Card> {
        val text = number.trim()
        if (text.isEmpty() || !text.all { it in '0'..'9' }) return fail(CardProblem.NUMBER_MISSING)
        val n = text.toLongOrNull()
        if (n == null || n == 0L || n >= 0xFFFFFF00L) return fail(CardProblem.NUMBER_INVALID)
        if (to.isBefore(from)) return fail(CardProblem.DATES_REVERSED)
        return Result.success(Card(n, from, to, doors))
    }

    private fun fail(p: CardProblem): Result<Card> = Result.failure(CardFormException(p))
}
