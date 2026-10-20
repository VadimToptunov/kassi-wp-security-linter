package io.github.vadimtoptunov.kassi.wp.core.ast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises the traversal helpers over a hand-built tree — no parser involved. */
class AstTraversalTest {

    private class Node(
        override val kind: AstKind,
        override val name: String? = null,
        override val children: List<Node> = emptyList(),
        override val arguments: List<AstNode> = emptyList(),
    ) : AstNode {
        override var parent: AstNode? = null
        override val text: String get() = name ?: kind.name
        override val location = SourceLocation("t.php", 1, 0, 0)
        override val stringValue: String? = null
        override fun child(role: AstRole): AstNode? = null

        init {
            children.forEach { it.parent = this }
        }
    }

    // function handler() { echo $_GET; wp_redirect($x); }
    private val get = Node(AstKind.VARIABLE, "_GET")
    private val echo = Node(AstKind.ECHO, children = listOf(get), arguments = listOf(get))
    private val redirect = Node(AstKind.CALL, "WP_Redirect", children = listOf(Node(AstKind.VARIABLE, "x")))
    private val body = Node(AstKind.BLOCK, children = listOf(echo, redirect))
    private val fn = Node(AstKind.FUNCTION, "handler", children = listOf(body))
    private val file = Node(AstKind.FILE, children = listOf(fn))

    @Test
    fun `walk is pre-order and source-ordered`() {
        assertEquals(
            listOf(AstKind.FILE, AstKind.FUNCTION, AstKind.BLOCK, AstKind.ECHO, AstKind.VARIABLE, AstKind.CALL, AstKind.VARIABLE),
            file.walk().map { it.kind }.toList(),
        )
    }

    @Test
    fun `enclosingFunction finds the nearest function-like ancestor`() {
        assertEquals(fn, get.enclosingFunction())
        assertNull(fn.enclosingFunction())
    }

    @Test
    fun `isCallTo is case-insensitive like PHP function names`() {
        assertTrue(redirect.isCallTo("wp_redirect"))
        assertEquals(listOf(redirect), file.descendantsOfKind(AstKind.CALL).toList())
    }
}
