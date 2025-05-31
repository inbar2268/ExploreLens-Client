#version 300 es

precision mediump float;

in vec2 v_TexCoord;  // Changed from vTexPos to v_TexCoord
uniform sampler2D uTexture;  // Texture sampler
uniform vec4 fragColor;  // Color for the fragment

layout(location = 0) out vec4 o_FragColor;  // Output color

void main(void) {
    // Sample the texture using the texture coordinates (removed Y-axis flipping)
    vec4 texColor = texture(uTexture, v_TexCoord);

    // Apply the fragment color to the texture, preserving its alpha channel
    // Removed discard condition and vignette effect
    o_FragColor = texColor * fragColor;
}