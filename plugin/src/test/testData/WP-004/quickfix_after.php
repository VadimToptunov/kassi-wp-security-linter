<?php
register_rest_route('p/v1', '/x', array(
    'methods' => 'GET',
    'callback' => 'p_get',
    'permission_callback' => '__return_true',
));
register_rest_route('p/v1', '/y', ['methods' => 'GET', 'callback' => 'p_y']);
