<?php
// PHAR vector: a request-derived path reaches a file op (phar:// deserialization) + a file-delete gadget.
add_action( 'wp_ajax_nopriv_kassi_tpl', 'kassi_tpl' );
function kassi_tpl() {
    $tpl = $_GET['tpl'];
    if ( <error descr="PHP object injection → POP gadget chain: phar:// deserialization via a file operation on a request-derived path (unauthenticated, source $_GET) can instantiate Kassi_Temp::__destruct() in the same plugin, which performs code execution / file write or delete (unlink). Pass ['allowed_classes' => false] to unserialize(), or validate the path, to break the chain.">getimagesize( $tpl )</error> ) {
        echo 'ok';
    }
    wp_die();
}

class Kassi_Temp {
    public $path;
    public function __destruct() {
        unlink( $this->path );
    }
}
