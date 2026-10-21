<?php
register_rest_<caret>route('p/v1', '/x', array(
    'methods' => 'GET',
    'callback' => 'p_get',
));
register_rest_route('p/v1', '/y', ['methods' => 'GET', 'callback' => 'p_y']);
