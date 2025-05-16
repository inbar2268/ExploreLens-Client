#version 300 es

layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexPos;

out vec2 vTexPos;

uniform mat4 u_ViewProjection;
uniform vec3 u_CameraPos;
uniform vec3 u_LabelOrigin;
uniform float u_Scale;

void main() {
    vTexPos = aTexPos;

    vec3 labelNormal = normalize(u_CameraPos - u_LabelOrigin);
    vec3 labelSide = normalize(cross(labelNormal, vec3(0.0, 1.0, 0.0)));
    vec3 labelUp = normalize(cross(labelSide, labelNormal));

    vec3 offset = -aPosition.x * u_Scale * labelSide + aPosition.y * u_Scale * labelUp;
    vec3 modelPosition = u_LabelOrigin + offset;

    gl_Position = u_ViewProjection * vec4(modelPosition, 1.0);
}


