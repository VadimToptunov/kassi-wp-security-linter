<?php
echo esc_html($_GET['q']);
$s = wp_unslash($_POST['s']);
?>
<input value="<?php echo esc_attr($s); ?>">
<a href="<?= esc_url($_GET['u']) ?>">x</a>
<?php
echo (int) $_GET['n'];
echo intval($_GET['p']);
echo wp_kses_post($_POST['c']);
echo wp_json_encode($_GET);
echo sanitize_text_field($_GET['t']);
