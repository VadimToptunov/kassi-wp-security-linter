<?php
function n_param($title) { echo $title; }
echo get_option('blogname');
echo $post->post_title;
echo isset($_GET['a']) ? 'yes' : 'no';
echo strlen($_GET['a']);
echo $_SERVER['REMOTE_ADDR'];
