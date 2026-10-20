package io.github.vadimtoptunov.kassi.wp.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `core` must stay parser- and IDE-neutral: other consumers run the same engine over a
 * headless PHP parser. Two independent checks: the source never imports IDE/plugin packages, and the
 * test classpath (which mirrors the module's dependencies) cannot even load them.
 */
class ModuleBoundaryTest {
    private val forbiddenPrefixes = listOf(
        "com.intellij.",
        "com.jetbrains.",
        "org.jetbrains.intellij.",
        "io.github.vadimtoptunov.kassi.wp.plugin",
    )

    @Test
    fun `core sources import nothing from IntelliJ, the PHP plugin or the plugin module`() {
        val srcDir = File(requireNotNull(System.getProperty("kassi.core.srcDir")) { "kassi.core.srcDir not set" })
        val sources = srcDir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
        assertTrue(sources.isNotEmpty(), "no sources found under $srcDir")

        val importRe = Regex("""^\s*import\s+([\w.]+)""", RegexOption.MULTILINE)
        val violations = sources.flatMap { file ->
            importRe.findAll(file.readText()).map { it.groupValues[1] }
                .filter { imp -> forbiddenPrefixes.any { imp.startsWith(it) } }
                .map { "${file.relativeTo(srcDir)}: import $it" }
        }
        assertTrue(violations.isEmpty(), "core must not depend on IDE/plugin code:\n" + violations.joinToString("\n"))
    }

    @Test
    fun `IntelliJ and PHP PSI classes are not on the core classpath`() {
        assertFailsWith<ClassNotFoundException> { Class.forName("com.intellij.psi.PsiElement") }
        assertFailsWith<ClassNotFoundException> { Class.forName("com.jetbrains.php.lang.psi.PhpFile") }
    }
}
