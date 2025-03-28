#version 300 es

layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexPos;

out vec2 vTexPos;

uniform mat4 u_ViewProjection;
uniform vec3 u_CameraPos;
uniform vec3 u_LabelOrigin;

void main() {
  vTexPos = aTexPos;

  // Debug: Make the label always face the camera
  vec3 labelNormal = normalize(u_CameraPos - u_LabelOrigin);
  vec3 labelSide = -cross(labelNormal, vec3(0.0, 1.0, 0.0));

  // Increase the scale factor for visibility
  float scaleFactor = 0.5; // Increased from 0.1

  vec3 modelPosition = u_LabelOrigin +
                       aPosition.x * scaleFactor * labelSide +
                       aPosition.y * scaleFactor * vec3(0.0, 1.0, 0.0);

  gl_Position = u_ViewProjection * vec4(modelPosition, 1.0);
}