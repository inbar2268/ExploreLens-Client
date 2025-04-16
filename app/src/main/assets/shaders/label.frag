#version 300 es

precision mediump float;

in vec2 vTexPos;  // Texture coordinates passed from the vertex shader
uniform sampler2D uTexture;  // Texture sampler
uniform vec4 fragColor;  // Color for the fragment (for background or effects)

layout(location = 0) out vec4 o_FragColor;  // Output color of the fragment

void main(void) {
    // Sample the texture using the texture coordinates (flipping Y-axis)
    vec4 texColor = texture(uTexture, vec2(vTexPos.x, 1.0 - vTexPos.y));

    // Only output pixels where there's something in the texture (alpha > 0)
    // This preserves transparency from the texture
    if (texColor.a < 0.01) {
        discard;  // Discard fully transparent pixels
    }

    // Multiply the texture color with the uniform color
    // This allows controlling the overall opacity via fragColor
    o_FragColor = texColor * fragColor;
}