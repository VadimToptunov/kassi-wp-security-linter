<?php

// Constant header — nothing from the request.
function n_constant() {
    header('Content-Type: application/json');
}

// Header value from a site option, not the request.
function n_from_option() {
    header('X-Generator: ' . get_bloginfo('version'));
}

// A numeric cast neutralises the request value.
function n_int_cast() {
    $code = (int) $_GET['code'];
    header("X-Code: $code");
}
