#version 300 es
/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
