<?php
function s_json() { $config = json_decode(wp_unslash($_POST['config']), true); }
function s_no_classes() { $list = unserialize($_COOKIE['s_list'], ['allowed_classes' => false]); }
function s_int() { $n = unserialize(intval($_GET['n'])); }
