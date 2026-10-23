#version 150
in vec3 Position;
in vec3 Color;
uniform mat4 Projection;
out vec3 color;
void main(){gl_Position=Projection*vec4(Position,1.);color=Color;}
