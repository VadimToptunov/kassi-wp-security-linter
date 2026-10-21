<?php
register_rest_route('p/v1', '/a', array(
    'methods' => 'POST',
    'callback' => 'p_save',
    'permission_callback' => <weak_warning descr="permission_callback always allows access on a state-changing REST route (POST). Unless the handler authenticates the request itself, anyone can call it">'__return_true'</weak_warning>,
));

<warning descr="REST route (DELETE) has no permission_callback: anyone, including unauthenticated visitors, can call it">register_rest_route</warning>('p/v1', '/b', [
    'methods' => 'DELETE',
    'callback' => 'p_delete',
]);

<warning descr="REST route (POST) has no permission_callback: anyone, including unauthenticated visitors, can call it">register_rest_route</warning>('p/v1', '/c', [
    'methods' => 'POST',
    'callback' => 'p_null',
    'permission_callback' => null,
]);

register_rest_route('p/v1', '/d', [
    'methods' => WP_REST_Server::EDITABLE,
    'callback' => 'p_edit',
    'permission_callback' => <weak_warning descr="permission_callback always allows access on a state-changing REST route (PATCH, POST, PUT). Unless the handler authenticates the request itself, anyone can call it">function () { return true; }</weak_warning>,
]);

register_rest_route('p/v1', '/e', [
    'methods' => \WP_REST_Server::CREATABLE,
    'callback' => 'p_create',
    'permission_callback' => <weak_warning descr="permission_callback always allows access on a state-changing REST route (POST). Unless the handler authenticates the request itself, anyone can call it">fn() => true</weak_warning>,
]);

register_rest_route('p/v1', '/f', [
    'methods' => ['GET', 'POST'],
    'callback' => 'p_mixed',
    'permission_callback' => <weak_warning descr="permission_callback always allows access on a state-changing REST route (GET, POST). Unless the handler authenticates the request itself, anyone can call it">'\__return_true'</weak_warning>,
]);

register_rest_route('p/v1', '/g', array(
    array('methods' => 'GET', 'callback' => 'p_get', 'permission_callback' => '__return_true'),
    <warning descr="REST route (POST) has no permission_callback: anyone, including unauthenticated visitors, can call it">array</warning>('methods' => WP_REST_Server::CREATABLE, 'callback' => 'p_post'),
    'schema' => 'p_schema',
));

// Methods unknown, but no callback at all: the route is public whatever it does.
<warning descr="REST route has no permission_callback: anyone, including unauthenticated visitors, can call it">register_rest_route</warning>('p/v1', '/h', [
    'methods' => $method,
    'callback' => 'p_dynamic',
]);
