package io.github.vadimtoptunov.kassi.wp.core.config

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathExclusionsTest {
    private val ex = PathExclusions(listOf("vendor/", "node_modules/", "tests/", "src/legacy", "*.min.php", "build/**/gen/*.php", "# comment", ""))

    @Test
    fun `directory names match at any depth`() {
        assertTrue(ex.isExcluded("vendor/autoload.php"))
        assertTrue(ex.isExcluded("modules/pay/vendor/lib/x.php"))
        assertTrue(ex.isExcluded("tests/unit/FooTest.php"))
        assertFalse(ex.isExcluded("src/vendors.php"))
        assertFalse(ex.isExcluded("vendor.php"))
    }

    @Test
    fun `prefixes and globs`() {
        assertTrue(ex.isExcluded("src/legacy/old.php"))
        assertFalse(ex.isExcluded("src/legacy2/x.php"))
        assertTrue(ex.isExcluded("assets/app.min.php"))
        assertTrue(ex.isExcluded("build/a/b/gen/x.php"))
        assertFalse(ex.isExcluded("build/a/b/x.php"))
    }

    @Test
    fun `windows separators and leading slash are normalised`() {
        assertTrue(ex.isExcluded("\\vendor\\a.php"))
        assertTrue(PathExclusions(listOf("/src/legacy/")).isExcluded("src/legacy/a.php"))
    }
}
