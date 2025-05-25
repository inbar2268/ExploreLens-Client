#version 300 es
/*
 * Copyright 2020 Google LLC
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

in vec2 v_TexCoord;
out vec4 o_FragColor;

uniform sampler2D uTexture;  // Text texture
uniform vec4 fragColor;      // Color to apply to the texture

void main() {
    // Sample the texture at the given texture coordinates
    vec4 texColor = texture(uTexture, v_TexCoord);

    // Apply the fragment color to the texture
    // This allows tinting the texture while preserving its alpha channel
    o_FragColor = texColor * fragColor;
}