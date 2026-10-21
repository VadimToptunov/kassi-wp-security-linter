<?php
add_shortcode('p_icon', 'p_icon_shortcode');
function p_icon_shortcode($atts) {
    $att = shortcode_atts(array('style' => '', 'name' => ''), $atts, 'p_icon');
    $output = '<i class="' . $att['style'] . ' fa-' . $att['name'] . '"></i>';
    return <warning descr="Shortcode attribute ($atts) is output without escaping (esc_attr, esc_html, esc_url, wp_kses): stored XSS for anyone who can write posts">$output</warning>;
}

class P_Map {
    public function __construct() {
        add_shortcode('p_map', array($this, 'render'));
    }
    public function render($atts) {
        $atts = shortcode_atts(array('center' => '', 'zoom' => '15'), $atts, 'p_map');
        return <warning descr="Shortcode attribute ($atts) is output without escaping (esc_attr, esc_html, esc_url, wp_kses): stored XSS for anyone who can write posts">'<div center="' . $atts['center'] . '" zoom="' . $atts['zoom'] . '"></div>'</warning>;
    }
}

add_shortcode('p_box', function ($a) {
    $color = sanitize_text_field($a['color']);
    echo <warning descr="Shortcode attribute ($a) is output without escaping (esc_attr, esc_html, esc_url, wp_kses): stored XSS for anyone who can write posts">'<div style="background: ' . $color . '">'</warning>;
});

// Cleaning one element does not clean the others.
add_shortcode('p_card', 'p_card');
function p_card($atts) {
    $atts = shortcode_atts(array('user' => '', 'title' => ''), $atts);
    $atts['user'] = preg_replace('/[^a-z]/', '', $atts['user']);
    return <warning descr="Shortcode attribute ($atts) is output without escaping (esc_attr, esc_html, esc_url, wp_kses): stored XSS for anyone who can write posts">'<h3 title="' . $atts['title'] . '">' . $atts['user'] . '</h3>'</warning>;
}
