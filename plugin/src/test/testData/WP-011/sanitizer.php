<?php
function s_filetype() {
    $check = wp_check_filetype($_FILES['f']['name']);
    if (!$check['type']) { die('File type is not allowed.'); }
    move_uploaded_file($_FILES['f']['tmp_name'], $dir . '/' . $_FILES['f']['name']);
}
function s_ext_allowlist($file) {
    $ext = strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
    if (!in_array($ext, array('jpg', 'png'), true)) { return; }
    move_uploaded_file($file['tmp_name'], $dir . '/' . uniqid() . '.' . $ext);
}
function s_sniff($tmp) {
    if (false === getimagesize($tmp)) { return; }
    move_uploaded_file($tmp, $dir . '/avatar-' . get_current_user_id());
}
