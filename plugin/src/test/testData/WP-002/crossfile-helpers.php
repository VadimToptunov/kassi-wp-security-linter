<?php
// Gate helper defined in a DIFFERENT file than the handlers.
class Helpers {
    public static function verify_ajax_call() {
        check_ajax_referer('cf-nonce', 'security');
        if (!current_user_can('manage_options')) {
            wp_die('nope');
        }
    }
    public static function just_log() {
        error_log('noop'); // does NOT verify a nonce
    }
}
