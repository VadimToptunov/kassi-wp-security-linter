<?php
// FP controls: a sink with no gadget, and a sink next to a HARMLESS magic method (not a gadget).
function n_lone_sink() {
    return unserialize( $_POST['a'] );         // WP-009's concern; no gadget here, so no chain
}
function n_sink_with_harmless() {
    return unserialize( $_POST['b'] );
}

class Harmless {
    public $v;
    public function __destruct() {
        $this->v = 1;                          // no dangerous operation → not a gadget
    }
    public function __toString() {
        return (string) $this->v;
    }
}
