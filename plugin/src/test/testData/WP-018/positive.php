<?php

function p_header_concat() {
    header(<error descr="Request data ($_GET) reaches header() without CR/LF removal (HTTP response header injection)">'X-WPAC-UNAPPROVED: ' . $_GET['WPACUnapproved']</error>);
}

function p_header_var() {
    $status = $_POST['status'];
    header(<error descr="Request data ($_POST) reaches header() without CR/LF removal (HTTP response header injection)">"X-Status: $status"</error>);
}

// A capability check in the function: still reported, but weak (admin-only).
function p_admin_weak() {
    if (!current_user_can('manage_options')) { wp_die(); }
    header(<weak_warning descr="Request data ($_REQUEST) reaches header() without CR/LF removal (HTTP response header injection). Weak: the function checks a capability, so only privileged users reach it">'X-Debug: ' . $_REQUEST['debug']</weak_warning>);
}
