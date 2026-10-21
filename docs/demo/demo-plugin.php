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
