#version 300 es

layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexPos;

out vec2 v_TexCoord; // <--- Changed from vTexPos to v_TexCoord

uniform mat4 u_ViewProjection;
uniform vec3 u_CameraPos;
uniform vec3 u_LabelOrigin;

void main() {
    // Pass the texture coordinates to the fragment shader
    v_TexCoord = aTexPos; // <--- Changed from vTexPos to v_TexCoord

    // Calculate the camera-facing direction
    vec3 cameraDirection = normalize(u_CameraPos - u_LabelOrigin);

    // Calculate the up vector (world space up)
    vec3 up = vec3(0.0, 1.0, 0.0);

    // Calculate the right vector using cross product
    vec3 right = normalize(cross(up, cameraDirection));

    // Recalculate the up vector to ensure orthogonality
    up = normalize(cross(cameraDirection, right));

    // Calculate the position of the vertex in world space
    // Note the scaling factor is larger (0.15) for these labels to make them more visible
    vec3 vertexPosition = u_LabelOrigin
    + aPosition.x * right * 0.15
    + aPosition.y * up * 0.15;

    // Apply a slight offset toward the camera to avoid z-fighting
    vertexPosition += cameraDirection * 0.01;

    // Transform to clip space
    gl_Position = u_ViewProjection * vec4(vertexPosition, 1.0);
}