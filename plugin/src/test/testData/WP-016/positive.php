<?php
function p_eval_var() {
    $code = $_POST['code'];
    eval(<error descr="Request data ($_POST) is evaluated as PHP code by eval()">$code</error>);
}

function p_eval_concat() {
    eval(<error descr="Request data ($_GET) is evaluated as PHP code by eval()">'return ' . $_GET['expr'] . ';'</error>);
}

function p_assert() {
    assert(<error descr="Request data ($_REQUEST) is evaluated as PHP code by assert()">$_REQUEST['cond']</error>);
}

function p_create_function() {
    $fn = create_function('$x', <error descr="Request data ($_POST) is evaluated as PHP code by create_function()">$_POST['body']</error>);
}

// A capability check in the function: still reported, but weak (admin-only).
function p_admin_eval() {
    if (!current_user_can('manage_options')) { wp_die(); }
    eval(<weak_warning descr="Request data ($_POST) is evaluated as PHP code by eval(). Weak: the function checks a capability, so only privileged users reach it">$_POST['snippet']</weak_warning>);
}
