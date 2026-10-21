package io.github.vadimtoptunov.kassi.wp.core.flow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceSpecTest {
    @Test
    fun `parses the four source notations`() {
        assertEquals(SourceSpec.Superglobal("_GET", null), SourceSpec.parse("\$_GET"))
        assertEquals(SourceSpec.Superglobal("_SERVER", "REQUEST_URI"), SourceSpec.parse("\$_SERVER[REQUEST_URI]"))
        assertEquals(SourceSpec.Method("get_param"), SourceSpec.parse("->get_param"))
        assertEquals(SourceSpec.Function("filter_input"), SourceSpec.parse("filter_input"))
    }

    @Test
    fun `superglobal key patterns match exactly or by prefix`() {
        val http = SourceSpec.parse("\$_SERVER[HTTP_*]") as SourceSpec.Superglobal
        assertTrue(http.matchesKey("HTTP_USER_AGENT"))
        assertFalse(http.matchesKey("REMOTE_ADDR"))
        assertFalse(http.matchesKey(null)) // non-literal key: not proven
        val uri = SourceSpec.parse("\$_SERVER[REQUEST_URI]") as SourceSpec.Superglobal
        assertTrue(uri.matchesKey("REQUEST_URI"))
        assertFalse(uri.matchesKey("REQUEST_URIX"))
    }

    @Test
    fun `malformed sources are rejected`() {
        assertFailsWith<IllegalArgumentException> { SourceSpec.parse("\$GET") }
        assertFailsWith<IllegalArgumentException> { SourceSpec.parse("get param") }
    }

    @Test
    fun `taint union keeps every source label`() {
        assertEquals("\$_GET, \$_POST", (Taint(setOf("\$_POST")) + Taint(setOf("\$_GET"))).label)
        assertEquals(null, Taint.union(listOf(null, null)))
    }
}
