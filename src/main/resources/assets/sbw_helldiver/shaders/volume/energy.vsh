#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 ViewProjection;
out vec4 vertexColor;
out vec2 effectUV;
out float viewDistance;
void main(){gl_Position=ViewProjection*vec4(Position,1.0);vertexColor=Color;effectUV=UV0;viewDistance=length(Position);}
