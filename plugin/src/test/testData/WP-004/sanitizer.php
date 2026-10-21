<?php
// Real permission checks: the defences the CVE fixes added. All silent.
register_rest_route('p/v1', '/a', array(
    'methods' => 'POST',
    'callback' => 'p_save',
    'permission_callback' => function () {
        return current_user_can('manage_options');
    },
));

register_rest_route('p/v1', '/b', [
    'methods' => WP_REST_Server::CREATABLE,
    'callback' => [$this, 'create'],
    'permission_callback' => [$this, 'get_permissions'],
]);

register_rest_route('p/v1', '/c', [
    'methods' => WP_REST_Server::DELETABLE,
    'callback' => 'p_delete',
    'permission_callback' => fn() => current_user_can('delete_posts'),
]);

register_rest_route('p/v1', '/d', [
    'methods' => 'PUT',
    'callback' => 'p_put',
    'permission_callback' => 'p_can_edit',
]);
