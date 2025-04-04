#version 300 es

precision mediump float;

in vec2 vTexPos;  // Texture coordinates passed from the vertex shader
uniform sampler2D uTexture;  // Texture sampler
uniform vec4 fragColor;  // Color for the fragment (for background or effects)

layout(location = 0) out vec4 o_FragColor;  // Output color of the fragment

void main(void) {
    // Sample the texture using the texture coordinates
    vec4 texColor = texture(uTexture, vec2(vTexPos.x, 1.0 - vTexPos.y));  // Flip the Y-axis to match OpenGL conventions

    // Set the final output color, combining the texture color with the fragColor
    o_FragColor = texColor * fragColor;  // Apply color to texture (blend or effect)
}
