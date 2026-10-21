<?php
function p_interpolated() {
    global $wpdb;
    $id = $_GET['id'];
    $wpdb->query(<error descr="SQL built from request data ($_GET) reaches $wpdb->query() without $wpdb->prepare() placeholders">"DELETE FROM t WHERE id = $id"</error>);
}

function p_sanitize_text_field_is_not_sql_safe() {
    global $wpdb;
    $name = sanitize_text_field(wp_unslash($_POST['name']));
    $sql = "SELECT * FROM {$wpdb->prefix}t WHERE name = '" . $name . "'";
    return $wpdb->get_results(<error descr="SQL built from request data ($_POST) reaches $wpdb->get_results() without $wpdb->prepare() placeholders">$sql</error>);
}

function p_rest_param($request) {
    global $wpdb;
    $order = $request->get_param('order');
    $sql = "SELECT * FROM t";
    $sql .= " ORDER BY " . $order;
    return $wpdb->get_var(<error descr="SQL built from request data (->get_param()) reaches $wpdb->get_var() without $wpdb->prepare() placeholders">$sql</error>);
}

class P_Repo {
    function find() {
        $s = $_REQUEST['s'];
        return $this->wpdb->get_row(<error descr="SQL built from request data ($_REQUEST) reaches $wpdb->get_row() without $wpdb->prepare() placeholders">sprintf("SELECT * FROM t WHERE s = '%s'", $s)</error>);
    }
}

function p_input_in_prepare_format() {
    global $wpdb;
    $ids = implode(',', $_POST['ids']);
    $wpdb->get_col($wpdb->prepare(<error descr="SQL built from request data ($_POST) reaches $wpdb->prepare() without $wpdb->prepare() placeholders">"SELECT id FROM t WHERE id IN ($ids) AND x = %d"</error>, 1));
}

function p_foreach() {
    global $wpdb;
    foreach ($_POST['rows'] as $k => $v) {
        $wpdb->query(<error descr="SQL built from request data ($_POST) reaches $wpdb->query() without $wpdb->prepare() placeholders">"UPDATE t SET v = '$v' WHERE k = '$k'"</error>);
    }
}
