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

// Billboard vertex shader that maintains consistent screen size

layout(location = 0) in vec2 a_Position;  // NDC quad coordinates
layout(location = 1) in vec2 a_TexCoord;

uniform mat4 u_ViewProjection;      // Combined view and projection matrix
uniform vec3 u_LabelOrigin;         // World position where the label should be anchored
uniform vec3 u_CameraPos;           // Camera position in world space
uniform float u_ScreenSize;         // Desired size in screen space (proportion of screen height)

out vec2 v_TexCoord;                // Output texture coordinates

void main() {
    // Calculate view direction from camera to label
    vec3 cameraToLabel = u_LabelOrigin - u_CameraPos;
    float distanceToCamera = length(cameraToLabel);

    // Create a billboard orientation that always faces the camera
    vec3 forward = normalize(cameraToLabel);

    // Calculate up vector (world up)
    vec3 worldUp = vec3(0.0, 1.0, 0.0);

    // Ensure up is orthogonal to forward
    if (abs(dot(forward, worldUp)) > 0.99) {
        // If forward is nearly parallel to world up, use a different up vector
        worldUp = vec3(0.0, 0.0, 1.0);
    }

    // Calculate right vector using cross product
    vec3 right = normalize(cross(forward, worldUp));

    // Recalculate up to ensure orthogonality
    vec3 up = -normalize(cross(right, forward));

    // Scale factor that makes the label appear the same size regardless of distance
    // Multiply by distance to cancel out the perspective division
    float scaleFactor = u_ScreenSize * distanceToCamera;

    // Calculate the scaled offset from the center
    vec3 offset = right * a_Position.x * scaleFactor + up * a_Position.y * scaleFactor;

    // Calculate final position in world space
    vec3 worldPos = u_LabelOrigin + offset;

    // Transform to clip space
    gl_Position = u_ViewProjection * vec4(worldPos, 1.0);

    // Pass texture coordinates to fragment shader
    v_TexCoord = a_TexCoord;
}