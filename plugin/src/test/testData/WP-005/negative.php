<?php
// Flows the rule cannot prove: all silent by design.
function n_param($id) { global $wpdb; $wpdb->query("DELETE FROM t WHERE id = $id"); }
function n_unknown_function() { global $wpdb; $v = get_option('x'); $wpdb->query("DELETE FROM t WHERE v = '$v'"); }
function n_table_prefix() { global $wpdb; $t = $wpdb->prefix . 'x'; $wpdb->query("DROP TABLE $t"); }
function n_not_wpdb($pdo) { $pdo->query("SELECT * FROM t WHERE x = " . $_GET['x']); }
function n_closure_scope() {
    $x = $_GET['x'];
    $f = function () use ($x) { global $wpdb; $wpdb->query("DELETE FROM t WHERE x = '$x'"); };
}
function n_server_not_input() { global $wpdb; $ip = $_SERVER['REMOTE_ADDR']; $wpdb->query("INSERT INTO log VALUES ('$ip')"); }
