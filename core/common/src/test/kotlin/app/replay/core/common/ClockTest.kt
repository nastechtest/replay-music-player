package app.replay.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClockTest {

    @Test
    fun `system clock returns reasonable current time`() {
        val now = Clock.System.nowMillis()
        assertTrue(now > 0)
    }

    @Test
    fun `manual clock advances and sets time accurately`() {
        val clock = ManualClock(1000L)
        assertEquals(1000L, clock.nowMillis())

        clock.advanceBy(500L)
        assertEquals(1500L, clock.nowMillis())

        clock.set(3000L)
        assertEquals(3000L, clock.nowMillis())

        assertFailsWith<IllegalArgumentException> {
            clock.advanceBy(-10L)
        }
    }
}

