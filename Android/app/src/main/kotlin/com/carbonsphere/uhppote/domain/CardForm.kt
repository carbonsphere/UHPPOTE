package com.carbonsphere.uhppote.domain

import com.carbonsphere.uhppote.protocol.Card
import java.time.LocalDate

/** Why a card form cannot be sent; the UI shows the matching strings.xml message. */
enum class CardProblem { NUMBER_MISSING, NUMBER_INVALID, DATES_INVALID, DATES_REVERSED, PROFILE_INVALID }

class CardFormException(val problem: CardProblem) : IllegalArgumentException(problem.name)

/**
 * The add/edit card form as typed. doors: 0 = no access, 1 = allowed,
 * 2-254 = time profile; anything else (e.g. -1 for an unreadable profile
 * field) is rejected.
 */
data class CardForm(val number: String, val from: LocalDate, val to: LocalDate, val doors: List<Int>) {
    fun validate(): Result<Card> {
        val text = number.trim()
        if (text.isEmpty() || !text.all { it in '0'..'9' }) return fail(CardProblem.NUMBER_MISSING)
        val n = text.toLongOrNull()
        if (n == null || n == 0L || n >= 0xFFFFFF00L) return fail(CardProblem.NUMBER_INVALID)
        // The controller stores dates as 4-digit BCD years; unreadable dates arrive as LocalDate.MIN.
        if (from.year !in 1..9999 || to.year !in 1..9999) return fail(CardProblem.DATES_INVALID)
        if (to.isBefore(from)) return fail(CardProblem.DATES_REVERSED)
        if (doors.any { it !in 0..254 }) return fail(CardProblem.PROFILE_INVALID)
        return Result.success(Card(n, from, to, doors))
    }

    private fun fail(p: CardProblem): Result<Card> = Result.failure(CardFormException(p))
}
