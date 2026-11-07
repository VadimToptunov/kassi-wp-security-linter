<?php
function s_int_cast() { eval('$x = ' . (int) $_GET['n'] . ';'); }

function s_allowlist() {
    $op = $_GET['op'];
    if (!in_array($op, array('add', 'sub'), true)) {
        wp_die();
    }
    eval("\$result = compute_$op();");
}
