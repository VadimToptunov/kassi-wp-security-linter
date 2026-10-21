<?php
function p_delete() {
    $file = sanitize_text_field(wp_unslash($_POST['file']));
    if (file_exists(WP_CONTENT_DIR . '/uploads/p/' . $file)) {
        unlink(<warning descr="File path built from request data ($_POST) reaches unlink() without sanitize_file_name(), basename() or a realpath()/validate_file() check">WP_CONTENT_DIR . '/uploads/p/' . $file</warning>);
    }
}

function p_download() {
    $name = str_replace('../', '', $_GET['name']);
    readfile(<warning descr="File path built from request data ($_GET) reaches readfile() without sanitize_file_name(), basename() or a realpath()/validate_file() check">"/var/exports/$name"</warning>);
}

function p_template() {
    include <warning descr="File path built from request data ($_REQUEST) reaches include without sanitize_file_name(), basename() or a realpath()/validate_file() check">plugin_dir_path(__FILE__) . 'views/' . $_REQUEST['view'] . '.php'</warning>;
}

function p_upload() {
    $dest = wp_upload_dir()['basedir'] . '/p/' . $_FILES['doc']['name'];
    move_uploaded_file($_FILES['doc']['tmp_name'], <warning descr="File path built from request data ($_FILES) reaches move_uploaded_file() without sanitize_file_name(), basename() or a realpath()/validate_file() check">$dest</warning>);
}

function p_copy($request) {
    $src = $request->get_param('src');
    copy(<warning descr="File path built from request data (->get_param()) reaches copy() without sanitize_file_name(), basename() or a realpath()/validate_file() check">$src</warning>, '/tmp/p.bak');
}
