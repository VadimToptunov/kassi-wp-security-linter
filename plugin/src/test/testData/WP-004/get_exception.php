<?php
// Read-only routes: an explicit '__return_true' is the documented way to make them public.
register_rest_route('p/v1', '/a', ['methods' => 'GET', 'callback' => 'p_a', 'permission_callback' => '__return_true']);
register_rest_route('p/v1', '/b', ['methods' => WP_REST_Server::READABLE, 'callback' => 'p_b', 'permission_callback' => '__return_true']);
register_rest_route('p/v1', '/c', ['methods' => 'GET, HEAD', 'callback' => 'p_c', 'permission_callback' => '__return_true']);
register_rest_route('p/v1', '/d', ['callback' => 'p_d', 'permission_callback' => '__return_true']);

// Missing callback on a read-only route: weak (WordPress warns too).
<weak_warning descr="REST route has no permission_callback. Declare it explicitly ('__return_true' if this read-only route is meant to be public)">register_rest_route</weak_warning>('p/v1', '/e', ['methods' => 'get', 'callback' => 'p_e']);
<weak_warning descr="REST route has no permission_callback. Declare it explicitly ('__return_true' if this read-only route is meant to be public)">register_rest_route</weak_warning>('p/v1', '/f', ['callback' => 'p_f']);
