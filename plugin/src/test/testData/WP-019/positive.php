<?php

function p_call_user_func() {
    call_user_func(<error descr="Request data ($_POST) is used as the callable in call_user_func() without an allow-list (arbitrary function invocation)">$_POST['callback']</error>, 'arg');
}

function p_call_user_func_array_var() {
    $fn = $_GET['fn'];
    call_user_func_array(<error descr="Request data ($_GET) is used as the callable in call_user_func_array() without an allow-list (arbitrary function invocation)">$fn</error>, array());
}

// sanitize_text_field() does NOT make a callable safe — still reported.
function p_sanitized_still_bad() {
    $cb = sanitize_text_field($_REQUEST['cb']);
    call_user_func(<error descr="Request data ($_REQUEST) is used as the callable in call_user_func() without an allow-list (arbitrary function invocation)">$cb</error>);
}

// A capability check in the function: still reported, but weak (admin-only).
function p_admin_weak() {
    if (!current_user_can('manage_options')) { wp_die(); }
    call_user_func(<weak_warning descr="Request data ($_POST) is used as the callable in call_user_func() without an allow-list (arbitrary function invocation). Weak: the function checks a capability, so only privileged users reach it">$_POST['fn']</weak_warning>);
}
