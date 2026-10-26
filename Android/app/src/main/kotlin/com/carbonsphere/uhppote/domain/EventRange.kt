package com.carbonsphere.uhppote.domain

/**
 * The event indexes to fetch, newest first: the last [want] events, or, when
 * [readIndex] is given, every event after it. Never more than [max], and
 * never index 0 (the controller numbers events from 1).
 */
fun eventIndexes(newest: Long, want: Long, readIndex: Long?, max: Long = 500): List<Long> {
    if (newest <= 0) return emptyList()
    val oldest = (if (readIndex != null) readIndex + 1 else newest - want + 1)
        .coerceAtLeast(maxOf(1, newest - max + 1))
    if (oldest > newest) return emptyList()
    return (newest downTo oldest).toList()
}
