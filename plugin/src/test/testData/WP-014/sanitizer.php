<?php
// FP controls: the gadget is present, but each sink is neutralized, so there is no chain.
add_action( 'wp_ajax_nopriv_kassi_imp', 'kassi_imp' );
function kassi_imp() {
    // allowed_classes => false builds no object.
    $data = unserialize( base64_decode( $_POST['blob'] ), array( 'allowed_classes' => false ) );
    return $data;
}
function kassi_tpl() {
    // basename() strips the directory and any stream wrapper: the path no longer derives from input.
    $tpl = basename( $_GET['tpl'] );
    if ( getimagesize( KASSI_DIR . '/' . $tpl ) ) {
        echo 'ok';
    }
}

class Kassi_Gadget {
    public $query;
    public function __wakeup() {
        global $wpdb;
        $wpdb->query( $this->query );
    }
}
