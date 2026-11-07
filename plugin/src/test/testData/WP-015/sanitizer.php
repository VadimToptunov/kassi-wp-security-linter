<?php
function s_arg() { system('ping -c 1 ' . escapeshellarg($_GET['host'])); }

function s_cmd() {
    $c = escapeshellcmd($_POST['cmd']);
    exec($c);
}

function s_allowlist() {
    $action = $_GET['action'];
    if (!in_array($action, array('start', 'stop'), true)) {
        wp_die();
    }
    system("service nginx $action");
}
