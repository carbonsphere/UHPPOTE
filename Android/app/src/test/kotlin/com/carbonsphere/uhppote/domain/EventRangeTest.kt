package com.carbonsphere.uhppote.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class EventRangeTest {
    @Test
    fun lastN() = assertEquals((100L downTo 81L).toList(), eventIndexes(newest = 100, want = 20, readIndex = null))

    @Test
    fun fewerThanWant() = assertEquals((5L downTo 1L).toList(), eventIndexes(newest = 5, want = 20, readIndex = null))

    @Test
    fun unread() = assertEquals((100L downTo 96L).toList(), eventIndexes(newest = 100, want = 20, readIndex = 95))

    @Test
    fun unreadNone() {
        assertEquals(emptyList(), eventIndexes(newest = 100, want = 20, readIndex = 100))
        assertEquals(emptyList(), eventIndexes(newest = 100, want = 20, readIndex = 150))
    }

    @Test
    fun capAt500() {
        assertEquals((10000L downTo 9501L).toList(), eventIndexes(newest = 10000, want = 2000, readIndex = null))
        assertEquals((10000L downTo 9501L).toList(), eventIndexes(newest = 10000, want = 20, readIndex = 0))
    }

    @Test
    fun newestZeroEmpty() = assertEquals(emptyList(), eventIndexes(newest = 0, want = 20, readIndex = null))
}
