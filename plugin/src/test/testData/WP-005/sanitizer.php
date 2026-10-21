<?php
// The defences the fixes add: all silent.
function s_absint() { global $wpdb; $id = absint($_GET['id']); $wpdb->query("DELETE FROM t WHERE id = $id"); }
function s_prepare() { global $wpdb; $wpdb->get_row($wpdb->prepare("SELECT * FROM t WHERE name = %s", $_POST['name'])); }
function s_cast() { global $wpdb; $id = (int) $_GET['id']; $wpdb->get_var("SELECT x FROM t WHERE id = " . $id); }
function s_esc_sql() { global $wpdb; $n = esc_sql($_GET['n']); $wpdb->query("DELETE FROM t WHERE n = '$n'"); }
function s_sprintf_numeric() { global $wpdb; $wpdb->query(sprintf("DELETE FROM t WHERE id = %d", $_GET['id'])); }
function s_reassigned() { global $wpdb; $id = $_GET['id']; $id = intval($id); $wpdb->query("DELETE FROM t WHERE id = $id"); }

// Allow-list guards (LiteSpeed Cache db-optm pattern): clean inside the guarded branch...
function s_allowlist_branch() {
    global $wpdb;
    $tb_param = sanitize_text_field(wp_unslash($_GET['tb']));
    $tb = false;
    if (in_array($tb_param, $names, true)) {
        $tb = $tb_param;
    }
    $wpdb->query('ALTER TABLE `' . $tb . '` ENGINE = InnoDB');
}

// ...and after an early exit on the negated guard.
function s_allowlist_early_exit() {
    global $wpdb;
    $col = $_GET['orderby'];
    if (!in_array($col, array('name', 'date'), true)) {
        wp_die('bad column');
    }
    $wpdb->get_results("SELECT * FROM t ORDER BY $col");
}
