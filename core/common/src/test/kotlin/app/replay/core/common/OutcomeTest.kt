package app.replay.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutcomeTest {

    @Test
    fun `success returns value and maps correctly`() {
        val success = Outcome.success("hello")
        assertTrue(success is Outcome.Success)
        assertEquals("hello", success.value)
        assertEquals("hello", success.getOrNull())

        val mapped = success.map { it.length }
        assertTrue(mapped is Outcome.Success)
        assertEquals(5, mapped.value)
    }

    @Test
    fun `failure preserves error on map and returns null for getOrNull`() {
        val error = ReplayError.NotFound("album")
        val failure: Outcome<String> = Outcome.failure(error)
        assertTrue(failure is Outcome.Failure)
        assertEquals(error, failure.error)
        assertNull(failure.getOrNull())

        val mapped = failure.map { it.length }
        assertTrue(mapped is Outcome.Failure)
        assertEquals(error, mapped.error)
    }

    @Test
    fun `transient errors are classified correctly`() {
        assertTrue(ReplayError.Offline.isTransient)
        assertTrue(ReplayError.Network("timeout").isTransient)
        assertTrue(ReplayError.Server(503).isTransient)
        assertTrue(ReplayError.RateLimited(1000L).isTransient)
        assertTrue(ReplayError.Unexpected("unknown").isTransient)

        assertFalse(ReplayError.AuthRequired("key").isTransient)
        assertFalse(ReplayError.AuthExpired("expired").isTransient)
        assertFalse(ReplayError.NotFound("track").isTransient)
        assertFalse(ReplayError.InvalidMedia("corrupt").isTransient)
        assertFalse(ReplayError.UnsupportedFormat("aac").isTransient)
        assertFalse(ReplayError.PolicyDisabled("disabled").isTransient)
        assertFalse(ReplayError.PermissionRequired("android.permission.READ_MEDIA_AUDIO").isTransient)
        assertFalse(ReplayError.Unsupported("subsonic").isTransient)
        assertFalse(ReplayError.NotPlayable("geo-restricted").isTransient)
        assertFalse(ReplayError.UserActionRequired("open-browser").isTransient)
    }
}
