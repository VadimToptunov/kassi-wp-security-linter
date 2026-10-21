<?php
function s_basename() { unlink(WP_CONTENT_DIR . '/uploads/s/' . basename($_POST['file'])); }
function s_file_name() { $f = sanitize_file_name(wp_unslash($_GET['f'])); readfile("/var/exports/$f"); }
function s_allowlist() {
    $view = $_GET['view'];
    if (!in_array($view, array('list', 'grid'), true)) {
        wp_die();
    }
    include __DIR__ . "/views/$view.php";
}
function s_validate_file() {
    $f = $_GET['f'];
    if (0 !== validate_file($f)) {
        wp_die('bad path');
    }
    readfile(WP_CONTENT_DIR . '/' . $f);
}
function s_realpath() { $p = realpath(WP_CONTENT_DIR . '/' . $_GET['p']); readfile($p); }
function s_tmp_name() { move_uploaded_file($_FILES['doc']['tmp_name'], wp_upload_dir()['basedir'] . '/s/x.pdf'); }
