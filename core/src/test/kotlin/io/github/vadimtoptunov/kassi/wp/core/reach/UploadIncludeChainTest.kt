package io.github.vadimtoptunov.kassi.wp.core.reach

import io.github.vadimtoptunov.kassi.wp.core.reach.JoomlaReachability.Entry
import io.github.vadimtoptunov.kassi.wp.core.reach.UploadIncludeChain.FileCapability
import io.github.vadimtoptunov.kassi.wp.core.reach.UploadIncludeChain.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UploadIncludeChainTest {

    private fun write(ext: String, entry: Entry) = FileCapability(ext, Kind.WRITE, entry, "move_uploaded_file()", "$ext/up.php:10")
    private fun include(ext: String, entry: Entry) = FileCapability(ext, Kind.INCLUDE, entry, "require_once", "$ext/run.php:20")

    @Test
    fun `pairs a write with an include in the same extension only`() {
        val caps = listOf(
            write("com_foo", Entry.UNAUTH_FRONTEND),
            include("com_foo", Entry.UNAUTH_AJAX),
            include("com_bar", Entry.UNAUTH_FRONTEND), // no write in com_bar -> no candidate
        )
        val candidates = UploadIncludeChain.candidates(caps)
        assertEquals(1, candidates.size)
        assertEquals("com_foo", candidates.single().ext)
        assertTrue(candidates.single().unauth)
    }

    @Test
    fun `ranks fully-unauth chains above partly-gated ones`() {
        val caps = listOf(
            // com_gated: include is admin-only -> partly unauth only
            write("com_gated", Entry.UNAUTH_FRONTEND),
            include("com_gated", Entry.ADMIN),
            // com_hot: both halves unauth
            write("com_hot", Entry.UNAUTH_FRONTEND),
            include("com_hot", Entry.UNAUTH_FRONTEND),
        )
        val candidates = UploadIncludeChain.candidates(caps)
        assertEquals(2, candidates.size)
        assertEquals("com_hot", candidates.first().ext)
        assertTrue(candidates.first().unauth)
        assertTrue(candidates[1].partlyUnauth)
        assertEquals(false, candidates[1].unauth)
    }

    @Test
    fun `no write means no chain`() {
        val caps = listOf(include("com_foo", Entry.UNAUTH_FRONTEND), include("com_foo", Entry.UNAUTH_AJAX))
        assertTrue(UploadIncludeChain.candidates(caps).isEmpty())
    }
}
