<?php
// Both halves of a chain in one file: an unauthenticated unserialize sink + a SQL gadget.
add_action( 'wp_ajax_nopriv_kassi_imp', 'kassi_imp' );
function kassi_imp() {
    $data = <error descr="PHP object injection → POP gadget chain: unserialize() of request data (unauthenticated, source $_POST) can instantiate Kassi_Gadget::__wakeup() in the same plugin, which performs SQL execution ($wpdb->query). Pass ['allowed_classes' => false] to unserialize(), or validate the path, to break the chain.">unserialize( base64_decode( $_POST['blob'] ) )</error>;
    return $data;
}

class Kassi_Gadget {
    public $query;
    public function __wakeup() {
        global $wpdb;
        $wpdb->query( $this->query );
    }
}
