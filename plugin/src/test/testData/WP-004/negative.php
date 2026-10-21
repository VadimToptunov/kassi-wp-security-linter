<?php
// Shapes the rule cannot prove: all silent by design.
register_rest_route('p/v1', '/a', $args);

register_rest_route('p/v1', '/b', [
    ...$defaults,
    'methods' => 'POST',
    'callback' => 'p_spread',
]);

register_rest_route('p/v1', '/c', [
    'methods' => $method,
    'callback' => 'p_dyn',
    'permission_callback' => '__return_true',
]);

register_rest_route('p/v1', '/d', [
    $key => 'POST',
    'callback' => 'p_key',
]);

register_rest_route('p/v1', '/e', [
    'methods' => 'POST',
    'callback' => 'p_cond',
    'permission_callback' => function ($request) {
        if ($request['token'] === P_TOKEN) {
            return true;
        }
        return false;
    },
]);

// Not a REST registration.
register_route('p/v1', '/f', ['methods' => 'POST']);
$server->register_rest_route('p/v1', '/g', ['methods' => 'POST']);
