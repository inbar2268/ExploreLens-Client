#version 300 es

#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;

uniform samplerExternalOES u_CameraColorTexture;

in vec2 v_CameraTexCoord;

layout(location = 0) out vec4 o_FragColor;

void main() { o_FragColor = texture(u_CameraColorTexture, v_CameraTexCoord); }
