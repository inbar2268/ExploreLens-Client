#version 300 es

precision mediump float;

in vec2 vTexPos;  // Texture coordinates from vertex shader
uniform sampler2D uTexture;  // Texture sampler
uniform vec4 fragColor;  // Color for the fragment

layout(location = 0) out vec4 o_FragColor;  // Output color

void main(void) {
    // Sample the texture using the texture coordinates (flipping Y-axis)
    vec4 texColor = texture(uTexture, vec2(vTexPos.x, 1.0 - vTexPos.y));

    // Only output pixels where there's something in the texture (alpha > 0)
    // Higher threshold for cleaner edges
    if (texColor.a < 0.05) {
        discard;  // Discard transparent pixels
    }

    // Add a subtle vignette effect at the edges
    vec2 uv = vTexPos * 2.0 - 1.0;  // Center coordinates at (0,0)
    float radius = length(uv);
    float vignette = smoothstep(1.2, 0.7, radius);

    // Apply color with vignette
    vec4 finalColor = texColor * fragColor;
    finalColor.rgb *= mix(0.9, 1.0, vignette); // Subtle darkening at edges

    o_FragColor = finalColor;
}