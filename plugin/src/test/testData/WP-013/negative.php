<?php
// Callback in another file.
add_shortcode('n_other', 'n_defined_elsewhere');
// The content argument is not a source (post content is filtered by kses for contributors).
add_shortcode('n_wrap', 'n_wrap_shortcode');
function n_wrap_shortcode($atts, $content = '') {
    return '<div class="n-wrap">' . do_shortcode($content) . '</div>';
}
// Not a shortcode callback.
function n_plain($atts) { return '<b>' . $atts['x'] . '</b>'; }
// Registered and removed again in one function: evaluates an internal string, never post content.
class N_Shipping {
    public function evaluate_cost($sum) {
        add_shortcode('fee', array($this, 'fee'));
        $sum = do_shortcode($sum);
        remove_shortcode('fee', array($this, 'fee'));
        return $sum;
    }
    public function fee($atts) {
        $atts = shortcode_atts(array('min_fee' => ''), $atts, 'fee');
        return (string) $atts['min_fee'];
    }
}
