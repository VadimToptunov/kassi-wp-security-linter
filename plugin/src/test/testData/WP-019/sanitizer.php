<?php

// An in_array() allow-list with an early exit is the clean path (no string sanitiser helps here).
function s_allowlist_early_exit() {
    $cb = $_POST['callback'];
    $allowed = array('aaa', 'bbb');
    if (!in_array($cb, $allowed, true)) {
        wp_send_json_error('Invalid callback.');
    }
    call_user_func($cb);
}

// The allow-list can also guard the call directly.
function s_allowlist_branch() {
    $cb = sanitize_key($_GET['cb']);
    if (in_array($cb, array('x', 'y'), true)) {
        call_user_func($cb);
    }
}
