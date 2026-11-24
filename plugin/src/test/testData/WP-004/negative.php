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

// OAuth 2.0 token endpoints authenticate the client inside the handler (client_secret / PKCE /
// bearer), not at permission_callback — RFC 6749 §3.2, RFC 7009, RFC 7662, RFC 7591.
register_rest_route('oauth/v1', '/oauth/token', [
    'methods' => 'POST',
    'callback' => 'p_oauth_token',
    'permission_callback' => '__return_true',
]);
register_rest_route('oauth/v1', '/oauth/revoke', [
    'methods' => 'POST',
    'callback' => 'p_oauth_revoke',
    'permission_callback' => '__return_true',
]);
register_rest_route('oauth/v1', '/oauth/introspect', [
    'methods' => 'POST',
    'callback' => 'p_oauth_introspect',
    'permission_callback' => '__return_true',
]);
register_rest_route('oauth/v1', '/oauth/register', [
    'methods' => 'POST',
    'callback' => 'p_oauth_dcr',
    'permission_callback' => '__return_true',
]);

// Not a REST registration.
register_route('p/v1', '/f', ['methods' => 'POST']);
$server->register_rest_route('p/v1', '/g', ['methods' => 'POST']);
