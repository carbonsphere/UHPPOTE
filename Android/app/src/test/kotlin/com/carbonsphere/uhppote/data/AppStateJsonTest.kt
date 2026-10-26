package com.carbonsphere.uhppote.data

import kotlin.test.Test
import kotlin.test.assertEquals

class AppStateJsonTest {
    @Test
    fun jsonRoundTrip() {
        val s = AppState(keepScreenOn = true).addProfile("Office", listOf(device(423206957)), "p1")
            .setDoorName(423206957, 2, "Back door").setActive("p1")
        assertEquals(s, decodeAppState(encodeAppState(s)))
    }

    @Test
    fun unknownJsonFieldsIgnored() {
        val s = decodeAppState("""{"profiles":[],"activeProfileId":null,"somethingNew":1}""")
        assertEquals(AppState(), s)
    }

    @Test
    fun corruptJsonGivesEmptyState() {
        assertEquals(AppState(), decodeAppState("not json"))
    }
}
