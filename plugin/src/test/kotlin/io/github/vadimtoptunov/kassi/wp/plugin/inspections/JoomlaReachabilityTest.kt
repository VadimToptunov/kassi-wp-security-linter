package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintAnalyzer
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintSpec
import io.github.vadimtoptunov.kassi.wp.core.reach.JoomlaReachability
import io.github.vadimtoptunov.kassi.wp.core.reach.JoomlaReachability.Entry
import io.github.vadimtoptunov.kassi.wp.plugin.psi.PhpPsiAstNode
import io.github.vadimtoptunov.kassi.wp.plugin.rules.BuiltInRules

/** Covers JoomlaReachability: the Joomla entry-point reachability classifier used by the chain run. */
class JoomlaReachabilityTest : BasePlatformTestCase() {

    private val analyzer by lazy { TaintAnalyzer(TaintSpec.of(BuiltInRules.knowledgeBase.require("JL-007"))) }

    private fun scopes(path: String, code: String): List<AstNode> =
        analyzer.scopes(PhpPsiAstNode.wrap(myFixture.addFileToProject(path, code)))

    private fun methodScope(path: String, code: String): AstNode =
        scopes(path, code).first { it.kind == AstKind.METHOD || it.kind == AstKind.FUNCTION }

    private fun fileScope(path: String, code: String): AstNode =
        scopes(path, code).first { it.kind == AstKind.FILE }

    fun testOnAjaxMethodIsUnauthComAjax() {
        val scope = methodScope(
            "plg_system_foo/foo.php",
            "<?php class PlgSystemFoo { public function onAjaxFoo(\$app) { readfile(\$app->input->getRaw('f')); } }",
        )
        assertEquals(Entry.UNAUTH_AJAX, JoomlaReachability.classify(scope, "plg_system_foo/foo.php"))
    }

    fun testFrontendControllerWithNoAclIsUnauth() {
        val path = "com_foo/site/controllers/items.php"
        val scope = methodScope(path, "<?php class FooControllerItems { public function display(\$app) { readfile(\$app->input->getString('tpl')); } }")
        assertEquals(Entry.UNAUTH_FRONTEND, JoomlaReachability.classify(scope, path))
    }

    fun testAuthoriseInScopeIsGated() {
        val path = "com_foo/site/controllers/items.php"
        val scope = methodScope(
            path,
            "<?php class FooControllerItems { public function display(\$app, \$user) { if (!\$user->authorise('core.manage')) return; readfile(\$app->input->getString('tpl')); } }",
        )
        assertEquals(Entry.GATED, JoomlaReachability.classify(scope, path))
    }

    fun testAdministratorPathRequiresLogin() {
        val path = "administrator/components/com_foo/controllers/items.php"
        val scope = methodScope(path, "<?php class FooControllerItems { public function save(\$app) { readfile(\$app->input->getString('tpl')); } }")
        assertEquals(Entry.ADMIN, JoomlaReachability.classify(scope, path))
    }

    fun testInstallScriptIsNotCountedAsFrontend() {
        // A Joomla install script sits under com_foo but runs only at (admin) install time.
        val path = "com_foo/script.php"
        val scope = methodScope(path, "<?php class Com_FooInstallerScript { public function postflight(\$app) { file_get_contents(\$app->input->getString('p')); } }")
        assertEquals(Entry.UNKNOWN, JoomlaReachability.classify(scope, path))
    }

    fun testJexecGuardedTopLevelScriptIsDirectFile() {
        val path = "com_foo/popups/download.php"
        val scope = fileScope(path, "<?php defined('_JEXEC') or die(); \$f = \$_GET['img']; readfile(\$f);")
        assertEquals(Entry.DIRECT_FILE, JoomlaReachability.classify(scope, path))
    }
}
