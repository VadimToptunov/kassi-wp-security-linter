<?php
/**
 * Plugin Name: Kassi demo (intentionally vulnerable — for screenshots only, never install)
 * One example per inspection, used for Marketplace screenshots via `./gradlew runIde`.
 */

// WP-001 + WP-002: state change without capability check or nonce.
add_action( 'wp_ajax_demo_save', 'demo_save' );
function demo_save() {
	update_option( 'demo_settings', wp_unslash( $_POST['settings'] ) );
	wp_send_json_success();
}

// WP-003: privileged action reachable by visitors.
add_action( 'wp_ajax_nopriv_demo_reset', 'demo_reset' );
function demo_reset() {
	delete_option( 'demo_settings' );
}

// WP-004: public REST route that writes options.
add_action( 'rest_api_init', function () {
	register_rest_route( 'demo/v1', '/settings', array(
		'methods'             => 'POST',
		'callback'            => function ( WP_REST_Request $request ) {
			update_option( 'demo_enabled', (bool) $request->get_param( 'enabled' ) );
		},
		'permission_callback' => '__return_true',
	) );
} );

// WP-005: SQL injection.
function demo_find() {
	global $wpdb;
	$id = sanitize_text_field( $_GET['id'] );
	return $wpdb->get_row( "SELECT * FROM {$wpdb->prefix}demo WHERE id = '$id'" );
}

// WP-006: reflected XSS.
function demo_search_box() {
	echo '<input name="s" value="' . $_GET['s'] . '">';
}

// WP-007: path traversal.
function demo_download() {
	readfile( WP_CONTENT_DIR . '/exports/' . $_GET['file'] );
}

// WP-008: open redirect.
function demo_back() {
	wp_redirect( $_GET['redirect_to'] );
	exit;
}

// WP-009: PHP object injection.
function demo_import_settings() {
	$settings = maybe_unserialize( base64_decode( $_POST['settings'] ) );
	update_option( 'demo_settings', $settings );
}

// WP-010: server-side request forgery.
function demo_fetch_preview() {
	$response = wp_remote_get( esc_url_raw( $_POST['preview_url'] ) );
	return wp_remote_retrieve_body( $response );
}

// WP-011: upload stored without a type check.
function demo_upload_logo() {
	$target = wp_upload_dir()['basedir'] . '/demo/' . $_FILES['logo']['name'];
	move_uploaded_file( $_FILES['logo']['tmp_name'], $target );
}

// WP-012: role taken from the request.
function demo_register_member( $user_id ) {
	$user = new WP_User( $user_id );
	$user->set_role( sanitize_text_field( $_POST['role'] ) );
}

// WP-013: shortcode attribute output without escaping.
add_shortcode( 'demo_badge', 'demo_badge_shortcode' );
function demo_badge_shortcode( $atts ) {
	$a = shortcode_atts( array( 'color' => 'blue', 'label' => '' ), $atts );
	return '<span class="badge badge-' . $a['color'] . '">' . $a['label'] . '</span>';
}

// WP-014: object injection reaching a POP gadget chain (unauthenticated PHAR vector + file-delete gadget).
add_action( 'wp_ajax_nopriv_demo_render', 'demo_render' );
function demo_render() {
	$template = $_GET['template'];        // may be phar://evil.phar/x
	if ( file_exists( $template ) ) {     // phar:// deserialization on a request-derived path
		include $template;
	}
}
class Demo_Cache_Entry {
	public $file;
	public function __destruct() {        // POP gadget: deletes an attacker-chosen file
		unlink( $this->file );
	}
}

// WP-015: request data used as an OS command (exec/system/shell_exec/...) without escapeshellarg().
function demo_ping_tool() {
	$host = $_POST['host'];
	echo shell_exec( 'ping -c 1 ' . $host );
}

// WP-016: request data evaluated as PHP code by eval()/assert()/create_function() (code injection).
function demo_calc_eval() {
	$expr = $_POST['expr'];
	eval( '$result = ' . $expr . ';' );
	return $result;
}

// JL-005 (Joomla): request input reaches $db->setQuery() without $db->quote() or an (int) cast.
function demo_joomla_find() {
	$db    = Factory::getDbo();
	$input = Factory::getApplication()->input;
	$alias = $input->get( 'alias' );      // bare JInput ->get on the input object
	$db->setQuery( "SELECT * FROM #__content WHERE alias = '" . $alias . "'" );
	return $db->loadObject();
}

// JL-006 (Joomla): request input echoed without htmlspecialchars()/$this->escape() (reflected XSS).
function demo_joomla_search_box() {
	$q = Factory::getApplication()->input->getRaw( 'q' );   // getRaw = no filtering
	echo '<p class="search-for">You searched for: ' . $q . '</p>';
}

// JL-007 (Joomla): request input reaches require without basename()/JPath::clean() (LFI).
function demo_joomla_render_view() {
	$view = Factory::getApplication()->input->getString( 'view' );   // getString keeps '/'
	require_once JPATH_COMPONENT . '/views/' . $view . '/view.html.php';
}
