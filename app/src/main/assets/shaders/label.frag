#version 300 es

precision mediump float;

uniform sampler2D uTexture;
in vec2 vTexPos;

layout(location = 0) out vec4 o_FragColor;

void main(void) {
  o_FragColor = texture(uTexture, vec2(vTexPos.x, 1.0 - vTexPos.y));
}