<?php
add_shortcode('s_icon', 's_icon_shortcode');
function s_icon_shortcode($atts) {
    $att = shortcode_atts(array('style' => '', 'cols' => 3), $atts, 's_icon');
    $width = intval($att['cols']) * 10;
    return '<i class="' . esc_attr($att['style']) . '" style="width:' . $width . '%"></i>';
}
add_shortcode('s_list', function ($atts) {
    $a = shortcode_atts(array('type' => 'ul'), $atts);
    if (!in_array($a['type'], array('ul', 'ol'), true)) {
        return '';
    }
    return '<' . $a['type'] . '></' . $a['type'] . '>';
});
// One element cleaned by a strict character filter; the others escaped.
add_shortcode('s_timeline', 's_timeline');
function s_timeline($atts) {
    $atts = shortcode_atts(array('username' => '', 'width' => ''), $atts);
    $atts['username'] = preg_replace('/[^A-Za-z0-9_]+/', '', $atts['username']);
    $out = '<a data-width="' . esc_attr($atts['width']) . '">';
    $out .= sprintf('Tweets by @%s', $atts['username']);
    return $out;
}
