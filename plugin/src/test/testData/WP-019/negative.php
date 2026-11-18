<?php

// Constant callable — nothing from the request.
function n_constant() {
    call_user_func('my_plugin_handler', 'arg');
}

// Callable from a site option, not the request.
function n_option() {
    $cb = get_option('my_callback');
    call_user_func($cb);
}

// A method callable on $this, not the request.
function n_method() {
    call_user_func(array($this, 'render'), 'arg');
}

// A fixed literal prefix pins the callable to one namespace — not arbitrary invocation.
function n_prefixed_dispatch() {
    $func = $_GET['action'];
    call_user_func('myplugin_ajax_' . $func . '_handler');
}
