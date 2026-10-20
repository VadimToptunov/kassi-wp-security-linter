package io.github.vadimtoptunov.kassi.wp.plugin.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.ast.enclosingFunction
import io.github.vadimtoptunov.kassi.wp.core.ast.walk

/** Proves the PSI → core.ast mapping on real PHP parsed by the bundled PHP plugin. */
class PhpPsiAstNodeTest : BasePlatformTestCase() {

    private fun parse(code: String): AstNode {
        val file = myFixture.configureByText("t.php", code)
        return PhpPsiAstNode.wrap(file)
    }

    private fun AstNode.first(kind: AstKind, name: String? = null): AstNode =
        walk().first { it.kind == kind && (name == null || it.name == name) }

    fun testFunctionCallVariablesAndSuperglobals() {
        val root = parse(
            """
            <?php
            function handle_save() {
                ${'$'}id = (int) ${'$'}_GET['id'];
                \update_option('k', ${'$'}id);
            }
            """.trimIndent(),
        )
        assertEquals(AstKind.FILE, root.kind)

        val fn = root.first(AstKind.FUNCTION)
        assertEquals("handle_save", fn.name)
        assertEquals(AstKind.BLOCK, fn.child(AstRole.BODY)?.kind)

        val call = root.first(AstKind.CALL)
        assertEquals("update_option", call.name)
        assertEquals(listOf(AstKind.STRING_LITERAL, AstKind.VARIABLE), call.arguments.map { it.kind })
        assertEquals("k", call.arguments[0].stringValue)
        assertEquals(fn, call.enclosingFunction())
        assertEquals(4, call.location.line)

        val assign = root.first(AstKind.ASSIGNMENT)
        assertEquals("id", assign.child(AstRole.TARGET)?.name)
        val cast = assign.child(AstRole.VALUE)!!
        assertEquals(AstKind.CAST, cast.kind)
        assertEquals("int", cast.name)

        val access = cast.child(AstRole.VALUE)!!
        assertEquals(AstKind.ARRAY_ACCESS, access.kind)
        assertEquals("_GET", access.child(AstRole.RECEIVER)?.name)
        assertEquals("id", access.child(AstRole.KEY)?.stringValue)
    }

    fun testMethodCallConcatInterpolationEcho() {
        val root = parse(
            """
            <?php
            ${'$'}sql = "SELECT * FROM t WHERE id = ${'$'}id";
            ${'$'}sql .= ' AND x = ' . ${'$'}x;
            ${'$'}wpdb->get_results(${'$'}sql);
            echo ${'$'}name, 'ok';
            """.trimIndent(),
        )
        val interpolated = root.first(AstKind.INTERPOLATED_STRING)
        assertEquals(listOf("id"), interpolated.children.map { it.name })

        val concatAssign = root.first(AstKind.CONCAT_ASSIGNMENT)
        assertEquals("sql", concatAssign.child(AstRole.TARGET)?.name)
        val concat = concatAssign.child(AstRole.VALUE)!!
        assertEquals(AstKind.CONCAT, concat.kind)
        assertEquals("x", concat.child(AstRole.RIGHT)?.name)

        val mc = root.first(AstKind.METHOD_CALL)
        assertEquals("get_results", mc.name)
        assertEquals("wpdb", mc.child(AstRole.RECEIVER)?.name)
        assertEquals(listOf("sql"), mc.arguments.map { it.name })

        val echo = root.first(AstKind.ECHO)
        assertEquals(listOf(AstKind.VARIABLE, AstKind.STRING_LITERAL), echo.arguments.map { it.kind })
    }

    fun testArrayLiteralEntriesAndClosure() {
        val root = parse(
            """
            <?php
            register_rest_route('ns/v1', '/x', array(
                'methods' => 'POST',
                'permission_callback' => '__return_true',
                'callback' => function (${'$'}req) { return 1; },
            ));
            ${'$'}list = [1, ${'$'}y];
            """.trimIndent(),
        )
        val args = root.first(AstKind.CALL, "register_rest_route").arguments
        val options = args[2]
        assertEquals(AstKind.ARRAY_LITERAL, options.kind)
        val entries = options.arguments
        assertEquals(3, entries.size)
        assertTrue(entries.all { it.kind == AstKind.ARRAY_ENTRY })
        assertEquals(
            listOf("methods", "permission_callback", "callback"),
            entries.map { it.child(AstRole.KEY)?.stringValue },
        )
        assertEquals("__return_true", entries[1].child(AstRole.VALUE)?.stringValue)
        val closure = entries[2].child(AstRole.VALUE)!!
        assertEquals(AstKind.CLOSURE, closure.kind)
        // The closure-expression wrapper is transparent in both directions.
        assertTrue(closure in closure.parent!!.children)
        assertEquals(closure, closure.walk().first())

        val list = root.walk().filter { it.kind == AstKind.ARRAY_LITERAL }.last()
        assertEquals(listOf(AstKind.NUMBER_LITERAL, AstKind.VARIABLE), list.arguments.map { it.child(AstRole.VALUE)?.kind })
        assertEquals(listOf(null, null), list.arguments.map { it.child(AstRole.KEY) })
    }
}
