package com.carbonsphere.uhppote.data

import com.carbonsphere.uhppote.protocol.Card
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardCacheTest {
    @Test
    fun cacheRoundTrip() = runTest {
        val dir = Files.createTempDirectory("cards").toFile()
        val cache = CardCache(dir)
        assertNull(cache.load(423206957))
        val cards = listOf(
            Card(10012345, LocalDate.of(2024, 1, 1), LocalDate.of(2029, 12, 31), listOf(1, 0, 5, 1)),
            Card(42, LocalDate.of(2020, 2, 29), LocalDate.of(2020, 3, 1), listOf(0, 0, 0, 0)),
        )
        cache.save(423206957, cards)
        assertEquals(cards, CardCache(dir).load(423206957))
        assertEquals(true, dir.resolve("cards-423206957.json").readText().contains("\"2020-02-29\""))
        assertNull(cache.load(1))

        dir.resolve("cards-7.json").writeText("not json")
        assertNull(cache.load(7))
        dir.deleteRecursively()
    }
}
