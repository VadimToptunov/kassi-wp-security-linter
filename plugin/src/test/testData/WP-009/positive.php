<?php
function p_import() {
    $config = maybe_unserialize(<error descr="Request data ($_POST) reaches maybe_unserialize(): PHP object injection. Use json_decode(), or unserialize() with ['allowed_classes' => false]">base64_decode(trim($_POST['config']))</error>);
}
function p_cookie() {
    $list = isset($_COOKIE['p_list']) ? (array) unserialize(<error descr="Request data ($_COOKIE) reaches unserialize(): PHP object injection. Use json_decode(), or unserialize() with ['allowed_classes' => false]">$_COOKIE['p_list']</error>) : array();
}
function p_rest($request) {
    $data = $request->get_param('payload');
    return unserialize(<error descr="Request data (->get_param()) reaches unserialize(): PHP object injection. Use json_decode(), or unserialize() with ['allowed_classes' => false]">stripslashes($data)</error>, array('allowed_classes' => true));
}
