<?php
// JL-007 (CWE-22) positives: Joomla request input reaches include/require or a file read.

// getString keeps '/' and '..', so it carries traversal into require_once.
function jl007_require_getstring($app) {
    $view = $app->input->getString('view');
    require_once <warning descr="File path built from request data (->getstring()) reaches require_once without basename(), JPath::clean() or a realpath() check">JPATH_COMPONENT . '/views/' . $view . '/view.html.php'</warning>;
}

function jl007_readfile_superglobal() {
    readfile(<warning descr="File path built from request data ($_GET) reaches readfile() without basename(), JPath::clean() or a realpath() check">'/var/data/' . $_GET['file']</warning>);
}

function jl007_filegetcontents_getpath($input) {
    $p = $input->getPath('tpl');
    return file_get_contents(<warning descr="File path built from request data (->getpath()) reaches file_get_contents() without basename(), JPath::clean() or a realpath() check">JPATH_ROOT . '/' . $p</warning>);
}
