package app.replay.core.common

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DiskActivityTest {

    @Test
    fun `pulse emits an event to subscribers`() = runTest {
        DiskActivity.pulses.test {
            DiskActivity.pulse()
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

