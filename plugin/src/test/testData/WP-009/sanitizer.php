<?php
function s_json() { $config = json_decode(wp_unslash($_POST['config']), true); }
function s_no_classes() { $list = unserialize($_COOKIE['s_list'], ['allowed_classes' => false]); }
function s_int() { $n = unserialize(intval($_GET['n'])); }
function s_bounded_list() { $x = unserialize($_POST['x'], ['allowed_classes' => ['PYS_Settings', 'PYS_Event']]); }
function s_empty_list() { $y = unserialize($_COOKIE['y'], ['allowed_classes' => []]); }
