<?php
echo <warning descr="Request data ($_GET) is output without escaping (esc_html, esc_attr, esc_url, wp_kses)">$_GET['q']</warning>;
$s = wp_unslash($_POST['s']);
?>
<input value="<?php echo <warning descr="Request data ($_POST) is output without escaping (esc_html, esc_attr, esc_url, wp_kses)">$s</warning>; ?>">
<p><?= <warning descr="Request data ($_REQUEST) is output without escaping (esc_html, esc_attr, esc_url, wp_kses)">$_REQUEST['x']</warning> ?></p>
<?php
print <warning descr="Request data ($_GET) is output without escaping (esc_html, esc_attr, esc_url, wp_kses)">"Hello " . $_GET['name']</warning>;
echo 'ok', <warning descr="Request data ($_POST) is output without escaping (esc_html, esc_attr, esc_url, wp_kses)">"v=$s"</warning>;

function p_self_link() {
    $u = $_SERVER['REQUEST_URI'];
    echo <warning descr="Request data ($_SERVER) is output without escaping (esc_html, esc_attr, esc_url, wp_kses)">'<a href="' . $u . '">'</warning>;
}

function p_guard_only_covers_its_branch() {
    $v = $_GET['v'];
    if (in_array($v, array('a', 'b'), true)) {
        echo $v;
    }
    echo <warning descr="Request data ($_GET) is output without escaping (esc_html, esc_attr, esc_url, wp_kses)">$v</warning>;
}

function p_guard_without_exit_does_not_clean() {
    $v = $_GET['v'];
    if (!is_numeric($v)) {
        error_log('not numeric');
    }
    echo <warning descr="Request data ($_GET) is output without escaping (esc_html, esc_attr, esc_url, wp_kses)">$v</warning>;
}

function p_reassigned_after_guard() {
    $v = $_GET['v'];
    if (!is_numeric($v)) {
        return;
    }
    $v = $_GET['w'];
    echo <warning descr="Request data ($_GET) is output without escaping (esc_html, esc_attr, esc_url, wp_kses)">$v</warning>;
}
